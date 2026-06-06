// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.secret.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;

public class MockKeyProducer implements KeyProducer {

    private final KeyUtility keyUtility;
    private final Random random;
    private final int maximumBitLength;

    public MockKeyProducer(KeyUtility keyUtility, Random random, int maximumBitLength) {
        this.keyUtility = keyUtility;
        this.random = random;
        this.maximumBitLength = maximumBitLength;
    }

    public MockKeyProducer(KeyUtility keyUtility, Random random) {
        this(keyUtility, random, Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS);
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly)
            throws NoMoreSecretsAvailableException {
        int length = returnStartSecretOnly ? 1 : overallWorkSize;
        BigInteger[] secrets = new BigInteger[length];
        for (int i = 0; i < secrets.length; i++) {
            secrets[i] = keyUtility.createSecret(maximumBitLength, random);
        }
        return secrets;
    }

    @Override
    public void interrupt() {}
}
