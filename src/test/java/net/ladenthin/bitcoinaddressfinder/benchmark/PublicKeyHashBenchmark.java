// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.Hash160;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
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
 * Throughput benchmark for the two SHA-256 + RIPEMD-160 implementations
 * encapsulated by {@link Hash160}.
 *
 * <p>{@link Hash160#DEFAULT_USE_FAST} selects between two hash implementations:</p>
 * <ul>
 *   <li>{@code true} &#x2192; Guava SHA-256 + Bouncy Castle RIPEMD-160
 *       (the fast path)</li>
 *   <li>{@code false} &#x2192; BitcoinJ {@code CryptoUtils.sha256hash160}
 *       (the reference path)</li>
 * </ul>
 *
 * <p>This benchmark parameterises over {@code useFast = {false, true}} so that
 * both paths are measured side by side. The expected result is that {@code true}
 * yields significantly more ops/sec &#x2014; which is why
 * {@link Hash160#DEFAULT_USE_FAST} defaults to {@code true}.</p>
 *
 * <p>Run locally:</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args="PublicKeyHashBenchmark -prof gc"
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class PublicKeyHashBenchmark {

    /**
     * Selects the hash implementation passed to the {@link Hash160} constructor:
     * {@code true} &#x2192; Guava + Bouncy Castle,
     * {@code false} &#x2192; BitcoinJ {@code CryptoUtils}.
     *
     * <p>Corresponds directly to {@link Hash160#DEFAULT_USE_FAST}.</p>
     */
    @Param({"false", "true"})
    public boolean useFast;

    /** Fixed 65-byte uncompressed public key ({@code 04 || X || Y}) used as hash input. */
    private byte[] uncompressedKey = new byte[0];

    /** Fixed 33-byte compressed public key ({@code 02/03 || X}) used as hash input. */
    private byte[] compressedKey = new byte[0];

    /** {@link Hash160} instance configured with the selected implementation. */
    private Hash160 hash160 = new Hash160();

    /** Initializes fixed-content key byte arrays and the {@link Hash160} instance. */
    @Setup
    public void setUp() {
        hash160 = new Hash160(useFast);
        uncompressedKey = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        uncompressedKey[0] = (byte) PublicKeyBytes.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT;
        for (int i = 1; i < uncompressedKey.length; i++) {
            uncompressedKey[i] = (byte) i;
        }
        compressedKey = PublicKeyBytes.createCompressedBytes(uncompressedKey);
    }

    /**
     * Hashes an uncompressed public key (65 bytes) via {@link Hash160#hash(byte[])}.
     *
     * <p>Uncompressed keys are the larger input; this benchmark shows the per-byte
     * cost difference between the two implementations at full 65-byte input size.</p>
     *
     * @param bh JMH blackhole to prevent dead-code elimination
     */
    @Benchmark
    public void hashUncompressed(Blackhole bh) {
        bh.consume(hash160.hash(uncompressedKey));
    }

    /**
     * Hashes a compressed public key (33 bytes) via {@link Hash160#hash(byte[])}.
     *
     * <p>Compressed keys are the smaller input; this benchmark shows whether the
     * implementation gap persists at reduced input size.</p>
     *
     * @param bh JMH blackhole to prevent dead-code elimination
     */
    @Benchmark
    public void hashCompressed(Blackhole bh) {
        bh.consume(hash160.hash(compressedKey));
    }
}
