// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.bloom.BloomFilterAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BlockedBloomAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.HashSetAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.TruncatedLong64SortedArrayPresence;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Storage-free lookup-latency comparison of every address-presence backend, across a sweep of
 * database sizes.
 *
 * <h2>Why this exists alongside {@code AddressLookupBenchmark}</h2>
 * {@code AddressLookupBenchmark} builds a 2048-entry LMDB, so every filter fits in L1 and the
 * measurement collapses to instruction count. That regime is actively misleading: it ranks
 * {@code BINARY_FUSE_8} (~19 ns) ahead of {@code BLOCKED_BLOOM} (~30 ns), yet against the real
 * 132&nbsp;M-entry database the two measure 12.93&nbsp;M and 11.54&nbsp;M lookups/s — a gap of 11 %
 * rather than 58 % — because once the filter no longer fits in cache the cost is dominated by memory
 * latency, and blocked Bloom confines all {@code k} probes to a single 64-byte block while a fuse
 * lookup makes three scattered reads.
 *
 * <p>This benchmark therefore <b>sweeps {@code entries}</b>, which is the axis that actually decides
 * the ranking, and drops LMDB entirely: addresses come from {@link PrngAddressIterable}, so there is
 * no page-cache behaviour, no read amplification and no run-to-run variance from what the OS happens
 * to have cached — only the filter is measured.
 *
 * <h2>What is measured</h2>
 * Average time per {@link AddressPresence#containsAddress(ByteBuffer)} against <em>non-member</em>
 * probes (drawn from a different seed than the members). Misses are the overwhelmingly common case
 * during a key scan, and they are also the honest case for a filter: a miss short-circuits, so this
 * measures the filter's own work rather than a delegate's.
 *
 * <p>Backends are measured as bare filters, without an LMDB verifier behind them — {@code BLOOM} is
 * the exception, since Guava's filter is only reachable through {@code BloomFilterAccelerator}; it
 * gets an always-absent delegate, which non-member probes almost never reach.
 *
 * <h2>Memory</h2>
 * {@code HASHSET} costs ~80 B/entry and {@code TRUNCATED_LONG_64} ~8 B/entry, so the upper rungs of
 * the sweep need a large heap (at 100&nbsp;M entries {@code HASHSET} alone is ~8&nbsp;GB). Size
 * {@code -Xmx} accordingly or restrict {@code -p backend=} when sweeping the large sizes.
 *
 * <h2>Run</h2>
 * <pre>
 * # all backends at one size
 * java ... org.openjdk.jmh.Main FilterLookupBenchmark -p entries=1000000 -f 1 -wi 3 -i 5
 *
 * # the scale sweep that matters, filters only (skip the RAM-hungry exact backends)
 * java ... org.openjdk.jmh.Main FilterLookupBenchmark \
 *     -p backend=BLOOM,BINARY_FUSE_8,BINARY_FUSE_16,BLOCKED_BLOOM \
 *     -p entries=1000000,10000000,50000000 -f 1 -wi 3 -i 5
 * </pre>
 * See {@code docs/performance.md} §6 for the classpath recipe and the {@code --add-opens} set.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class FilterLookupBenchmark {

    /** Length of a hash160 entry in bytes. */
    private static final int HASH160_LENGTH = 20;

    /** Seed for the member set (the addresses inserted into the filter). */
    private static final long MEMBER_SEED = 0xC0FFEEL;

    /** Seed for the probe set; different from {@link #MEMBER_SEED}, so probes are non-members. */
    private static final long PROBE_SEED = 0x5EED_1234_ABCDL;

    /** Number of pre-generated probes cycled through; power of two so the cursor can mask. */
    private static final int PROBE_COUNT = 1 << 16;

    /** Bitmask used in place of {@code % PROBE_COUNT}. */
    private static final int PROBE_MASK = PROBE_COUNT - 1;

    /** Guava Bloom false-positive probability, matching the project default. */
    private static final double BLOOM_FPP = 0.01;

    /** Which backend this trial measures. */
    @Param({"BLOOM", "HASHSET", "TRUNCATED_LONG_64", "BINARY_FUSE_8", "BINARY_FUSE_16", "BLOCKED_BLOOM"})
    public String backend;

    /**
     * Number of addresses in the synthetic database. The default sweep spans cache-resident to
     * clearly cache-cold, which is where the backend ranking changes.
     */
    @Param({"100000", "1000000", "10000000"})
    public long entries;

    private AddressPresence lookup;
    private byte[][] probes;
    private ByteBuffer reusedKeyBuffer;
    private int probeCursor;

    /** Creates a new benchmark instance (no-arg constructor for JMH). */
    public FilterLookupBenchmark() {
        // no-op
    }

    /** Always-absent delegate, so a Bloom positive resolves without touching storage. */
    private static final class AbsentDelegate implements net.ladenthin.bitcoinaddressfinder.persistence.AddressLookup {
        @Override
        public boolean containsAddress(ByteBuffer hash160) {
            return false;
        }

        @Override
        public boolean requiresBackend() {
            return false;
        }

        @Override
        public org.bitcoinj.base.Coin getAmount(ByteBuffer hash160) {
            return org.bitcoinj.base.Coin.ZERO;
        }
    }

    /**
     * Builds the backend under test from the synthetic source and pre-generates the probe set.
     *
     * @throws IllegalArgumentException if {@link #backend} is not a known backend name
     */
    @Setup(Level.Trial)
    public void setUp() {
        PrngAddressIterable source = new PrngAddressIterable(MEMBER_SEED, entries);
        lookup = switch (backend) {
            case "BLOOM" -> BloomFilterAccelerator.populateFrom(source, new AbsentDelegate(), BLOOM_FPP);
            case "HASHSET" -> HashSetAddressPresence.populateFrom(source);
            case "TRUNCATED_LONG_64" -> TruncatedLong64SortedArrayPresence.populateFrom(source);
            case "BINARY_FUSE_8" -> BinaryFuse8AddressPresence.populateFrom(source);
            case "BINARY_FUSE_16" -> BinaryFuse16AddressPresence.populateFrom(source);
            case "BLOCKED_BLOOM" -> BlockedBloomAddressPresence.populateFrom(source);
            default -> throw new IllegalArgumentException("unknown backend: " + backend);
        };

        probes = new byte[PROBE_COUNT][];
        for (int i = 0; i < PROBE_COUNT; i++) {
            probes[i] = PrngAddressIterable.addressAt(PROBE_SEED, i);
        }
        reusedKeyBuffer = ByteBuffer.allocate(HASH160_LENGTH);
        probeCursor = 0;
    }

    /**
     * Measures one {@code containsAddress} call against a non-member probe. The result is returned
     * so JMH's automatic sink prevents dead-code elimination.
     *
     * @return the lookup result, consumed by JMH
     */
    @Benchmark
    public boolean containsAddress() {
        byte[] probe = probes[probeCursor++ & PROBE_MASK];
        reusedKeyBuffer.clear();
        reusedKeyBuffer.put(probe);
        reusedKeyBuffer.flip();
        return lookup.containsAddress(reusedKeyBuffer);
    }
}
