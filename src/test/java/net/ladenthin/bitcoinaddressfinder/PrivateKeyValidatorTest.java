// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class PrivateKeyValidatorTest {

    private final PrivateKeyValidator validator = new PrivateKeyValidator();

    // <editor-fold defaultstate="collapsed" desc="getMaxPrivateKeyForBatchSize">
    @Test
    public void getMaxPrivateKeyForBatchSize_batchSize0_returnsMaxPrivateKey() {
        // arrange
        int batchSizeInBits = 0;

        // act
        BigInteger result = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_batchSize1_returnsMaxMinus1() {
        // arrange
        int batchSizeInBits = 1;

        // act
        BigInteger result = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        assertThat(result.add(BigInteger.ONE), is(equalTo(PublicKeyBytes.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_maxAllowedBitSize_returnsMinimumSafeKey() {
        // arrange
        int batchSizeInBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS - 1;

        // act
        BigInteger result = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        BigInteger offset = BigInteger.ONE.shiftLeft(batchSizeInBits);
        BigInteger expected = PublicKeyBytes.MAX_PRIVATE_KEY.subtract(offset).add(BigInteger.ONE);
        assertThat(result, is(equalTo(expected)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_bitSizeNegative_throwsException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // act
            validator.getMaxPrivateKeyForBatchSize(-1);
        });
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_bitSizeTooLarge_throwsException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // act
            validator.getMaxPrivateKeyForBatchSize(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS + 1);
        });
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_tooLarge_throwsException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            // arrange
            int batchSizeInBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
    
            // act
            validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);
        });
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isInvalidWithBatchSize">
    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#privateKeysTooLargeWithChunkSize")
    public void isInvalidWithBatchSize_keyTooLarge_returnsTrue(BigInteger privateKey, int batchSizeInBits) {
        // arrange
        BigInteger maxAllowed = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // act
        boolean isInvalid = validator.isInvalidWithBatchSize(privateKey, maxAllowed);

        // assert
        assertThat(isInvalid, is(true));
    }

    @Test
    public void isInvalidWithBatchSize_keyWithinLimit_returnsFalse() {
        // arrange
        int batchSizeInBits = 2;
        BigInteger maxAllowed = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);
        BigInteger validKey = maxAllowed.subtract(BigInteger.ONE);

        // act
        boolean isInvalid = validator.isInvalidWithBatchSize(validKey, maxAllowed);

        // assert
        assertThat(isInvalid, is(false));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isOutsidePrivateKeyRange">
    @Test
    public void isOutsidePrivateKeyRange_minPrivateKey_returnsTrue() {
        // act
        boolean result = validator.isOutsidePrivateKeyRange(PublicKeyBytes.MIN_PRIVATE_KEY);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void isOutsidePrivateKeyRange_minValidPrivateKey_returnsFalse() {
        // act
        boolean result = validator.isOutsidePrivateKeyRange(PublicKeyBytes.MIN_VALID_PRIVATE_KEY);

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void isOutsidePrivateKeyRange_maxPrivateKey_returnsFalse() {
        // act
        boolean result = validator.isOutsidePrivateKeyRange(PublicKeyBytes.MAX_PRIVATE_KEY);

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void isOutsidePrivateKeyRange_zero_returnsTrue() {
        // act
        boolean result = validator.isOutsidePrivateKeyRange(BigInteger.ZERO);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void isOutsidePrivateKeyRange_belowMin_returnsTrue() {
        // arrange
        BigInteger invalidKey = PublicKeyBytes.MIN_PRIVATE_KEY.subtract(BigInteger.ONE);

        // act
        boolean result = validator.isOutsidePrivateKeyRange(invalidKey);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void isOutsidePrivateKeyRange_aboveMax_returnsTrue() {
        // arrange
        BigInteger invalidKey = PublicKeyBytes.MAX_PRIVATE_KEY.add(BigInteger.ONE);

        // act
        boolean result = validator.isOutsidePrivateKeyRange(invalidKey);

        // assert
        assertThat(result, is(true));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="returnValidPrivateKey">
    @Test
    public void returnValidPrivateKey_validKey_returnsSameKey() {
        // arrange
        BigInteger valid = PublicKeyBytes.MIN_PRIVATE_KEY.add(BigInteger.ONE);

        // act
        BigInteger result = validator.returnValidPrivateKey(valid);

        // assert
        assertThat(result, is(equalTo(valid)));
    }

    @Test
    public void returnValidPrivateKey_tooSmall_returnsReplacement() {
        // arrange
        BigInteger tooSmall = PublicKeyBytes.MIN_PRIVATE_KEY.subtract(BigInteger.ONE);

        // act
        BigInteger result = validator.returnValidPrivateKey(tooSmall);

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }

    @Test
    public void returnValidPrivateKey_tooLarge_returnsReplacement() {
        // arrange
        BigInteger tooLarge = PublicKeyBytes.MAX_PRIVATE_KEY.add(BigInteger.ONE);

        // act
        BigInteger result = validator.returnValidPrivateKey(tooLarge);

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="replaceInvalidPrivateKeys">
    @Test
    public void replaceInvalidPrivateKeys_mixedArray_replacesInvalids() {
        // arrange
        BigInteger[] secrets = new BigInteger[]{
            PublicKeyBytes.MIN_VALID_PRIVATE_KEY,                      // valid
            PublicKeyBytes.MAX_PRIVATE_KEY.add(BigInteger.ONE),        // invalid
            BigInteger.ZERO                                            // invalid
        };

        // act
        validator.replaceInvalidPrivateKeys(secrets);

        // assert
        assertThat(secrets[0], is(equalTo(PublicKeyBytes.MIN_VALID_PRIVATE_KEY)));
        assertThat(secrets[1], is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
        assertThat(secrets[2], is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }

    @Test
    public void replaceInvalidPrivateKeys_allValidKeys_keepsOriginalValues() {
        // arrange
        BigInteger[] secrets = new BigInteger[]{
            PublicKeyBytes.MIN_VALID_PRIVATE_KEY,
            PublicKeyBytes.MAX_PRIVATE_KEY,
            PublicKeyBytes.MIN_VALID_PRIVATE_KEY.add(BigInteger.ONE)
        };
        BigInteger[] expectedCopy = secrets.clone();

        // act
        validator.replaceInvalidPrivateKeys(secrets);

        // assert
        assertThat(secrets, is(equalTo(expectedCopy)));
    }

    @Test
    public void replaceInvalidPrivateKeys_allInvalidKeys_replacesAll() {
        // arrange
        BigInteger[] secrets = new BigInteger[]{
            BigInteger.ZERO,
            BigInteger.ONE.negate(),
            PublicKeyBytes.MAX_PRIVATE_KEY.add(BigInteger.ONE)
        };

        // act
        validator.replaceInvalidPrivateKeys(secrets);

        // assert
        assertThat(secrets[0], is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
        assertThat(secrets[1], is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
        assertThat(secrets[2], is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }
    // </editor-fold>
}
