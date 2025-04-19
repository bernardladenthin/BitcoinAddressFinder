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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.Test;

public class PrivateKeyTooLargeExceptionTest {
    // <editor-fold defaultstate="collapsed" desc="PrivateKeyTooLargeException">
    @Test
    public void privateKeyTooLargeException_buildsCorrectMessage() {
        // arrange
        BigInteger providedKey = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16);
        BigInteger maxAllowedKey = PublicKeyBytes.MAX_PRIVATE_KEY.subtract(BigInteger.TWO);
        int batchSize = 10;

        // act
        PrivateKeyTooLargeException ex = new PrivateKeyTooLargeException(providedKey, maxAllowedKey, batchSize);

        // assert
        String message = ex.getMessage();
        assertThat(message, containsString("0x" + providedKey.toString(16)));
        assertThat(message, containsString("0x" + maxAllowedKey.toString(16)));
        assertThat(message, containsString("batchSizeInBits = " + batchSize));
        assertThat(message, containsString("PublicKeyBytes.MAX_PRIVATE_KEY"));
    }
    // </editor-fold>
}
