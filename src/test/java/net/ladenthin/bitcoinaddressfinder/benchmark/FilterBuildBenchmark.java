// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.PrngAddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BlockedBloomAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.HashSetAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.TruncatedLong64SortedArrayPresence;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Construction cost per backend, measured without storage.
 *
 * <h2>Why this exists</h2>
 * Build time had only ever been measured by populating from a real LMDB store, which makes it an
 * <b>I/O measurement, not a construction measurement</b>: the same Full DB build varied between
 * 1&nbsp;043&nbsp;s and 2&nbsp;976&nbsp;s across runs purely with page-cache state and free RAM, and
 * that spread dwarfs the differences between backends. Any statement of the form "backend A builds
 * 1.8× faster than B" taken from those numbers is comparing cache luck.
 *
 * <p>Feeding from {@link PrngAddressIterable} instead removes storage entirely, so what remains is
 * the algorithm: blocked Bloom streams the bit array in one pass, while the Binary Fuse filters run
 * a multi-pass peeling construction over auxiliary arrays. That difference is real and worth
 * quantifying — it just cannot be quantified through a cold 61&nbsp;GB mmap.
 *
 * <h2>Single-shot, but with one warmup shot discarded</h2>
 * A build is a one-time cost, so {@link Mode#SingleShotTime} measures exactly one construction per
 * iteration rather than a steady-state rate.
 *
 * <p>The warmup iteration is <b>not</b> optional, despite the temptation to argue that a cold first
 * build is what the operator actually sees. Running with {@code -wi 0} produced error bars
 * <em>larger than the means</em> — 4282&nbsp;&plusmn;&nbsp;8010&nbsp;ms at 10&nbsp;M — because the
 * first shot carries the entire JIT compilation of the construction path while the rest do not, so
 * the "measurement" was really a bimodal mixture of two different programs. Discarding that one shot
 * cut the spread by a factor of 6 to 74 (to 3793&nbsp;&plusmn;&nbsp;762&nbsp;ms). A dispersion that
 * exceeds the mean is not an honest wide error bar, it is an unusable one.
 *
 * <h2>Memory</h2>
 * The Binary Fuse peeling construction peaks at ~29&nbsp;B/entry, so 100&nbsp;M entries needs
 * ~3&nbsp;GB and 1&nbsp;B entries ~29&nbsp;GB. Size {@code -Xmx} accordingly; blocked Bloom needs
 * only the finished filter.
 *
 * <h2>Run</h2>
 * <pre>
 * java ... org.openjdk.jmh.Main FilterBuildBenchmark -p entries=10000000,100000000 -f 1 -wi 1 -i 5
 * </pre>
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
@Fork(1)
public class FilterBuildBenchmark {

    /** Seed for the generated address set. */
    private static final long MEMBER_SEED = 0xC0FFEEL;

    /**
     * Which backend to construct, optionally with a density appended as {@code NAME:bitsPerEntry:k}
     * (for example {@code BLOCKED_BLOOM:17:7}), so that every configuration fits into a single JMH
     * session. See {@link FilterLookupBenchmark#backend} for why that matters.
     */
    @Param({"BINARY_FUSE_8", "BINARY_FUSE_16", "BLOCKED_BLOOM"})
    public String backend;

    /** Number of addresses to build over. */
    @Param({"10000000"})
    public long entries;

    /** Blocked Bloom bits per entry; {@code -1} uses the default. Ignored by the fuse backends. */
    @Param({"-1"})
    public int bitsPerEntry;

    /** Blocked Bloom bits probed per key; {@code -1} uses the default. Ignored by the fuse backends. */
    @Param({"-1"})
    public int k;

    /** Creates a new benchmark instance (no-arg constructor for JMH). */
    public FilterBuildBenchmark() {
        // no-op
    }

    /**
     * Constructs the filter once.
     *
     * @return the constructed filter, returned so JMH's sink prevents the build being optimised away
     */
    @Benchmark
    public AddressPresence build() {
        PrngAddressIterable source = new PrngAddressIterable(MEMBER_SEED, entries);

        String name = backend;
        int bpe = bitsPerEntry;
        int probes = k;
        if (backend.indexOf(':') >= 0) {
            String[] parts = backend.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("backend must be NAME or NAME:bitsPerEntry:k, was: " + backend);
            }
            name = parts[0];
            bpe = Integer.parseInt(parts[1]);
            probes = Integer.parseInt(parts[2]);
        }

        return switch (name) {
            case "BINARY_FUSE_8" -> BinaryFuse8AddressPresence.populateFrom(source);
            case "BINARY_FUSE_16" -> BinaryFuse16AddressPresence.populateFrom(source);
            case "BLOCKED_BLOOM" ->
                bpe > 0 && probes > 0
                        ? BlockedBloomAddressPresence.populateFrom(source, probes, bpe)
                        : BlockedBloomAddressPresence.populateFrom(source);
            case "TRUNCATED_LONG_64" -> TruncatedLong64SortedArrayPresence.populateFrom(source);
            case "HASHSET" -> HashSetAddressPresence.populateFrom(source);
            default -> throw new IllegalArgumentException("unknown backend: " + backend);
        };
    }
}
