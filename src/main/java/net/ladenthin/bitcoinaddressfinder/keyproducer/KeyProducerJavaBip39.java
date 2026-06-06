// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.math.BigInteger;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.secret.RandomSecretSupplier;
import net.ladenthin.bitcoinaddressfinder.secret.SecretSupplier;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;

/**
 * Key producer that derives secrets deterministically from a BIP39 mnemonic.
 */
@ToString(callSuper = true)
public class KeyProducerJavaBip39 extends KeyProducerJava<CKeyProducerJavaBip39> {

    private final KeyUtility keyUtility;
    private final SecretSupplier randomSupplier;

    /**
     * Creates a new BIP39 key producer.
     *
     * @param cKeyProducerJavaBip39 the BIP39 configuration
     * @param keyUtility            cryptographic helper
     * @param bitHelper             bit/batch-size helper
     */
    public KeyProducerJavaBip39(
            CKeyProducerJavaBip39 cKeyProducerJavaBip39, KeyUtility keyUtility, BitHelper bitHelper) {
        super(cKeyProducerJavaBip39);
        this.keyUtility = keyUtility;

        BIP39KeyProducer bip39KeyProducer = new BIP39KeyProducer(
                cKeyProducerJavaBip39.mnemonic,
                cKeyProducerJavaBip39.passphrase,
                cKeyProducerJavaBip39.bip32Path,
                cKeyProducerJavaBip39.getCreationTimeInstant(),
                cKeyProducerJavaBip39.hardened);
        randomSupplier = new RandomSecretSupplier(bip39KeyProducer);
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);
        return keyUtility.createSecrets(
                overallWorkSize, returnStartSecretOnly, this.cKeyProducerJava.privateKeyMaxNumBits, randomSupplier);
    }

    @Override
    public void interrupt() {}
}
