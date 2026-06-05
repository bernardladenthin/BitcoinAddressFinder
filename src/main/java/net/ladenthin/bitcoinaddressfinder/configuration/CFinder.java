// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the {@code Find} command: producers, key producers and consumer.
 */
@ToString
@EqualsAndHashCode
public class CFinder {

    /** Creates a new {@link CFinder}. */
    public CFinder() {}

    /** Random-based key producer configurations. */
    public List<CKeyProducerJavaRandom> keyProducerJavaRandom = new ArrayList<>();
    /** BIP39-based key producer configurations. */
    public List<CKeyProducerJavaBip39> keyProducerJavaBip39 = new ArrayList<>();
    /** Incremental key producer configurations. */
    public List<CKeyProducerJavaIncremental> keyProducerJavaIncremental = new ArrayList<>();
    /** TCP socket key producer configurations. */
    public List<CKeyProducerJavaSocket> keyProducerJavaSocket = new ArrayList<>();
    /** WebSocket key producer configurations. */
    public List<CKeyProducerJavaWebSocket> keyProducerJavaWebSocket = new ArrayList<>();
    /** ZeroMQ key producer configurations. */
    public List<CKeyProducerJavaZmq> keyProducerJavaZmq = new ArrayList<>();

    /** Consumer configuration. */
    public @Nullable CConsumerJava consumerJava;

    /**
     * Maximum time (in seconds) {@link net.ladenthin.bitcoinaddressfinder.Finder#shutdownAndAwaitTermination()}
     * waits for the producer executor to terminate before giving up. Default: ~100,000 years
     * (effectively unbounded; the call exists as a safety net). Tests override with a small
     * value (e.g. {@code 20}) to exercise the timeout path in seconds rather than years.
     */
    public long awaitTerminateSeconds = 365L * 1000L * 24L * 3600L;

    /** Java CPU producer configurations. */
    public List<CProducerJava> producerJava = new ArrayList<>();
    /** Secrets-file based producer configurations. */
    public List<CProducerJavaSecretsFiles> producerJavaSecretsFiles = new ArrayList<>();
    /** OpenCL (GPU) producer configurations. */
    public List<CProducerOpenCL> producerOpenCL = new ArrayList<>();
}
