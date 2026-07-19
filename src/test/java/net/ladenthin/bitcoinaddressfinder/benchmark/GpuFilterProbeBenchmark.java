// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BlockedBloomAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BlockedBloomGpuFilterData;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * GPU A/B of the two filter probes — Binary Fuse 8 against blocked Bloom — isolated from everything
 * else the production kernel does.
 *
 * <h2>Why a dedicated kernel rather than {@code enableGpuFilter}</h2>
 * In {@code generateKeysKernel_grid} the filter probe is a small tail behind EC point generation and
 * two hash160 chains — roughly 57 % / 43 % of kernel time per {@code docs/performance.md} § 6. An
 * A/B there would be dominated by work both arms perform identically, and could easily show a few
 * percent where the probes themselves differ by a factor. The {@code benchmarkFilterFuse8} and
 * {@code benchmarkFilterBlockedBloom} kernels do nothing but probe a supplied key array, so the
 * measured difference <em>is</em> the filter.
 *
 * <h2>What is being compared</h2>
 * A fuse lookup reads three fingerprints at scattered offsets — three uncoalesced global
 * transactions per candidate. The blocked layout confines all {@code k} probes of a key to one
 * 512-bit block, a single coalesced 64-byte transaction, at the cost of more bits per entry and
 * more ALU work. On the CPU that trade is worth 13-36 % at the billion-entry tier on a 16 MB-L3
 * host and costs ~6 % on a 96 MB-L3 host. A GPU has no comparable cache but far stricter coalescing
 * rules, so which way it falls is an open question — that is why this benchmark exists rather than
 * a prediction in the docs.
 *
 * <h2>Fair comparison</h2>
 * Both arms probe the <em>same</em> keys, drawn from a seed disjoint from the members so that
 * essentially every probe is a miss. Misses are the overwhelmingly common case during a scan and
 * also where the layouts differ most, since a fuse miss still costs three reads. Both kernels write
 * a per-work-item flag instead of incrementing an atomic counter, which would serialise under
 * contention and confound the measurement. Filters are built from the same entry count, so their
 * sizes differ exactly as they would in production (~1.14 vs ~1.6-2 B/entry).
 *
 * <p>Filter and keys are uploaded once in {@code @Setup} — at production sizes the filter reaches
 * gigabytes, and re-uploading per invocation would measure PCIe rather than the probe. The kernel
 * build, the dominant one-time cost (seconds on NVIDIA, historically minutes on AMD, see
 * {@code docs/performance.md} § 9), likewise happens once there.
 *
 * <h2>Availability</h2>
 * Requires an OpenCL device; without one {@link OpenCLPlatformAssume} throws from {@code @Setup} and
 * JMH reports {@code ERROR} for every row, matching {@link GpuFuse8FilterBenchmark}.
 *
 * <h2>Run</h2>
 * <pre>
 * java ... org.openjdk.jmh.Main GpuFilterProbeBenchmark -f 1 -wi 2 -w 5 -i 5 -r 5
 * java ... org.openjdk.jmh.Main GpuFilterProbeBenchmark -p entries=100000000
 * </pre>
 * Divide the reported ms/op by {@code probeCount} for a per-probe figure; the ratio between the two
 * {@code filter} rows is the result of interest.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class GpuFilterProbeBenchmark {

    /** Seed for the member set inserted into the filter. */
    private static final long MEMBER_SEED = 0xC0FFEEL;

    /** Seed for the probe keys; disjoint from the members, so probes are misses. */
    private static final long PROBE_SEED = 0x5EED_1234_ABCDL;

    /** Which filter this trial probes. */
    @Param({"FUSE8", "BLOCKED_BLOOM"})
    public String filter;

    /** Addresses in the filter — drives its size, hence its coalescing and memory behaviour. */
    @Param({"10000000"})
    public long entries;

    /** Keys probed per kernel launch, i.e. the global work size. */
    @Param({"4194304"})
    public int probeCount;

    /** Blocked Bloom bits per entry; {@code -1} uses the class default. Ignored by {@code FUSE8}. */
    @Param({"-1"})
    public int bitsPerEntry;

    /** Blocked Bloom bits probed per key; {@code -1} uses the class default. Ignored by {@code FUSE8}. */
    @Param({"-1"})
    public int k;

    private OpenCLContext context;

    /** Creates a new benchmark instance (no-arg constructor for JMH). */
    public GpuFilterProbeBenchmark() {
        // no-op
    }

    /**
     * Compiles the kernel, builds the filter and uploads filter plus probe keys — all once.
     *
     * @throws Exception if OpenCL initialisation fails
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        CProducerOpenCL config = new CProducerOpenCL();
        // Minimal grid: this benchmark never calls createKeys, it only needs a compiled program and
        // a live context/queue, so the key-generation parameters are irrelevant.
        config.batchSizeInBits = 8;
        context = new OpenCLContext(config, new BitHelper());
        context.init();

        PrngAddressIterable source = new PrngAddressIterable(MEMBER_SEED, entries);
        long[] probeKeys = new long[probeCount];
        for (int i = 0; i < probeCount; i++) {
            probeKeys[i] = ByteBuffer.wrap(PrngAddressIterable.addressAt(PROBE_SEED, i))
                    .getLong(0);
        }

        // Optional "NAME:bitsPerEntry:k" suffix keeps every configuration in one JMH session; see
        // FilterLookupBenchmark#backend for why comparing across sessions is not admissible.
        String name = filter;
        int bpe = bitsPerEntry;
        int probesPerKey = k;
        if (filter.indexOf(':') >= 0) {
            String[] parts = filter.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "filter must be NAME or NAME:bitsPerEntry:k, was: " + filter);
            }
            name = parts[0];
            bpe = Integer.parseInt(parts[1]);
            probesPerKey = Integer.parseInt(parts[2]);
        }

        switch (name) {
            case "FUSE8" -> {
                BinaryFuse8GpuFilterData data =
                        BinaryFuse8AddressPresence.populateFrom(source).toGpuFilterData();
                int[] meta = {
                    (int) data.seed(),
                    (int) (data.seed() >>> 32),
                    data.segmentLength(),
                    data.segmentLengthMask(),
                    data.segmentCountLength()
                };
                context.prepareBenchFilterProbeFuse8(data.fingerprints(), meta, probeKeys);
            }
            case "BLOCKED_BLOOM" -> {
                BlockedBloomGpuFilterData data = (bpe > 0 && probesPerKey > 0
                                ? BlockedBloomAddressPresence.populateFrom(source, probesPerKey, bpe)
                                : BlockedBloomAddressPresence.populateFrom(source))
                        .toGpuFilterData();
                context.prepareBenchFilterProbeBlockedBloom(data.words(), data.toMetadata(), probeKeys);
            }
            default -> throw new IllegalArgumentException("unknown filter: " + filter);
        }
    }

    /**
     * Probes every uploaded key on the device and blocks until the kernel completes.
     *
     * @return the number of work-items launched, consumed by JMH so the call cannot be elided
     */
    @Benchmark
    public int probeAllKeys() {
        return context.runBenchFilterProbe();
    }

    /** Releases the device buffers, the kernel and the context. */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.releaseBenchFilterProbe();
            context.close();
        }
    }
}
