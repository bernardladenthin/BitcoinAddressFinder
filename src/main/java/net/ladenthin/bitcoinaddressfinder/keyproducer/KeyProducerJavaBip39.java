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
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.BIP39KeyProducer;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.RandomSecretSupplier;
import net.ladenthin.bitcoinaddressfinder.SecretSupplier;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import org.slf4j.Logger;

public class KeyProducerJavaBip39 extends KeyProducerJava<CKeyProducerJavaBip39> {

    private final KeyUtility keyUtility;
    private final BitHelper bitHelper;
    private final SecretSupplier randomSupplier;
    private final BIP39KeyProducer bip39KeyProducer;
    
    public KeyProducerJavaBip39(CKeyProducerJavaBip39 cKeyProducerJavaBip39, KeyUtility keyUtility, BitHelper bitHelper, Logger logger) {
        super(cKeyProducerJavaBip39, logger);
        this.keyUtility = keyUtility;
        this.bitHelper = bitHelper;
        
        bip39KeyProducer = new BIP39KeyProducer(
            cKeyProducerJavaBip39.mnemonic,
            cKeyProducerJavaBip39.passphrase,
            cKeyProducerJavaBip39.bip32Path,
            cKeyProducerJavaBip39.getCreationTimeInstant(),
            cKeyProducerJavaBip39.hardened
        );
        randomSupplier = new RandomSecretSupplier(bip39KeyProducer);
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) throws NoMoreSecretsAvailableException {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);
        return keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, this.cKeyProducerJava.privateKeyMaxNumBits, randomSupplier);
    }

    @Override
    public void interrupt() {
    }
}
