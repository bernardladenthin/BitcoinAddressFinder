// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLGridResult;
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
 * Grid-size (batch-size) and loop-count parameter sweep for the OpenCL kernel.
 *
 * <p>Captures the &quot;sweep gridNumBits&quot; idea from the cjherm/BitcoinAddressFinder23
 * fork, rewritten for the actual current mainline API surface:</p>
 *
 * <ul>
 *   <li>the &quot;grid size&quot; lever is {@code CProducer.batchSizeInBits} (work size
 *       = {@code 1 << batchSizeInBits}); there is no {@code gridNumBits} field</li>
 *   <li>the second lever is {@link CProducerOpenCL#loopCount} (inner iterations per
 *       work-item, default 1)</li>
 *   <li>there is no {@code chunkMode} or {@code kernelMode} field today</li>
 *   <li>kernel entry is {@link OpenCLContext#createKeys(BigInteger)}; it takes a single
 *       private-key base, not an array, and returns one {@link OpenCLGridResult} per
 *       launch</li>
 *   <li>OpenCL availability is gated via
 *       {@link OpenCLPlatformAssume#assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable()},
 *       the existing project convention; if no device is present the JMH trial fails
 *       with {@code AssumptionViolatedException} which JMH reports as {@code ERROR} on
 *       that {@code @Param} combo (a deliberate &quot;asymptote visible&quot; choice rather
 *       than silently downgrading)</li>
 * </ul>
 *
 * <p>What is measured: the steady-state number of kernel launches per second at
 * each {@code (batchSizeInBits, loopCount)} corner. The number of candidate keys
 * produced per launch is {@code 1 << batchSizeInBits}, so candidates/sec at a
 * given data point = JMH-reported ops/sec &#x00D7; {@code (1 << batchSizeInBits)}.
 * Note: JMH's {@code @OperationsPerInvocation} would normalize this automatically
 * but only accepts a compile-time constant and therefore cannot vary with
 * {@code @Param} &#x2014; intentional limitation.</p>
 *
 * <p>Validity constraint: {@link CProducerOpenCL#loopCount}'s Javadoc requires
 * {@code batchSizeInBits % loopCount == 0}. The default {@code @Param} corners
 * below are all valid; if you override the parameter sets via JMH CLI
 * ({@code -p batchSizeInBits=...,-p loopCount=...}) the OpenCL kernel build will
 * fail loudly at the {@code @Setup} step rather than silently produce wrong data.</p>
 *
 * <p>Companion benchmark to implement later: context-reuse amortisation
 * (analogue of cjherm's {@code CtxRoundsIteratorBenchmark}). Cannot share a class
 * with this one because JMH cannot switch {@code @Setup} {@link Level} via
 * {@code @Param}.</p>
 *
 * <p>Run locally (requires an OpenCL device):</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args=&quot;GridSizeSweepBenchmark&quot;
 *
 * # narrower sweep
 * mvn test-compile exec:java \
 *   -Dexec.args=&quot;GridSizeSweepBenchmark -p batchSizeInBits=18,20 -p loopCount=1,2&quot;
 *
 * # allocation profile
 * mvn test-compile exec:java -Dexec.args=&quot;GridSizeSweepBenchmark -prof gc&quot;
 * </pre>
 *
 * <p>This benchmark is intentionally NOT executed by {@code mvn test} (no
 * {@code @Test} annotation, lives in the JMH-runner path); CI runners typically
 * lack an OpenCL device.</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(
        value = 1,
        jvmArgsAppend = {
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED"
        })
public class GridSizeSweepBenchmark {

    /**
     * Log2 of the per-launch work size. Each launch produces
     * {@code 1 << batchSizeInBits} candidate keys.
     */
    @Param({"16", "18", "20"})
    public int batchSizeInBits;

    /**
     * Inner iterations per OpenCL work-item. Must be a power of two, and
     * {@code batchSizeInBits} must be divisible by {@code loopCount}
     * (per {@link CProducerOpenCL#loopCount} Javadoc).
     */
    @Param({"1", "2"})
    public int loopCount;

    private OpenCLContext ctx;
    private BigInteger privateKeyBase;

    /**
     * Creates a new {@link GridSizeSweepBenchmark} (no-arg constructor for JMH).
     */
    public GridSizeSweepBenchmark() {
        // no-op
    }

    /**
     * Builds an OpenCL context for the current {@code @Param} combo. Skips
     * the trial cleanly (JMH reports {@code ERROR} on the data point) if no
     * OpenCL device is available, mirroring how {@link OpenCLPlatformAssume}
     * is used in {@code ProducerOpenCLTest} and {@code OpenCLBuilderTest}.
     *
     * @throws Exception if {@link OpenCLContext#init()} fails (typically VRAM
     *                   exhaustion on too-large {@code batchSizeInBits} or
     *                   kernel build error on invalid divisibility)
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        final CProducerOpenCL p = new CProducerOpenCL();
        p.batchSizeInBits = batchSizeInBits;
        p.loopCount = loopCount;

        ctx = new OpenCLContext(p, new BitHelper());
        ctx.init();

        privateKeyBase = new BigInteger(256, new SecureRandom());
    }

    /**
     * One kernel launch at the current {@code @Param} combo. Returns the
     * {@link OpenCLGridResult} so JMH's dead-code elimination cannot remove
     * the call.
     *
     * @return the grid result of one OpenCL kernel launch
     */
    @Benchmark
    public OpenCLGridResult oneKernelLaunch() {
        return ctx.createKeys(privateKeyBase);
    }

    /**
     * Releases the OpenCL context built in {@link #setUp()}. Guarded against
     * the &quot;setUp threw before ctx was assigned&quot; case so JMH can still
     * record the ERROR for that data point.
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (ctx != null && !ctx.isClosed()) {
            ctx.close();
        }
    }
}
