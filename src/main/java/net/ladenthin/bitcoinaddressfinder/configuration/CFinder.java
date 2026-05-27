// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the {@code Find} command: producers, key producers and consumer.
 */
public class CFinder {

    /** Creates a new {@link CFinder}. */
    public CFinder() {
    }

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

    /** Java CPU producer configurations. */
    public List<CProducerJava> producerJava = new ArrayList<>();
    /** Secrets-file based producer configurations. */
    public List<CProducerJavaSecretsFiles> producerJavaSecretsFiles = new ArrayList<>();
    /** OpenCL (GPU) producer configurations. */
    public List<CProducerOpenCL> producerOpenCL = new ArrayList<>();

}
