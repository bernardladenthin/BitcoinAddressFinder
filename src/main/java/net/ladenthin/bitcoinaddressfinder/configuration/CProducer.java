// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;

public class CProducer {
    
    /**
     * Lazy initialization. The configuration is changed on demand.
     */
    private BigInteger killBits;
    
    public static final int MAX_GRID_NUM_BITS = 24;
    
    /**
     * (2<sup>{@code maxNumBits}</sup> - 1) can be set to a lower value to improve a search on specific ranges (e.g. the puzzle transaction https://privatekeys.pw/puzzles/bitcoin-puzzle-tx ).
     * {@code 1} can't be tested because {@link ECKey#fromPrivate} throws an {@link IllegalArgumentException}.
     * Range: {@code 2} (inclusive) to {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BITS} (inclusive).
     */
    public int privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
    
    /**
     * Range: {@code 1} (inclusive) to {@link #MAX_GRID_NUM_BITS} (inclusive).
     */
    public int gridNumBits = 8;
    
    /**
     * Enable the log output for the secret address.
     */
    public boolean logSecretBase;

    public int getWorkSize() {
        return 1 << gridNumBits;
    }
    
    public BigInteger getKillBits() {
        if (killBits == null) {
            killBits = BigInteger.valueOf(2).pow(gridNumBits).subtract(BigInteger.ONE);
        }
        return killBits;
    }
    
    public BigInteger killBits(BigInteger bigInteger) {
        return bigInteger.andNot(getKillBits());
    }
    
    public void assertGridNumBitsCorrect() {
        {
            // ensure the constant MAX_GRID_NUM_BITS is set correct
            int maximumWorkSize = (int)(Integer.MAX_VALUE / PublicKeyBytes.TWO_COORDINATES_NUM_BYTES);
            // https://stackoverflow.com/questions/5242533/fast-way-to-find-exponent-of-nearest-superior-power-of-2
            int numBitsMaximum = maximumWorkSize == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(maximumWorkSize - 1);

            if (MAX_GRID_NUM_BITS > numBitsMaximum) {
                throw new IllegalArgumentException("MAX_GRID_NUM_BITS is too high for 32 bit memory allocation.");
            }
        }
        
        if (gridNumBits > MAX_GRID_NUM_BITS) {
            throw new IllegalArgumentException("gridNumBits must be lower or equal than " + MAX_GRID_NUM_BITS + ".");
        }
        
    }
    
}
