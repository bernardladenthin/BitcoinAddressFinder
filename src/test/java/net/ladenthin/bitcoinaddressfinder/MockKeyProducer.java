// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import org.slf4j.Logger;

public class MockKeyProducer implements KeyProducer {

    private final KeyUtility keyUtility;
    private final Random random;
    private final int maximumBitLength;
    private final Logger mockLogger;

    MockKeyProducer(KeyUtility keyUtility, Random random, int maximumBitLength) {
        this.keyUtility = keyUtility;
        this.random = random;
        this.maximumBitLength = maximumBitLength;
        mockLogger = mock(Logger.class);
    }

    MockKeyProducer(KeyUtility keyUtility, Random random) {
        this(keyUtility, random, PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS);
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

    @Override
    public Logger getLogger() {
        return mockLogger;
    }
}
