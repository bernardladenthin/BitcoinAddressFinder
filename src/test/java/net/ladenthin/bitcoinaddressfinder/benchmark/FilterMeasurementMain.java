// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BlockedBloomAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.HashSetAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.TruncatedLong64SortedArrayPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;

/**
 * Standalone measurement harness (NOT a JMH benchmark, NOT a {@code @Test}) that builds one address
 * lookup backend from a real LMDB database and reports the four numbers the filter design turns on:
 *
 * <ol>
 *   <li><b>build time</b> — wall-clock to construct the in-memory snapshot from the LMDB stream;</li>
 *   <li><b>retained heap</b> — used heap after a full GC with the filter reachable, minus the same
 *       measured before the build (approximate but dominated by the multi-GB filter);</li>
 *   <li><b>lookup throughput</b> — probes/second for {@code containsAddress} over random non-members
 *       (the overwhelmingly common miss path during a key scan);</li>
 *   <li><b>false-positive rate</b> — fraction of those random non-members the filter lets through
 *       (each such hit would cost one LMDB verification at run time).</li>
 * </ol>
 *
 * <p>The lookup is measured against the <em>raw filter</em> (not the accelerator) so the number is
 * the filter's own cost, isolated from LMDB verification. Members are sampled from the database and
 * checked to confirm the no-false-negative contract holds on real data.
 *
 * <p>Usage (classpath must include test-classes, classes and the test-scope deps — see CLAUDE.md
 * "Running JMH benchmarks locally" for the {@code --add-opens} set lmdbjava needs):
 * <pre>
 *   java --add-opens=java.base/java.nio=ALL-UNNAMED \
 *        --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
 *        --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
 *        -Xmx8g -cp "target/test-classes;target/classes;$(cat target/cp-test.txt)" \
 *        net.ladenthin.bitcoinaddressfinder.benchmark.FilterMeasurementMain \
 *        &lt;lmdbDir&gt; &lt;BACKEND&gt; [probeCount]
 * </pre>
 * where {@code BACKEND} is one of {@code BINARY_FUSE_8}, {@code BINARY_FUSE_16},
 * {@code BLOCKED_BLOOM}, {@code TRUNCATED_LONG_64}, {@code HASHSET}. Prints one CSV line to stdout.
 */
public final class FilterMeasurementMain {

    private static final int HASH160_LENGTH = 20;

    private FilterMeasurementMain() {
        // no instances
    }

    /**
     * Runs one measurement.
     *
     * @param args {@code <lmdbDir> <BACKEND> [probeCount]}
     * @throws Exception if LMDB cannot be opened
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: FilterMeasurementMain <lmdbDir> <BACKEND> [probeCount]");
            System.exit(2);
            return;
        }
        String lmdbDir = args[0];
        String backend = args[1];
        int probeCount = args.length >= 3 ? Integer.parseInt(args[2]) : 5_000_000;
        // Optional BLOCKED_BLOOM tuning knobs; -1 means "use the class defaults".
        int k = args.length >= 4 ? Integer.parseInt(args[3]) : -1;
        int bitsPerEntry = args.length >= 5 ? Integer.parseInt(args[4]) : -1;

        Network network = new NetworkParameterFactory().getNetwork();
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);

        CLMDBConfigurationReadOnly cfg = new CLMDBConfigurationReadOnly();
        cfg.lmdbDirectory = lmdbDir;
        cfg.useProxyOptimal = true;
        cfg.logStatsOnInit = false;
        cfg.logStatsOnClose = false;

        LMDBPersistence lmdb = new LMDBPersistence(cfg, persistenceUtils);
        lmdb.init();
        try {
            long count = lmdb.count();
            log("opened " + lmdbDir + " with " + count + " entries; backend=" + backend);

            // Sample real members (first N addresses) to check the no-false-negative contract later.
            List<byte[]> members = sampleMembers(lmdb, 20_000);
            log("sampled " + members.size() + " real members for no-false-negative check");

            long usedBefore = usedHeapAfterGc();
            long t0 = System.nanoTime();
            AddressPresence filter = build(backend, lmdb, k, bitsPerEntry);
            long buildNanos = System.nanoTime() - t0;
            long usedAfter = usedHeapAfterGc();
            long retainedBytes = Math.max(0, usedAfter - usedBefore);

            // No false negatives: every sampled real member must be reported present by the filter.
            int falseNegatives = 0;
            ByteBuffer probe = ByteBuffer.allocate(HASH160_LENGTH);
            for (byte[] m : members) {
                probe.clear();
                probe.put(m);
                probe.flip();
                if (!filter.containsAddress(probe)) {
                    falseNegatives++;
                }
            }

            // Lookup throughput + FPR over deterministic random non-members.
            byte[][] probes = randomNonMembers(probeCount);
            long positives = 0;
            long tl0 = System.nanoTime();
            for (int i = 0; i < probes.length; i++) {
                probe.clear();
                probe.put(probes[i]);
                probe.flip();
                if (filter.containsAddress(probe)) {
                    positives++;
                }
            }
            long lookupNanos = System.nanoTime() - tl0;

            double buildSec = buildNanos / 1e9;
            double retainedMiB = retainedBytes / (1024.0 * 1024.0);
            double retainedBytesPerEntry = count == 0 ? 0 : (double) retainedBytes / count;
            double lookupsPerSec = probes.length / (lookupNanos / 1e9);
            double fpr = (double) positives / probes.length;

            // Machine-readable CSV line, prefixed so it is greppable in a noisy log.
            System.out.printf(
                    "RESULT,backend=%s,k=%d,bpe=%d,entries=%d,buildSec=%.2f,retainedMiB=%.1f,bytesPerEntry=%.3f,"
                            + "lookupsPerSec=%.0f,fpr=%.6f,falseNegatives=%d,probes=%d%n",
                    backend,
                    k,
                    bitsPerEntry,
                    count,
                    buildSec,
                    retainedMiB,
                    retainedBytesPerEntry,
                    lookupsPerSec,
                    fpr,
                    falseNegatives,
                    probes.length);
        } finally {
            lmdb.close();
        }
    }

    private static AddressPresence build(String backend, LMDBPersistence lmdb, int k, int bitsPerEntry) {
        return switch (backend) {
            case "BINARY_FUSE_8" -> BinaryFuse8AddressPresence.populateFrom(lmdb);
            case "BINARY_FUSE_16" -> BinaryFuse16AddressPresence.populateFrom(lmdb);
            case "BLOCKED_BLOOM" ->
                k > 0 && bitsPerEntry > 0
                        ? BlockedBloomAddressPresence.populateFrom(lmdb, k, bitsPerEntry)
                        : BlockedBloomAddressPresence.populateFrom(lmdb);
            case "TRUNCATED_LONG_64" -> TruncatedLong64SortedArrayPresence.populateFrom(lmdb);
            case "HASHSET" -> HashSetAddressPresence.populateFrom(lmdb);
            default -> throw new IllegalArgumentException("unknown backend: " + backend);
        };
    }

    /** Sentinel to break out of {@link LMDBPersistence#forEachAddress} once enough members sampled. */
    private static final class StopSampling extends RuntimeException {
        StopSampling() {
            super(null, null, false, false);
        }
    }

    private static List<byte[]> sampleMembers(LMDBPersistence lmdb, int max) {
        List<byte[]> out = new ArrayList<>(max);
        try {
            lmdb.forEachAddress(bb -> {
                if (bb.remaining() == HASH160_LENGTH) {
                    byte[] copy = new byte[HASH160_LENGTH];
                    bb.get(bb.position(), copy);
                    out.add(copy);
                    if (out.size() >= max) {
                        // Break early: the try-with-resources in forEachAddress closes the cursor/txn,
                        // so we must not scan the whole (billion-entry) database just for the prefix.
                        throw new StopSampling();
                    }
                }
            });
        } catch (StopSampling done) {
            // reached the sample cap
        }
        return out;
    }

    private static byte[][] randomNonMembers(int n) {
        // Deterministic, and offset into a key space that real hash160s (from real keys) do not
        // occupy in practice; a handful of accidental collisions only inflate the measured FPR.
        Random rng = new Random(0x5EED_1234_ABCDL);
        byte[][] out = new byte[n][];
        for (int i = 0; i < n; i++) {
            byte[] q = new byte[HASH160_LENGTH];
            rng.nextBytes(q);
            out[i] = q;
        }
        return out;
    }

    private static long usedHeapAfterGc() {
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static void log(String msg) {
        System.err.println("[measure] " + msg);
    }
}
