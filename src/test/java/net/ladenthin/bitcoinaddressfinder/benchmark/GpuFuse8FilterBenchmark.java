// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.KernelProfileStage;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLGridResult;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
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
 * A/B throughput benchmark for the GPU-side Binary Fuse 8 address-presence filter
 * ({@code BINARY_FUSE_8} compact output) versus the legacy full-transfer path.
 *
 * <p>This is the throughput companion to {@code OpenCLCompactOutputIntegrationTest}
 * (which proves the compact path is <em>correct</em>) and to {@code GridSizeSweepBenchmark}
 * (which sweeps batch size with no filter). It answers a single question: <b>how much faster
 * is one kernel launch when the GPU filters inline and transmits only the handful of hits,
 * compared with transferring every work-item result back over PCIe?</b>
 *
 * <p>The lever is the single boolean {@code @Param gpuFilter}:</p>
 * <ul>
 *   <li>{@code gpuFilter=false} &mdash; baseline. No filter is uploaded, so
 *       {@link OpenCLContext#createKeys(BigInteger)} runs in full-transfer mode and reads back
 *       all {@code 1 << batchSizeInBits} work-item results (the legacy behaviour).</li>
 *   <li>{@code gpuFilter=true} &mdash; FUSE_8 compact mode
 *       ({@link CProducerOpenCL#enableGpuFilter}{@code =true},
 *       {@link CProducerOpenCL#transferAll}{@code =false}). A Binary Fuse 8 filter populated
 *       with {@code filterEntries} addresses that do <em>not</em> match the scanned candidates
 *       is uploaded to VRAM; the kernel then transmits only the rare false-positives
 *       (~0.4&nbsp;% of the batch) instead of the whole grid.</li>
 * </ul>
 *
 * <p>The two rows JMH prints for the same {@code batchSizeInBits} are directly comparable: the
 * speedup of the {@code true} row over the {@code false} row is the value the GPU filter adds.
 *
 * <p>What is measured: end-to-end kernel-launch throughput (launch + readback + host-side parse
 * via {@link OpenCLContext#createKeys(BigInteger)} and {@code getPublicKeyBytes()}) at each
 * {@code (gpuFilter, batchSizeInBits)} corner. The candidate keys produced per launch is
 * {@code 1 << batchSizeInBits}, so candidates/sec at a data point = JMH ops/sec
 * &#x00D7; {@code (1 << batchSizeInBits)}. (JMH's {@code @OperationsPerInvocation} would
 * normalise this automatically but only accepts a compile-time constant and therefore cannot
 * track the {@code @Param}.)</p>
 *
 * <p>OpenCL availability is gated via
 * {@link OpenCLPlatformAssume#assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable()};
 * compact mode additionally needs OpenCL 2.0+ (atomic_add), which the same assume covers. If no
 * suitable device is present the JMH trial fails with {@code AssumptionViolatedException}, which
 * JMH reports as {@code ERROR} on that {@code @Param} combo (a deliberate &quot;asymptote
 * visible&quot; choice rather than silently downgrading).</p>
 *
 * <p><b>Why one long iteration rather than many short samples:</b> the only meaningful one-time
 * cost here is OpenCL <b>kernel compilation</b>, which happens once in {@link OpenCLContext#init()}
 * (the {@code @Setup(Level.Trial)} step, outside the timed region). JVM warmup iterations do not
 * help a GPU benchmark, so the defaults below spend the budget on a single long measurement
 * iteration (~200&nbsp;s) preceded by a short warmup just long enough for the GPU to reach steady
 * clocks. Override with {@code -wi 2 -i 4} for a quick (~2&nbsp;min) sanity check.</p>
 *
 * <p>Run locally (requires an OpenCL 2.0+ device):</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args=&quot;GpuFuse8FilterBenchmark&quot;
 *
 * # sweep batch size, both filter modes (the saving grows with the grid)
 * mvn test-compile exec:java \
 *   -Dexec.args=&quot;GpuFuse8FilterBenchmark -p batchSizeInBits=19,20&quot;
 *
 * # quick sanity check (many short samples instead of one long one)
 * mvn test-compile exec:java \
 *   -Dexec.args=&quot;GpuFuse8FilterBenchmark -wi 2 -i 4&quot;
 * </pre>
 *
 * <p>This benchmark is intentionally NOT executed by {@code mvn test} (no {@code @Test}
 * annotation, lives in the JMH-runner path); CI runners typically lack a GPU.</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
// One long measurement iteration: the one-time cost is OpenCL kernel compilation (done in
// @Setup, outside timing), not JVM warmup. A short warmup just lets the GPU clocks ramp up.
@Warmup(iterations = 1, time = 30)
@Measurement(iterations = 1, time = 200)
@Fork(
        value = 1,
        // Master JVM-flag list — keep identical (same set + same order) to the pom.xml
        // <argLine>, .mvn/jvm.config and examples/*.bat so the JMH fork matches the JVM
        // Surefire uses (lmdbjava reflects into sun.nio.ch / jdk.internal.ref).
        jvmArgsAppend = {
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "--add-exports=java.base/java.lang=ALL-UNNAMED",
            "--add-exports=java.base/java.io=ALL-UNNAMED",
            "--add-exports=java.base/java.nio=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-exports=jdk.management/com.sun.management.internal=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        })
public class GpuFuse8FilterBenchmark {

    /** Width of a hash160 address entry in bytes. */
    private static final int HASH160_LENGTH = 20;

    /** Deterministic seed for the synthetic filter contents, so trials are reproducible. */
    private static final long FILTER_CONTENT_SEED = 0x9E3779B97F4A7C15L;

    /**
     * When {@code true} a Binary Fuse 8 filter is uploaded and the kernel runs in compact mode
     * (transmits only hits); when {@code false} the kernel transfers every work-item result.
     */
    @Param({"false", "true"})
    public boolean gpuFilter;

    /**
     * Log2 of the per-launch work size. Each launch produces {@code 1 << batchSizeInBits}
     * candidate keys.
     */
    @Param({"19"})
    public int batchSizeInBits;

    /**
     * Inner iterations per OpenCL work-item (the scalar-walker / comb amortisation lever). Default
     * {@code 1} preserves the original filter-vs-transfer comparison; set it to the device sweet
     * spot (e.g. {@code 128} on the RTX 3070) together with {@code profiling=true} to attribute
     * compact-mode cost between GPU compute and PCIe readback at the real operating point.
     * {@code 1 << batchSizeInBits} must be divisible by {@code keysPerWorkItem}.
     */
    @Param({"1"})
    public int keysPerWorkItem;

    /**
     * Number of (non-matching) addresses inserted into the GPU filter. Only relevant when
     * {@code gpuFilter=true}; sized to model a realistic database the scanned random keys do
     * not collide with, so the compact path emits only Binary Fuse 8 false-positives.
     */
    @Param({"1048576"})
    public int filterEntries;

    /**
     * Enables OpenCL device-side profiling ({@link CProducerOpenCL#enableProfiling}). When
     * {@code true}, {@link #tearDown()} prints the device-side kernel-execution and result-readback
     * times of the last launch, isolating GPU compute from PCIe transfer (and, against the
     * wall-clock throughput, from host-side parsing). Off by default so the headline throughput
     * rows are not perturbed by profiling overhead; enable with {@code -p profiling=true}.
     */
    @Param({"false"})
    public boolean profiling;

    /**
     * Selects the kernel's modular-inverse implementation ({@link CProducerOpenCL#useSafeGcdInverse}).
     * {@code true} (default) uses the safegcd path; {@code false} builds the legacy binary-GCD inverse.
     * Sweep both (e.g. {@code -p useSafeGcdInverse=true,false}) to reproduce the Stage 4 A/B in §5 of
     * {@code docs/performance.md} in a single JMH run.
     */
    @Param({"true"})
    public boolean useSafeGcdInverse;

    /**
     * Compile-time kernel profiling stage ({@link CProducerOpenCL#kernelProfileStage}). {@code FULL}
     * is the real kernel; {@code ONE_HASH160} / {@code NO_HASH160} short-circuit hashing to attribute
     * kernel time. Sweep {@code -p kernelProfileStage=FULL,ONE_HASH160,NO_HASH160} (compact mode) and
     * diff the throughputs per {@code docs/performance.md} "Stage attribution". Non-FULL modes emit
     * incorrect hashes — timing only.
     */
    @Param({"FULL"})
    public KernelProfileStage kernelProfileStage;

    private OpenCLContext ctx;
    private BigInteger privateKeyBase;

    /** Creates a new {@link GpuFuse8FilterBenchmark} (no-arg constructor for JMH). */
    public GpuFuse8FilterBenchmark() {
        // no-op
    }

    /**
     * Builds an OpenCL context for the current {@code @Param} combo and, when
     * {@code gpuFilter=true}, populates and uploads a Binary Fuse 8 filter of
     * {@code filterEntries} addresses unrelated to the scanned key range.
     *
     * <p>Skips the trial cleanly (JMH reports {@code ERROR} on the data point) if no
     * OpenCL 2.0+ device is available, mirroring {@code GridSizeSweepBenchmark} and
     * {@code OpenCLCompactOutputIntegrationTest}.</p>
     *
     * @throws Exception if {@link OpenCLContext#init()} fails (e.g. VRAM exhaustion or
     *                   kernel build error)
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        final CProducerOpenCL p = new CProducerOpenCL();
        p.batchSizeInBits = batchSizeInBits;
        p.keysPerWorkItem = keysPerWorkItem;
        // Compact mode is requested only for the filter arm; the baseline arm keeps the legacy
        // full-transfer layout (no filter uploaded -> createKeys() transfers every result).
        p.enableGpuFilter = gpuFilter;
        p.transferAll = false;
        p.enableProfiling = profiling;
        p.useSafeGcdInverse = useSafeGcdInverse;
        p.kernelProfileStage = kernelProfileStage;

        ctx = new OpenCLContext(p, new BitHelper());
        ctx.init();

        if (gpuFilter) {
            BinaryFuse8GpuFilterData payload = BinaryFuse8AddressPresence.populateFrom(
                            nonMatchingAddresses(filterEntries))
                    .toGpuFilterData();
            long seed = payload.seed();
            ctx.uploadGpuFilter(
                    payload.fingerprints(),
                    (int) seed,
                    (int) (seed >>> 32),
                    payload.segmentLength(),
                    payload.segmentLengthMask(),
                    payload.segmentCountLength());
        }

        // A 256-bit base far from the synthetic filter contents: scanned candidates will not
        // collide with the filter, so the compact arm emits only Binary Fuse 8 false-positives.
        privateKeyBase = new BigInteger(256, new SecureRandom());
    }

    /**
     * One kernel launch at the current {@code @Param} combo, including the host-side parse of the
     * returned grid. Returns the number of emitted work-items so JMH's dead-code elimination
     * cannot remove the call (and so the readback + parse cost is genuinely incurred).
     *
     * @return the number of public-key results the launch produced
     */
    @Benchmark
    public int oneKernelLaunch() {
        // try-with-resources returns the readback buffer to the reuse pool after parsing.
        try (OpenCLGridResult result = ctx.createKeys(privateKeyBase)) {
            return result.getPublicKeyBytes().length;
        }
    }

    /**
     * Releases the OpenCL context built in {@link #setUp()}. Guarded against the
     * &quot;setUp threw before ctx was assigned&quot; case so JMH can still record the ERROR
     * for that data point.
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (ctx != null && !ctx.isClosed()) {
            if (profiling) {
                ctx.getOpenClTask().ifPresent(task -> {
                    long kernelNanos = task.getLastKernelExecutionNanos();
                    long readbackNanos = task.getLastResultReadbackNanos();
                    // Printed to stdout (captured in the JMH log) rather than returned as a JMH
                    // metric: these are device-side timings of the last launch, used to attribute
                    // cost between GPU compute and PCIe transfer, not the benchmark's throughput.
                    System.out.printf(
                            "[profiling] gpuFilter=%s batchSizeInBits=%d keysPerWorkItem=%d -> kernel=%.3f ms, readback=%.3f ms%n",
                            gpuFilter,
                            batchSizeInBits,
                            keysPerWorkItem,
                            kernelNanos / 1_000_000.0,
                            readbackNanos / 1_000_000.0);
                });
            }
            ctx.close();
        }
    }

    /**
     * Builds an {@link AddressIterable} of {@code count} deterministic, pseudo-random 20-byte
     * hash160 entries. The entries are unrelated to any derived candidate key, so they model a
     * database the scanned random keys do not match.
     *
     * @param count number of synthetic addresses to generate
     * @return an iterable over {@code count} hash160 entries
     */
    private static AddressIterable nonMatchingAddresses(int count) {
        final Random random = new Random(FILTER_CONTENT_SEED);
        final List<byte[]> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] hash160 = new byte[HASH160_LENGTH];
            random.nextBytes(hash160);
            entries.add(hash160);
        }
        return new AddressIterable() {
            @Override
            public Stream<ByteBuffer> addresses() {
                return entries.stream().map(ByteBuffer::wrap);
            }

            @Override
            public long count() {
                return entries.size();
            }
        };
    }
}
