// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
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
 * Throughput benchmark for the two byte-array reversal strategies controlled
 * by the {@code useXorSwap} constructor parameter of {@link ByteBufferUtility}.
 *
 * <p>The parameter selects between two in-place reversal algorithms in
 * {@link ByteBufferUtility#reverse(byte[])}:</p>
 * <ul>
 *   <li>{@code useXorSwap = true} &#x2192; XOR swap (no temporary variable,
 *       3 XOR operations per element pair)</li>
 *   <li>{@code useXorSwap = false} &#x2192; temporary-variable swap (the
 *       classic approach, 1 read + 2 writes per element pair)</li>
 * </ul>
 *
 * <p>Array sizes mirror the byte lengths that appear on the hot path in
 * {@code EndiannessConverter} (called during OpenCL key scanning):</p>
 * <ul>
 *   <li>{@code 20} &#x2014; RIPEMD-160 / hash160 output
 *       ({@link PublicKeyBytes#RIPEMD160_HASH_NUM_BYTES})</li>
 *   <li>{@code 32} &#x2014; SHA-256 output / private key
 *       ({@link PublicKeyBytes#SHA256_HASH_NUM_BYTES})</li>
 *   <li>{@code 65} &#x2014; uncompressed SEC public key
 *       ({@link PublicKeyBytes#SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BYTES})</li>
 * </ul>
 *
 * <p>Run locally:</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args="ByteSwapBenchmark -prof gc"
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ByteSwapBenchmark {

    /**
     * Selects the reversal algorithm passed to the {@link ByteBufferUtility} constructor:
     * {@code true} &#x2192; XOR swap, {@code false} &#x2192; temporary-variable swap.
     */
    @Param({"false", "true"})
    public boolean useXorSwap;

    /**
     * Array length in bytes.
     *
     * <p>20 = RIPEMD-160 hash, 32 = SHA-256 / private key,
     * 65 = uncompressed public key.</p>
     */
    @Param({"20", "32", "65"})
    public int arraySize;

    private ByteBufferUtility byteBufferUtility = new ByteBufferUtility(false);
    private byte[] array = new byte[0];

    /** Creates a {@link ByteBufferUtility} configured with the selected swap strategy. */
    @Setup
    public void setUp() {
        byteBufferUtility = new ByteBufferUtility(false, useXorSwap);
        array = new byte[arraySize];
        for (int i = 0; i < arraySize; i++) {
            array[i] = (byte) i;
        }
    }

    /**
     * Reverses the byte array in-place via {@link ByteBufferUtility#reverse(byte[])}.
     *
     * <p>The {@link ByteBufferUtility} instance was constructed with {@code useXorSwap}
     * so the call exercises the selected algorithm inside the real production method.</p>
     *
     * @param bh JMH blackhole to prevent dead-code elimination
     */
    @Benchmark
    public void reverse(Blackhole bh) {
        byteBufferUtility.reverse(array);
        bh.consume(array);
    }
}
