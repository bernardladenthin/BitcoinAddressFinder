// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.secret.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;

public class KeyProducerJavaBip39Test {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    String keyProducerId = "exampleId";

    private KeyProducerJavaBip39 createKeyProducerJavaBip39(CKeyProducerJavaBip39 config) {
        return new KeyProducerJavaBip39(config, keyUtility, bitHelper);
    }

    private BigInteger[] generateSecrets() throws NoMoreSecretsAvailableException {
        CKeyProducerJavaBip39 config = new CKeyProducerJavaBip39();
        config.keyProducerId = keyProducerId;
        config.privateKeyMaxNumBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS;
        config.mnemonic = CKeyProducerJavaBip39.DEFAULT_MNEMONIC;
        config.passphrase = "";
        config.creationTimeSeconds = 0L;
        config.hardened = false;

        KeyProducerJavaBip39 producer = createKeyProducerJavaBip39(config);
        return producer.createSecrets(bitHelper.convertBitsToSize(0), true);
    }

    /**
     * The derived BIP39 keys are full 256-bit keys. A lower {@code privateKeyMaxNumBits} (inherited from the
     * random producer's configuration) would hand out only the masked top bytes of each derived key -- values
     * that are no BIP39 key at all -- so the configuration is rejected instead.
     */
    @Test
    public void constructor_privateKeyMaxNumBitsBelowFullKeySize_throwsIllegalArgumentException() {
        CKeyProducerJavaBip39 config = new CKeyProducerJavaBip39();
        config.keyProducerId = keyProducerId;
        config.privateKeyMaxNumBits = 64;

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> createKeyProducerJavaBip39(config));
        assertThat(e.getMessage(), containsString("privateKeyMaxNumBits"));
    }

    @Test
    public void testBip39() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets();
        assertThat(result.length, is(equalTo(1)));
    }
}
