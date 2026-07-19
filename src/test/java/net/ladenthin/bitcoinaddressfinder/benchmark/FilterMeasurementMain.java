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
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
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
 *       measured before the build. <b>This is an estimate, not the structure's size.</b> It also
 *       captures whatever else the harness happens to be holding, so it is only meaningful once the
 *       filter dominates the heap: at multi-GB scale the error is negligible, but at ~10 M entries
 *       it has been observed to report ~26 MiB where the backing array is exactly 16 MiB. Derive
 *       bits-per-entry analytically (block count × 512 ÷ entries) rather than from this figure;</li>
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
 *        &lt;lmdbDir&gt; &lt;BACKEND&gt; [probeCount] [k] [bitsPerEntry] [noReadAhead]
 * </pre>
 * where {@code BACKEND} is one of {@code BINARY_FUSE_8}, {@code BINARY_FUSE_16},
 * {@code BLOCKED_BLOOM}, {@code TRUNCATED_LONG_64}, {@code HASHSET}, or {@code LMDB_ONLY} to measure
 * the exact-lookup cost itself (no filter is built; the LMDB store answers directly). Prints one CSV
 * line to stdout.
 */
public final class FilterMeasurementMain {

    private static final int HASH160_LENGTH = 20;

    private FilterMeasurementMain() {
        // no instances
    }

    /**
     * Runs one measurement.
     *
     * @param args {@code <lmdbDir> <BACKEND> [probeCount] [k] [bitsPerEntry] [noReadAhead]}
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
        // Opt into MDB_NORDAHEAD to A/B the OS read-ahead behaviour on a larger-than-RAM database.
        boolean noReadAhead = args.length >= 6 && Boolean.parseBoolean(args[5]);

        // A synthetic source removes LMDB (and therefore page-cache behaviour and read
        // amplification) from the measurement. Used for questions that do not depend on real data,
        // e.g. sweeping k at a chosen bits-per-entry: the optimum is a function of bit density and
        // per-block load, both scale-independent, so it can be answered at a small entry count
        // instead of rebuilding over a billion-entry database for tens of minutes per data point.
        if (lmdbDir.startsWith("prng:")) {
            long synthetic = Long.parseLong(lmdbDir.substring("prng:".length()));
            runSynthetic(synthetic, backend, probeCount, k, bitsPerEntry);
            return;
        }

        Network network = new NetworkParameterFactory().getNetwork();
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);

        CLMDBConfigurationReadOnly cfg = new CLMDBConfigurationReadOnly();
        cfg.lmdbDirectory = lmdbDir;
        cfg.useProxyOptimal = true;
        cfg.logStatsOnInit = false;
        cfg.logStatsOnClose = false;
        cfg.useNoReadAhead = noReadAhead;

        LMDBPersistence lmdb = new LMDBPersistence(cfg, persistenceUtils);
        lmdb.init();
        try {
            long count = lmdb.count();
            log("opened " + lmdbDir + " with " + count + " entries; backend=" + backend + "; noReadAhead="
                    + noReadAhead);

            // Sample real members (first N addresses) to check the no-false-negative contract later.
            List<byte[]> members = sampleMembers(lmdb, 20_000);
            log("sampled " + members.size() + " real members for no-false-negative check");

            long usedBefore = usedHeapAfterGc();
            long t0 = System.nanoTime();
            // LMDB_ONLY measures the exact-verification cost every probabilistic backend pays on a
            // filter hit. Both density models in docs/performance.md assume that figure rather than
            // measuring it, and it decides whether 11 or 17 bits/entry is the better default.
            AddressPresence filter = "LMDB_ONLY".equals(backend) ? lmdb : build(backend, lmdb, k, bitsPerEntry);
            long buildNanos = System.nanoTime() - t0;
            long usedAfter = usedHeapAfterGc();
            long retainedBytes = Math.max(0, usedAfter - usedBefore);

            // No false negatives: every sampled real member must be reported present by the filter.
            int falseNegatives = 0;
            // Direct, not heap: LMDB rejects heap buffers as keys (BufferMustBeDirectException), and the
            // LMDB_ONLY backend probes the store itself. Direct is also what production passes, so the
            // in-memory backends are measured on the same buffer type they see in the consumer.
            ByteBuffer probe = ByteBuffer.allocateDirect(HASH160_LENGTH);
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
                    "RESULT,backend=%s,k=%d,bpe=%d,nordahead=%b,entries=%d,buildSec=%.2f,exactBytes=%d,exactBytesPerEntry=%.3f,retainedMiB=%.1f,bytesPerEntry=%.3f,"
                            + "lookupsPerSec=%.0f,fpr=%.6f,falseNegatives=%d,probes=%d%n",
                    backend,
                    k,
                    bitsPerEntry,
                    noReadAhead,
                    count,
                    buildSec,
                    filter.sizeInBytes(),
                    count == 0 || filter.sizeInBytes() < 0 ? 0.0 : (double) filter.sizeInBytes() / count,
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

    /** Measures against generated addresses; mirrors the LMDB path but skips storage entirely. */
    private static void runSynthetic(long count, String backend, int probeCount, int k, int bitsPerEntry) {
        PrngAddressIterable source = new PrngAddressIterable(0xC0FFEEL, count);
        log("synthetic source: " + count + " generated addresses; backend=" + backend);

        long usedBefore = usedHeapAfterGc();
        long t0 = System.nanoTime();
        AddressPresence filter = build(backend, source, k, bitsPerEntry);
        long buildNanos = System.nanoTime() - t0;
        long usedAfter = usedHeapAfterGc();
        long retainedBytes = Math.max(0, usedAfter - usedBefore);

        // No false negatives: a sample of real members must all be reported present.
        int falseNegatives = 0;
        int sample = (int) Math.min(20_000L, count);
        ByteBuffer probe = ByteBuffer.allocateDirect(HASH160_LENGTH);
        for (int i = 0; i < sample; i++) {
            probe.clear();
            probe.put(PrngAddressIterable.addressAt(0xC0FFEEL, i));
            probe.flip();
            if (!filter.containsAddress(probe)) {
                falseNegatives++;
            }
        }

        // Probes come from a different seed, so they are non-members by construction.
        long positives = 0;
        long tl0 = System.nanoTime();
        for (int i = 0; i < probeCount; i++) {
            probe.clear();
            probe.put(PrngAddressIterable.addressAt(0x5EED_1234_ABCDL, i));
            probe.flip();
            if (filter.containsAddress(probe)) {
                positives++;
            }
        }
        long lookupNanos = System.nanoTime() - tl0;

        System.out.printf(
                "RESULT,backend=%s,k=%d,bpe=%d,source=prng,entries=%d,buildSec=%.2f,exactBytes=%d,exactBytesPerEntry=%.3f,retainedMiB=%.1f,"
                        + "bytesPerEntry=%.3f,lookupsPerSec=%.0f,fpr=%.6f,falseNegatives=%d,probes=%d%n",
                backend,
                k,
                bitsPerEntry,
                count,
                buildNanos / 1e9,
                filter.sizeInBytes(),
                count == 0 || filter.sizeInBytes() < 0 ? 0.0 : (double) filter.sizeInBytes() / count,
                retainedBytes / (1024.0 * 1024.0),
                count == 0 ? 0 : (double) retainedBytes / count,
                probeCount / (lookupNanos / 1e9),
                (double) positives / probeCount,
                falseNegatives,
                probeCount);
    }

    private static AddressPresence build(String backend, AddressIterable lmdb, int k, int bitsPerEntry) {
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
