// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CFinder {
    
    public List<CKeyProducerJavaRandom> keyProducerJavaRandom = new ArrayList<>();
    public List<CKeyProducerJavaBip39> keyProducerJavaBip39 = new ArrayList<>();
    public List<CKeyProducerJavaIncremental> keyProducerJavaIncremental = new ArrayList<>();
    public List<CKeyProducerJavaSocket> keyProducerJavaSocket = new ArrayList<>();
    public List<CKeyProducerJavaWebSocket> keyProducerJavaWebSocket = new ArrayList<>();
    public List<CKeyProducerJavaZmq> keyProducerJavaZmq = new ArrayList<>();
    
    public @Nullable CConsumerJava consumerJava;
    
    public List<CProducerJava> producerJava = new ArrayList<>();
    public List<CProducerJavaSecretsFiles> producerJavaSecretsFiles = new ArrayList<>();
    public List<CProducerOpenCL> producerOpenCL = new ArrayList<>();
    
}
