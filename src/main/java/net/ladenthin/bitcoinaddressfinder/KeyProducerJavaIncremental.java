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
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import org.jspecify.annotations.NonNull;

public class KeyProducerJavaIncremental extends KeyProducerJava<CKeyProducerJavaIncremental> {
    
    @NonNull
    private BigInteger currentValue;
    
    public KeyProducerJavaIncremental(CKeyProducerJavaIncremental cKeyProducerJavaIncremental, KeyUtility keyUtility, BitHelper bitHelper) {
        super(cKeyProducerJavaIncremental);
        this.currentValue = new BigInteger(cKeyProducerJavaIncremental.startAddress, BitHelper.RADIX_HEX);
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) throws NoMoreSecretsAvailableException {
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
}
