// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.BIP39KeyProducer;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.RandomSecretSupplier;
import net.ladenthin.bitcoinaddressfinder.SecretSupplier;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import org.slf4j.Logger;

/**
 * Key producer that derives secrets deterministically from a BIP39 mnemonic.
 */
public class KeyProducerJavaBip39 extends KeyProducerJava<CKeyProducerJavaBip39> {

    private final KeyUtility keyUtility;
    private final BitHelper bitHelper;
    private final SecretSupplier randomSupplier;
    private final BIP39KeyProducer bip39KeyProducer;

    /**
     * Creates a new BIP39 key producer.
     *
     * @param cKeyProducerJavaBip39 the BIP39 configuration
     * @param keyUtility            cryptographic helper
     * @param bitHelper             bit/batch-size helper
     * @param logger                SLF4J logger
     */
    public KeyProducerJavaBip39(
            CKeyProducerJavaBip39 cKeyProducerJavaBip39, KeyUtility keyUtility, BitHelper bitHelper, Logger logger) {
        super(cKeyProducerJavaBip39, logger);
        this.keyUtility = keyUtility;
        this.bitHelper = bitHelper;

        bip39KeyProducer = new BIP39KeyProducer(
                cKeyProducerJavaBip39.mnemonic,
                cKeyProducerJavaBip39.passphrase,
                cKeyProducerJavaBip39.bip32Path,
                cKeyProducerJavaBip39.getCreationTimeInstant(),
                cKeyProducerJavaBip39.hardened);
        randomSupplier = new RandomSecretSupplier(bip39KeyProducer);
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly)
            throws NoMoreSecretsAvailableException {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);
        return keyUtility.createSecrets(
                overallWorkSize, returnStartSecretOnly, this.cKeyProducerJava.privateKeyMaxNumBits, randomSupplier);
    }

    @Override
    public void interrupt() {}
}
