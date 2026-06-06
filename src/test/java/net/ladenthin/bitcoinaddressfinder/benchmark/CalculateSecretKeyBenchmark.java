// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Throughput benchmark comparing the two combine modes of
 * {@link KeyUtility#calculateSecretKey(BigInteger, int, boolean)}: bitwise OR
 * ({@code useOr=true}, the production default {@link KeyUtility#CALCULATE_SECRET_KEY_USE_OR})
 * versus arithmetic ADD ({@code useOr=false}).
 *
 * <p>{@code calculateSecretKey} sits on the key-producer hot path: it is called once per key
 * inside every batch to turn the masked secret base plus the per-key offset into a concrete
 * private-key candidate. OR avoids carry propagation and is expected to be at least as fast as
 * ADD; this benchmark exists to keep that assumption measured rather than assumed, and to keep
 * the ADD alternative permanently exercised so it is never dropped as dead code.</p>
 *
 * <p>The {@code aligned} secret base mirrors the production contract (low bits masked off, so
 * OR and ADD are equivalent); the {@code keyNumber} param sweeps a representative batch index
 * range.</p>
 *
 * <p>Run locally:</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args="CalculateSecretKeyBenchmark"
 * mvn test-compile exec:java -Dexec.args="CalculateSecretKeyBenchmark -prof gc"
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class CalculateSecretKeyBenchmark {

    /** Number of low bits the batch occupies; the secret base is masked to be aligned to it. */
    @Param({"1", "8", "16", "20"})
    public int batchBits;

    /** Representative per-key offset inside the batch. */
    @Param({"0", "1", "65535", "1048575"})
    public int keyNumber;

    private BigInteger alignedSecretBase;

    /**
     * Builds an aligned secret base: a large fixed value with its low {@code batchBits} bits
     * cleared, matching the {@code createSecretBase} contract so OR and ADD are equivalent.
     */
    @Setup
    public void setUp() {
        BigInteger base = BigInteger.valueOf(0xC0FFEEC0FFEEL).shiftLeft(64).or(BigInteger.valueOf(0x1234_5678L));
        BigInteger lowMask = BigInteger.ONE.shiftLeft(batchBits).subtract(BigInteger.ONE);
        alignedSecretBase = base.andNot(lowMask);
    }

    /**
     * Benchmarks the bitwise-OR combine mode (production default).
     *
     * @param bh JMH blackhole to prevent dead-code elimination
     */
    @Benchmark
    public void calculateSecretKeyUsingOr(Blackhole bh) {
        bh.consume(KeyUtility.calculateSecretKey(alignedSecretBase, keyNumber, true));
    }

    /**
     * Benchmarks the arithmetic-ADD combine mode (the retained alternative).
     *
     * @param bh JMH blackhole to prevent dead-code elimination
     */
    @Benchmark
    public void calculateSecretKeyUsingAdd(Blackhole bh) {
        bh.consume(KeyUtility.calculateSecretKey(alignedSecretBase, keyNumber, false));
    }
}
