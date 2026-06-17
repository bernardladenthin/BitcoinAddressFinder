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
 * Isolated microbenchmark for the secp256k1 field multiply, comparing the reduced-radix 2^26
 * implementation ({@code inc_ecc_secp256k1_fe10x26.cl}) against the vendored radix-2^32
 * {@code mul_mod} ({@code copyfromhashcat/inc_ecc_secp256k1.cl}).
 *
 * <p>Motivation: docs/performance.md identifies the field multiply as carry-bound in the radix-2^32
 * representation (every 32x32-&gt;64 partial product propagates a carry immediately; a dedicated
 * {@code sqr_mod} measured <em>slower</em> for this reason). The reduced-radix 2^26 form leaves 6
 * spare bits per limb so partial products accumulate in a 64-bit register before a single carry pass,
 * which is the representation libsecp256k1 uses for its 32-bit field. This benchmark drives the
 * {@code bench_fe_mul} kernel (each work-item chains {@code iterations} multiplies over a full grid)
 * to measure that lever in isolation, before any production hot path adopts it.</p>
 *
 * <p>The two arms keep coordinates in their native form for the whole chain (the 2^26 arm converts
 * in/out only once), mirroring a real EC walk that never round-trips between representations
 * per-multiply. Self-skips when no OpenCL 2.0+ device is present. Reads §6 of docs/performance.md
 * first - the laptop GPU is thermally noisy; compare matched arms (ON-OFF-ON bracketing).</p>
 *
 * <p>One JMH op = one {@link OpenCLContext#runBenchFeMul} launch =
 * {@code (1 << gridSizeInBits) × iterations} field multiplies, so multiplies/sec = ops/sec × that
 * product.</p>
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
public class FieldMulBenchmark {

    /** {@code true} = reduced-radix 2^26 field multiply; {@code false} = vendored radix-2^32 mul_mod. */
    @Param({"true", "false"})
    public boolean useReducedRadix;

    /** Log2 of the number of work-items launched (the grid size; fills the GPU for realistic occupancy). */
    @Param({"18"})
    public int gridSizeInBits;

    /** Number of chained multiplies each work-item performs per launch (amortises launch/alloc overhead). */
    @Param({"4096"})
    public int iterations;

    private OpenCLContext ctx;
    private int globalWorkSize;

    /** Creates a new {@link FieldMulBenchmark} (no-arg constructor for JMH). */
    public FieldMulBenchmark() {
        // no-op
    }

    /**
     * Builds an OpenCL context. Skips the trial cleanly (JMH reports {@code ERROR}) if no OpenCL 2.0+
     * device is present.
     *
     * @throws Exception if {@link OpenCLContext#init()} fails (e.g. kernel build error)
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        final CProducerOpenCL p = new CProducerOpenCL();
        // batchSizeInBits only sizes the production result buffer; bench_fe_mul ignores it, but keep
        // it consistent with the grid we launch so init()'s buffers are sane.
        p.batchSizeInBits = gridSizeInBits;
        p.keysPerWorkItem = 1;

        ctx = new OpenCLContext(p, new BitHelper());
        ctx.init();

        globalWorkSize = 1 << gridSizeInBits;
    }

    /**
     * One launch of {@code bench_fe_mul}: {@code globalWorkSize × iterations} field multiplies in the
     * selected representation.
     */
    @Benchmark
    public void multiplyGrid() {
        ctx.runBenchFeMul(globalWorkSize, iterations, useReducedRadix);
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
