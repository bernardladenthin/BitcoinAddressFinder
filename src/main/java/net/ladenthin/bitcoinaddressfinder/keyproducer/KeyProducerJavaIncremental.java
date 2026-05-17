// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

public class KeyProducerJavaIncremental extends KeyProducerJava<CKeyProducerJavaIncremental> {
    
    @NonNull
    private BigInteger currentValue;
    
    public KeyProducerJavaIncremental(CKeyProducerJavaIncremental cKeyProducerJavaIncremental, KeyUtility keyUtility, BitHelper bitHelper, Logger logger) {
        super(cKeyProducerJavaIncremental, logger);
        this.currentValue = new BigInteger(cKeyProducerJavaIncremental.startAddress, BitHelper.RADIX_HEX);
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) throws NoMoreSecretsAvailableException {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);
        final BigInteger endAddress = cKeyProducerJava.getEndAddress();
        if (currentValue.compareTo(endAddress) > 0) {
            throw new NoMoreSecretsAvailableException(currentValue + " exceeds ");
        }
        
        int length = returnStartSecretOnly ? 1 : overallWorkSize;
        BigInteger[] secrets = new BigInteger[length];
        BigInteger counter = currentValue;
        for (int i = 0; i < length; i++) {
            if (counter.compareTo(endAddress) > 0) {
                throw new NoMoreSecretsAvailableException(counter + " exceeds end address " + endAddress);
            }
            secrets[i] = counter;
            counter = counter.add(BigInteger.ONE);
        }
        
        currentValue = currentValue.add(BigInteger.valueOf(overallWorkSize));
        
        return secrets;
    }

    @Override
    public void interrupt() {
    }
}
