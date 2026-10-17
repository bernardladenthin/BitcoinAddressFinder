// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.math.BigInteger;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
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
     * @throws IllegalArgumentException if {@code privateKeyMaxNumBits} is not the full key size
     */
    public KeyProducerJavaBip39(
            CKeyProducerJavaBip39 cKeyProducerJavaBip39, KeyUtility keyUtility, BitHelper bitHelper) {
        super(requireFullKeySize(cKeyProducerJavaBip39));
        this.keyUtility = keyUtility;

        BIP39KeyProducer bip39KeyProducer = new BIP39KeyProducer(
                cKeyProducerJavaBip39.mnemonic,
                cKeyProducerJavaBip39.passphrase,
                cKeyProducerJavaBip39.bip32Path,
                cKeyProducerJavaBip39.getCreationTimeInstant(),
                cKeyProducerJavaBip39.hardened);
        randomSupplier = new RandomSecretSupplier(bip39KeyProducer);
    }

    /**
     * Rejects a {@code privateKeyMaxNumBits} below the full key size. The derived keys are full 256-bit
     * keys; fewer bits would hand out only their masked top bytes, which are no BIP39 key at all. Checked
     * before {@code super(...)} so no partially constructed producer exists when it throws.
     *
     * @param cKeyProducerJavaBip39 the BIP39 configuration
     * @return {@code cKeyProducerJavaBip39}, unchanged
     * @throws IllegalArgumentException if {@code privateKeyMaxNumBits} is not the full key size
     */
    private static CKeyProducerJavaBip39 requireFullKeySize(CKeyProducerJavaBip39 cKeyProducerJavaBip39) {
        if (cKeyProducerJavaBip39.privateKeyMaxNumBits != Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS) {
            throw new IllegalArgumentException("privateKeyMaxNumBits must be "
                    + Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS
                    + " for the BIP39 key producer (derived keys are not truncated), but was "
                    + cKeyProducerJavaBip39.privateKeyMaxNumBits);
        }
        return cKeyProducerJavaBip39;
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
