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
import org.junit.jupiter.params.provider.ValueSource;

public class PrivateKeyValidatorTest {

    private final PrivateKeyValidator validator = new PrivateKeyValidator();

    // <editor-fold defaultstate="collapsed" desc="getMaxPrivateKeyForBatchSize">
    @Test
    public void getMaxPrivateKeyForBatchSize_batchSize0_returnsTheLastValidKey() {
        // arrange
        int batchSizeInBits = 0;

        // act
        BigInteger result = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        assertThat(result, is(equalTo(Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.ONE))));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_batchSize1_returnsMaxMinus2() {
        // arrange
        int batchSizeInBits = 1;

        // act
        BigInteger result = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        assertThat(result.add(BigInteger.TWO), is(equalTo(Secp256k1Constants.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_maxAllowedBitSize_returnsMinimumSafeKey() {
        // arrange
        int batchSizeInBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS - 1;

        // act
        BigInteger result = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        BigInteger offset = BigInteger.ONE.shiftLeft(batchSizeInBits);
        BigInteger expected = Secp256k1Constants.MAX_PRIVATE_KEY.subtract(offset);
        assertThat(result, is(equalTo(expected)));
    }

    /**
     * The last grid a base may start is the one whose last key is {@code n − 1}: every key of it is a
     * valid private key, and one more would be the group order {@code n}.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 8, 20, Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS - 1})
    public void getMaxPrivateKeyForBatchSize_gridFromTheMaximum_endsOnTheLastValidKey(int batchSizeInBits) {
        // arrange
        BigInteger maxBase = validator.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // act
        BigInteger lastKeyOfTheGrid =
                maxBase.add(BigInteger.ONE.shiftLeft(batchSizeInBits)).subtract(BigInteger.ONE);

        // assert
        assertThat(lastKeyOfTheGrid, is(equalTo(Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.ONE))));
        assertThat(validator.isOutsidePrivateKeyRange(lastKeyOfTheGrid), is(false));
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

    // <editor-fold defaultstate="collapsed" desc="isAlignedToBatchSize">
    /**
     * A grid's base must have its low {@code batchSizeInBits} bits clear: the OpenCL kernel derives a
     * work item's key as {@code base | offset} (an OR into the lowest word), which equals the
     * {@code base + offset} the host reports for a hit only for such a base.
     */
    @Test
    public void isAlignedToBatchSize_lowBitsClear_returnsTrue() {
        PrivateKeyValidator validator = new PrivateKeyValidator();

        assertThat(validator.isAlignedToBatchSize(BigInteger.ZERO, 8), is(true));
        assertThat(validator.isAlignedToBatchSize(BigInteger.valueOf(0x100), 8), is(true));
        assertThat(validator.isAlignedToBatchSize(BigInteger.valueOf(0x1_0000), 16), is(true));
        // the last aligned grid that stays below n with 8 bits: ...CD0364000
        assertThat(
                validator.isAlignedToBatchSize(
                        Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.valueOf(0x141)), 8),
                is(true));
    }

    @Test
    public void isAlignedToBatchSize_anyLowBitSet_returnsFalse() {
        PrivateKeyValidator validator = new PrivateKeyValidator();

        assertThat(validator.isAlignedToBatchSize(BigInteger.ONE, 8), is(false));
        assertThat(validator.isAlignedToBatchSize(BigInteger.valueOf(0x180), 8), is(false));
        assertThat(validator.isAlignedToBatchSize(BigInteger.valueOf(0x1_8000), 16), is(false));
        // n - 2^8 = ...CD0364041: the largest base the upper-bound check accepts, but not a grid base
        assertThat(
                validator.isAlignedToBatchSize(
                        Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.valueOf(0x100)), 8),
                is(false));
    }

    @Test
    public void isAlignedToBatchSize_batchSizeZero_everyBaseIsAligned() {
        PrivateKeyValidator validator = new PrivateKeyValidator();

        assertThat(validator.isAlignedToBatchSize(BigInteger.valueOf(0x12345), 0), is(true));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS + 1})
    public void isAlignedToBatchSize_batchSizeOutOfRange_throwsIllegalArgumentException(int batchSizeInBits) {
        PrivateKeyValidator validator = new PrivateKeyValidator();

        assertThrows(
                IllegalArgumentException.class, () -> validator.isAlignedToBatchSize(BigInteger.ZERO, batchSizeInBits));
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
    public void isOutsidePrivateKeyRange_keyBelowTheGroupOrder_returnsFalse() {
        // act
        boolean result =
                validator.isOutsidePrivateKeyRange(Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.ONE));

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void isOutsidePrivateKeyRange_groupOrder_returnsTrue() {
        // act
        boolean result = validator.isOutsidePrivateKeyRange(Secp256k1Constants.MAX_PRIVATE_KEY);

        // assert
        assertThat(result, is(true));
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
            Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.ONE),
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
