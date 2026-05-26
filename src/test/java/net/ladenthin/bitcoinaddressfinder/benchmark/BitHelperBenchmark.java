// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Throughput benchmark for {@link BitHelper} bit-manipulation operations.
 *
 * <p>{@link BitHelper#getKillBits(int)} and {@link BitHelper#convertBitsToSize(int)} sit
 * on the key-producer hot path: they are called once per producer batch to compute the
 * address-space partition size. This benchmark measures their allocation and computation cost.</p>
 *
 * <p>Run locally:</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args="BitHelperBenchmark -prof gc"
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class BitHelperBenchmark {

    /** Bit counts covering the typical producer batch-size range (1, 8, 16, 20 bits). */
    @Param({"1", "8", "16", "20"})
    public int bits;

    private final BitHelper bitHelper = new BitHelper();

    /**
     * Benchmarks {@link BitHelper#getKillBits(int)} across several bit counts.
     *
     * <p>Measures {@link BigInteger} allocation and shift cost for the kill-bits computation
     * used to mask off the low-order bits of a private key batch start.</p>
     *
     * @param bh JMH blackhole to prevent dead-code elimination
     */
    @Benchmark
    public void getKillBits(Blackhole bh) {
        bh.consume(bitHelper.getKillBits(bits));
    }

    /**
     * Benchmarks {@link BitHelper#convertBitsToSize(int)} across several bit counts.
     *
     * <p>Measures the cost of computing the batch size ({@code 2^bits}) as a plain
     * {@code int} shift &#x2014; the cheapest of the two helper operations.</p>
     *
     * @param bh JMH blackhole to prevent dead-code elimination
     */
    @Benchmark
    public void convertBitsToSize(Blackhole bh) {
        bh.consume(bitHelper.convertBitsToSize(bits));
    }
}
