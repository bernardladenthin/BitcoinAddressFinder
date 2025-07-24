// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.util.ArrayList;
import java.util.List;

public class CFinder {
    
    public List<CKeyProducerJavaRandom> keyProducerJavaRandom = new ArrayList<>();
    public List<CKeyProducerJavaBip39> keyProducerJavaBip39 = new ArrayList<>();
    public List<CKeyProducerJavaIncremental> keyProducerJavaIncremental = new ArrayList<>();
    public List<CKeyProducerJavaSocket> keyProducerJavaSocket = new ArrayList<>();
    public List<CKeyProducerJavaZmq> keyProducerJavaZmq = new ArrayList<>();
    
    public CConsumerJava consumerJava;
    
    public List<CProducerJava> producerJava = new ArrayList<>();
    public List<CProducerJavaSecretsFiles> producerJavaSecretsFiles = new ArrayList<>();
    public List<CProducerOpenCL> producerOpenCL = new ArrayList<>();
    
}
