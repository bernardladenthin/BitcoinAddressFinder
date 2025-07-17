// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import org.bitcoinj.base.Network;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

public class KeyProducerJavaBip39Test {
    
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    
    String keyProducerId = "exampleId";
    
    
    private BigInteger[] generateSecrets() throws NoMoreSecretsAvailableException {
        CKeyProducerJavaBip39 config = new CKeyProducerJavaBip39();
        config.keyProducerId = keyProducerId;
        config.privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
        config.mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        config.passphrase = "";
        config.creationTimeSeconds = 0L;
        config.hardened = false;
        
        KeyProducerJavaBip39 producer = new KeyProducerJavaBip39(config, keyUtility, bitHelper);
        return producer.createSecrets(bitHelper.convertBitsToSize(0), true);
    }
    
    @Test
    public void testBip39() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets();
        assertThat(result.length, is(equalTo(1)));
    }
}
