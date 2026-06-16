// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
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
 * Isolated microbenchmark for the OpenCL modular inverse {@code inv_mod}, comparing the safegcd path
 * against the legacy binary extended-GCD, at two operand widths (≈256-bit and ≈160-bit).
 *
 * <p>Motivation: the end-to-end {@code GpuFuse8FilterBenchmark} showed safegcd ≈ +45% on the whole
 * kernel, but that mixes the inverse with the rest of the pipeline. This benchmark drives only the
 * {@code bench_inv_mod} kernel (each work-item does {@code iterations} inverses over a full grid, so
 * warp divergence is realistic) to attribute the win to the inverse itself and to show how it depends
 * on operand width:</p>
 *
 * <ul>
 *   <li><b>safegcd</b> ({@code useSafeGcdInverse=true}) runs a <b>fixed</b> 600 divsteps for every
 *       operand, branch-uniform — the same cost at 160- and 256-bit, and identical across warp lanes.</li>
 *   <li>the <b>binary GCD</b> ({@code useSafeGcdInverse=false}) iterates a number of times that
 *       depends on the operand's bit length and pattern, so it is cheaper for small (160-bit) operands
 *       but <b>diverges</b> across lanes — the warp pays for its slowest lane.</li>
 * </ul>
 *
 * <p><b>Why 256-bit is the case that matters in production:</b> {@code inv_mod} is only ever called on
 * field coordinates (X/Y/Z mod {@code p}), which are pseudo-random in {@code [0, p)} — i.e. full
 * ≈256-bit — regardless of the private-key range being scanned (even a tiny private key yields a
 * 256-bit public-key coordinate). The 160-bit arm is included only to expose the binary GCD's
 * input-dependence; the production workload is the 256-bit arm, where safegcd wins.</p>
 *
 * <p>The selector is compiled in (see {@link CProducerOpenCL#useSafeGcdInverse} →
 * {@code -D USE_LEGACY_BINARY_GCD_INV_MOD}), so each {@code @Param} value rebuilds the kernel in
 * {@code @Setup} (outside the timed region). Self-skips when no OpenCL 2.0+ device is present. Reads
 * §6 of {@code docs/performance.md} first — the laptop GPU is thermally noisy; compare matched arms.</p>
 *
 * <p>One JMH op = one {@link OpenCLContext#runBenchInvMod} launch =
 * {@code (1 << gridSizeInBits) × iterations} inverses, so inverses/sec = ops/sec × that product.</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 3, time = 10)
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
public class InvModBenchmark {

    /** {@code true} = safegcd inverse (default); {@code false} = legacy binary GCD. */
    @Param({"true", "false"})
    public boolean useSafeGcdInverse;

    /** Operand width: {@code 256} (production-realistic field elements) or {@code 160} (small). */
    @Param({"256", "160"})
    public int inputBits;

    /** Log2 of the number of work-items launched (the grid size; fills the GPU for divergence realism). */
    @Param({"18"})
    public int gridSizeInBits;

    /** Number of inverses each work-item performs per launch (amortises launch/alloc overhead). */
    @Param({"256"})
    public int iterations;

    private OpenCLContext ctx;
    private int globalWorkSize;
    private boolean inputHighLimbsZero;

    /** Creates a new {@link InvModBenchmark} (no-arg constructor for JMH). */
    public InvModBenchmark() {
        // no-op
    }

    /**
     * Builds an OpenCL context with the selected inverse implementation. Skips the trial cleanly
     * (JMH reports {@code ERROR}) if no OpenCL 2.0+ device is present.
     *
     * @throws Exception if {@link OpenCLContext#init()} fails (e.g. kernel build error)
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        final CProducerOpenCL p = new CProducerOpenCL();
        // batchSizeInBits only sizes the production result buffer; bench_inv_mod ignores it, but keep
        // it consistent with the grid we launch so init()'s buffers are sane.
        p.batchSizeInBits = gridSizeInBits;
        p.keysPerWorkItem = 1;
        p.useSafeGcdInverse = useSafeGcdInverse;

        ctx = new OpenCLContext(p, new BitHelper());
        ctx.init();

        globalWorkSize = 1 << gridSizeInBits;
        inputHighLimbsZero = inputBits == 160;
    }

    /**
     * One launch of {@code bench_inv_mod}: {@code globalWorkSize × iterations} modular inverses.
     */
    @Benchmark
    public void inverseGrid() {
        ctx.runBenchInvMod(globalWorkSize, iterations, inputHighLimbsZero);
    }

    /**
     * Releases the OpenCL context. Guarded against a setUp that threw before assigning {@code ctx}.
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        if (ctx != null && !ctx.isClosed()) {
            ctx.close();
        }
    }
}
