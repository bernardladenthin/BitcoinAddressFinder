// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PrivateKeyTooLargeException}.
 */
public class PrivateKeyTooLargeExceptionTest {

    private static final BigInteger PROVIDED_KEY =
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16);
    private static final BigInteger MAX_ALLOWED_KEY = Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.TWO);
    private static final int BATCH_SIZE_IN_BITS = 10;

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_validArguments_messageContainsProvidedKey() {
        // act
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // assert
        assertThat(exception.getMessage(), containsString("0x" + PROVIDED_KEY.toString(16)));
    }

    @Test
    public void constructor_validArguments_messageContainsMaxAllowedKey() {
        // act
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // assert
        assertThat(exception.getMessage(), containsString("0x" + MAX_ALLOWED_KEY.toString(16)));
    }

    @Test
    public void constructor_validArguments_messageContainsBatchSizeInBits() {
        // act
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // assert
        assertThat(exception.getMessage(), containsString("batchSizeInBits = " + BATCH_SIZE_IN_BITS));
    }

    @Test
    public void constructor_validArguments_messageContainsReference() {
        // act
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // assert
        assertThat(exception.getMessage(), containsString("Secp256k1Constants.MAX_PRIVATE_KEY"));
    }

    @Test
    public void constructor_validArguments_isInstanceOfIllegalArgumentException() {
        // act
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // assert
        assertThat(exception, is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void constructor_validArguments_noCause() {
        // act
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getProvidedKey">
    @Test
    public void getProvidedKey_validArguments_returnsProvidedKey() {
        // arrange
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // act
        BigInteger actual = exception.getProvidedKey();

        // assert
        assertThat(actual, is(equalTo(PROVIDED_KEY)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getMaxAllowedKey">
    @Test
    public void getMaxAllowedKey_validArguments_returnsMaxAllowedKey() {
        // arrange
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // act
        BigInteger actual = exception.getMaxAllowedKey();

        // assert
        assertThat(actual, is(equalTo(MAX_ALLOWED_KEY)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getBatchSizeInBits">
    @Test
    public void getBatchSizeInBits_validArguments_returnsBatchSizeInBits() {
        // arrange
        PrivateKeyTooLargeException exception =
                new PrivateKeyTooLargeException(PROVIDED_KEY, MAX_ALLOWED_KEY, BATCH_SIZE_IN_BITS);

        // act
        int actual = exception.getBatchSizeInBits();

        // assert
        assertThat(actual, is(equalTo(BATCH_SIZE_IN_BITS)));
    }
    // </editor-fold>
}
