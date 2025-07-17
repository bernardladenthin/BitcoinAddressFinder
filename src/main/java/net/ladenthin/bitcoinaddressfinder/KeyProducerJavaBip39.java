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

public class KeyProducerJavaBip39 extends KeyProducerJava {

    private final CKeyProducerJavaBip39 cKeyProducerJavaBip39;
    private final KeyUtility keyUtility;
    private final BitHelper bitHelper;
    private final BIP39KeyProducer bip39KeyProducer;
    
    public KeyProducerJavaBip39(CKeyProducerJavaBip39 cKeyProducerJavaBip39, KeyUtility keyUtility, BitHelper bitHelper) {
        super(cKeyProducerJavaBip39);
        this.cKeyProducerJavaBip39 = cKeyProducerJavaBip39;
        this.keyUtility = keyUtility;
        this.bitHelper = bitHelper;
        
        bip39KeyProducer = new BIP39KeyProducer(
            cKeyProducerJavaBip39.mnemonic,
            cKeyProducerJavaBip39.passphrase,
            cKeyProducerJavaBip39.bip32Path,
            cKeyProducerJavaBip39.getCreationTimeInstant(),
            cKeyProducerJavaBip39.hardened
        );
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) throws NoMoreSecretsAvailableException {
        return keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, cKeyProducerJavaBip39.privateKeyMaxNumBits, bip39KeyProducer);
    }
}
