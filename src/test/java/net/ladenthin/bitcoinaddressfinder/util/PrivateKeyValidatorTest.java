// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import org.junit.jupiter.api.Test;
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
        assertThat(result, is(equalTo(Secp256k1Constants.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_batchSize1_returnsMaxMinus1() {
        // arrange
        int batchSizeInBits = 1;

        // act
        BigInteger result = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        assertThat(result.add(BigInteger.ONE), is(equalTo(Secp256k1Constants.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_maxAllowedBitSize_returnsMinimumSafeKey() {
        // arrange
        int batchSizeInBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS - 1;

        // act
        BigInteger result = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        BigInteger offset = BigInteger.ONE.shiftLeft(batchSizeInBits);
        BigInteger expected =
                Secp256k1Constants.MAX_PRIVATE_KEY.subtract(offset).add(BigInteger.ONE);
        assertThat(result, is(equalTo(expected)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_bitSizeNegative_throwsException() {
        // act
        assertThrows(IllegalArgumentException.class, () -> validator.getMaxPrivateKeyForBatchSize(-1));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_bitSizeTooLarge_throwsException() {
        // act
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.getMaxPrivateKeyForBatchSize(Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS + 1));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_tooLarge_throwsException() {
        // arrange
        int batchSizeInBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS;

        // act
        assertThrows(IllegalStateException.class, () -> validator.getMaxPrivateKeyForBatchSize(batchSizeInBits));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isInvalidWithBatchSize">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_PRIVATE_KEYS_TOO_LARGE_WITH_CHUNK_SIZE)
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
        boolean result = validator.isOutsidePrivateKeyRange(Secp256k1Constants.MIN_VALID_PRIVATE_KEY);

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void isOutsidePrivateKeyRange_maxPrivateKey_returnsFalse() {
        // act
        boolean result = validator.isOutsidePrivateKeyRange(Secp256k1Constants.MAX_PRIVATE_KEY);

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
        BigInteger invalidKey = Secp256k1Constants.MAX_PRIVATE_KEY.add(BigInteger.ONE);

        // act
        boolean result = validator.isOutsidePrivateKeyRange(invalidKey);

        // assert
        assertThat(result, is(true));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="coerceToValidPrivateKey">
    @Test
    public void coerceToValidPrivateKey_validKey_returnsSameKey() {
        // arrange
        BigInteger valid = PublicKeyBytes.MIN_PRIVATE_KEY.add(BigInteger.ONE);

        // act
        BigInteger result = validator.coerceToValidPrivateKey(valid);

        // assert
        assertThat(result, is(equalTo(valid)));
    }

    @Test
    public void coerceToValidPrivateKey_tooSmall_returnsReplacement() {
        // arrange
        BigInteger tooSmall = PublicKeyBytes.MIN_PRIVATE_KEY.subtract(BigInteger.ONE);

        // act
        BigInteger result = validator.coerceToValidPrivateKey(tooSmall);

        // assert
        assertThat(result, is(equalTo(Secp256k1Constants.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }

    @Test
    public void coerceToValidPrivateKey_tooLarge_returnsReplacement() {
        // arrange
        BigInteger tooLarge = Secp256k1Constants.MAX_PRIVATE_KEY.add(BigInteger.ONE);

        // act
        BigInteger result = validator.coerceToValidPrivateKey(tooLarge);

        // assert
        assertThat(result, is(equalTo(Secp256k1Constants.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="replaceInvalidPrivateKeys">
    @Test
    public void replaceInvalidPrivateKeys_mixedArray_replacesInvalids() {
        // arrange
        BigInteger[] secrets = new BigInteger[] {
            Secp256k1Constants.MIN_VALID_PRIVATE_KEY, // valid
            Secp256k1Constants.MAX_PRIVATE_KEY.add(BigInteger.ONE), // invalid
            BigInteger.ZERO // invalid
        };

        // act
        validator.replaceInvalidPrivateKeys(secrets);

        // assert
        assertThat(secrets[0], is(equalTo(Secp256k1Constants.MIN_VALID_PRIVATE_KEY)));
        assertThat(secrets[1], is(equalTo(Secp256k1Constants.INVALID_PRIVATE_KEY_REPLACEMENT)));
        assertThat(secrets[2], is(equalTo(Secp256k1Constants.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }

    @Test
    public void replaceInvalidPrivateKeys_allValidKeys_keepsOriginalValues() {
        // arrange
        BigInteger[] secrets = new BigInteger[] {
            Secp256k1Constants.MIN_VALID_PRIVATE_KEY,
            Secp256k1Constants.MAX_PRIVATE_KEY,
            Secp256k1Constants.MIN_VALID_PRIVATE_KEY.add(BigInteger.ONE)
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
        BigInteger[] secrets = new BigInteger[] {
            BigInteger.ZERO, BigInteger.ONE.negate(), Secp256k1Constants.MAX_PRIVATE_KEY.add(BigInteger.ONE)
        };

        // act
        validator.replaceInvalidPrivateKeys(secrets);

        // assert
        assertThat(secrets[0], is(equalTo(Secp256k1Constants.INVALID_PRIVATE_KEY_REPLACEMENT)));
        assertThat(secrets[1], is(equalTo(Secp256k1Constants.INVALID_PRIVATE_KEY_REPLACEMENT)));
        assertThat(secrets[2], is(equalTo(Secp256k1Constants.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }
    // </editor-fold>
}
