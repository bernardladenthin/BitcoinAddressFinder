// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.*;

public class CommonDataProvider {
    
    /**
     * For {@link #cSecretFormat()}.
     */
    public final static String DATA_PROVIDER_LARGE_SECRETS_AS_HEX = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#largeSecretsAsHex";

    /**
    * Provides valid 64-character (32-byte) hex strings representing large unsigned secrets.
    * 
    * These values are used to verify correct conversion from hex to BigInteger and back to hex,
    * without losing leading zero bytes — a common issue with BigInteger.toByteArray().
    *
    * ⚠️ Important:
    * - Avoid using {@code new BigInteger(...).toByteArray()} directly, as it may introduce a leading sign byte (0x00)
    *   or drop leading zeros depending on the value.
    * - Instead, use {@code BigInteger.toString(16)} cautiously, or prefer utility methods like
    *   {@code keyUtility.bigIntegerToFixedLengthHex(...)} to ensure fixed-length 64-char hex encoding.
    *
    * These test cases help detect and avoid those pitfalls.
    */
    public static Stream<Arguments> largeSecretsAsHex() {
        Object[][] _data = new Object[][] {
            {"0000000000000000000000000000000000000000000000000000000000000000"},
            {"0000000000000000000000000000000000000000000000000000000000000001"},
            {"0000000000000000000000000000000000000000000000400000000000000000"},
            {"00000000000000000000000000000000000000000000007fffffffffffffffff"},
            {"2c7419465eaba472fd5ff50055a363e55936567a72995be2788aebb4ae74f3ff"},
            {"a6eaa2a8fa07686f3ef73736ea4668f5dbcc1f7c178b99afcacdadb64f0ce8bf"}, // must remain 64 hex chars; don't truncate/pad during conversion
            {PublicKeyBytes.MAX_PRIVATE_KEY_HEX.toLowerCase()},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }

    /**
     * For {@link #cSecretFormat()}.
     */
    public final static String DATA_PROVIDER_CSECRET_FORMAT = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#cSecretFormat";

    public static Stream<Arguments> cSecretFormat() {
        Object[][] _data = transformFlatToObjectArrayArray(CSecretFormat.values());
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link EndiannessConverterTest}.
     */
    public static final String DATA_PROVIDER_ENDIANNESS = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#endiannessScenarios";

    public static Stream<Arguments> endiannessScenarios() {
        Object[][] _data = new Object[][] {
            {ByteOrder.LITTLE_ENDIAN, ByteOrder.LITTLE_ENDIAN, false},
            {ByteOrder.BIG_ENDIAN, ByteOrder.BIG_ENDIAN, false},
            {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN, true},
            {ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN, true},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    public enum KeyProducerTypesLocal {
        KeyProducerJavaBip39,
        KeyProducerJavaIncremental,
        KeyProducerJavaRandom,
        KeyProducerJavaSocket,
        KeyProducerJavaWebSocket,
        KeyProducerJavaZmq
    }
    
    /**
     * For {@link FinderTest}.
     */
    public static final String DATA_PROVIDER_KEY_PRODUCER_TYPES = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#keyProducerTypes";

    public static Stream<Arguments> keyProducerTypes() {
        return java.util.Arrays.stream(keyProducerTypesData()).map(row -> Arguments.of(row));
    }

    private static Object[][] keyProducerTypesData() {
        return Arrays.stream(KeyProducerTypesLocal.values())
            .map(type -> new Object[]{type})
            .toArray(Object[][]::new);
    }

    /**
    * For tests validating combinations of key producer types and bit sizes.
    */
   public static final String DATA_PROVIDER_JAVA_KEY_PRODUCER_AND_BIT_SIZE = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#keyProducerTypeAndBitSize";

    public static Stream<Arguments> keyProducerTypeAndBitSize() {
        Object[][] _data = mergeMany(
            keyProducerTypesData(),    // e.g., Socket, ZMQ, etc.
            bitSizesAtMostMaxData()     // e.g., 0 – PublicKeyBytes#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY
        );
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }

    /**
     * Merges multiple Object[][] data providers into a cartesian product.
     * Each Object[][] must be a 2D array, where each row is a test case argument set.
     *
     * Example:
     * mergeMany(dp1, dp2, dp3) → returns all combinations of dp1 × dp2 × dp3
     */
    public static Object[][] mergeMany(Object[][]... providers) {
        List<Object[]> result = new ArrayList<>();
        mergeRecursive(providers, 0, new ArrayList<>(), result);
        return result.toArray(new Object[0][]);
    }

    private static void mergeRecursive(Object[][][] providers, int index, List<Object> current, List<Object[]> result) {
        if (index == providers.length) {
            result.add(current.toArray());
            return;
        }

        for (Object[] row : providers[index]) {
            List<Object> next = new ArrayList<>(current);
            Collections.addAll(next, row);
            mergeRecursive(providers, index + 1, next, result);
        }
    }

    /**
     * For {@link #bitsToSize()}.
     */
    public final static String DATA_PROVIDER_BITS_TO_SIZE = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bitsToSize";

    public static Stream<Arguments> bitsToSize() {
        Object[][] _data = new Object[][]{
            {0, 1},
            {1, 2},
            {2, 4},
            {3, 8},
            {8, 256},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }

    /**
     * For {@link #killBits()}.
     */
    public final static String DATA_PROVIDER_KILL_BITS = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#killBits";

    public static Stream<Arguments> killBits() {
        Object[][] _data = new Object[][]{
            {0, BigInteger.valueOf(0L)},
            {1, BigInteger.valueOf(1L)},
            {2, BigInteger.valueOf(3L)},
            {3, BigInteger.valueOf(7L)},
            {8, BigInteger.valueOf(255L)},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }

    /**
     * For {@link #bytesToMib()}.
     */
    public final static String DATA_PROVIDER_BYTES_TO_MIB = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bytesToMib";

    public static Stream<Arguments> bytesToMib() {
        Object[][] _data = new Object[][]{
            {1L, 0.00000095367431640625d},
            {1024L * 1024L, 1.0d},
            {1024L * 1024L, 1.0d},
            {1024L * 1024L * 10, 10.0d},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #mibToBytes()}.
     */
    public final static String DATA_PROVIDER_MIB_TO_BYTES = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#mibToBytes";

    public static Stream<Arguments> mibToBytes() {
        Object[][] _data = new Object[][]{
            {1L, 1024L*1024L},
            {2L, 1024L*1024L *2L},
            {1024L, 1024L*1024L * 1024L},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #lmdbAmounts()}.
     */
    public final static String DATA_PROVIDER_LMDB_AMOUNTS = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#lmdbAmounts";

    public static Stream<Arguments> lmdbAmounts() {
        long randomAmount = 13371337L;
        Object[][] _data = new Object[][]{
            // use static amount
            {true, -1337L, randomAmount, -1337L},
            {true, -7L,    randomAmount, -7L},
            {true, -6L,    randomAmount, -6L},
            {true, -5L,    randomAmount, -5L},
            {true, -4L,    randomAmount, -4L},
            {true, -3L,    randomAmount, -3L},
            {true, -2L,    randomAmount, -2L},
            {true, -1L,    randomAmount, -1L},
            {true, 0L,     randomAmount, 0L},
            {true, 1L,     randomAmount, 1L},
            {true, 2L,     randomAmount, 2L},
            {true, 3L,     randomAmount, 3L},
            {true, 4L,     randomAmount, 4L},
            {true, 5L,     randomAmount, 5L},
            {true, 6L,     randomAmount, 6L},
            {true, 7L,     randomAmount, 7L},
            {true, 1337L,  randomAmount, 1337L},
            // not use static amount
            {false, -1337L, randomAmount, randomAmount},
            {false, -7L,    randomAmount, randomAmount},
            {false, -6L,    randomAmount, randomAmount},
            {false, -5L,    randomAmount, randomAmount},
            {false, -4L,    randomAmount, randomAmount},
            {false, -3L,    randomAmount, randomAmount},
            {false, -2L,    randomAmount, randomAmount},
            {false, -1L,    randomAmount, randomAmount},
            {false, 0L,     randomAmount, randomAmount},
            {false, 1L,     randomAmount, randomAmount},
            {false, 2L,     randomAmount, randomAmount},
            {false, 3L,     randomAmount, randomAmount},
            {false, 4L,     randomAmount, randomAmount},
            {false, 5L,     randomAmount, randomAmount},
            {false, 6L,     randomAmount, randomAmount},
            {false, 7L,     randomAmount, randomAmount},
            {false, 1337L,  randomAmount, randomAmount},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }

    /**
     * For {@link #lmdbIncreaseSize()}.
     */
    public final static String DATA_PROVIDER_LMDB_INCREASE_SIZE = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#lmdbIncreaseSize";

    public static Stream<Arguments> lmdbIncreaseSize() {
        Object[][] _data = new Object[][]{
            {1024L},
            {2048L},
            {4096L}
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }

    /**
     * For {@link #bitSizesAtMostMax()}.
     */
    public final static String DATA_PROVIDER_BIT_SIZES_AT_MOST_MAX = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bitSizesAtMostMax";

    public static Stream<Arguments> bitSizesAtMostMax() {
        return java.util.Arrays.stream(bitSizesAtMostMaxData()).map(row -> Arguments.of(row));
    }

    private static Object[][] bitSizesAtMostMaxData() {
        final int max = PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY;

        Object[][] data = new Object[max + 1][1];
        for (int i = 0; i <= max; i++) {
            data[i][0] = i;
        }

        return data;
    }
    
    /**
     * For {@link #compressedAndStaticAmount()}.
     */
    public final static String DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#compressedAndStaticAmount";

    public static Stream<Arguments> compressedAndStaticAmount() {
        Object[][] _data = new Object[][]{
            {true, true},
            {false, true},
            {true, false},
            {false, false}
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #staticAmount()}.
     */
    public final static String DATA_PROVIDER_STATIC_AMOUNT = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#staticAmount";

    public static Stream<Arguments> staticAmount() {
        Object[][] _data = new Object[][]{
            {true},
            {false},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #compressed()}.
     */
    public final static String DATA_PROVIDER_COMPRESSED = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#compressed";

    public static Stream<Arguments> compressed() {
        Object[][] _data = new Object[][]{
            {true},
            {false},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #addressSeperator()}.
     */
    public final static String DATA_PROVIDER_ADDRESS_SEPARATOR = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#addressSeperator";

    public static Stream<Arguments> addressSeperator() {
            Object[][] _data = Arrays.stream(SeparatorFormat.values())
                 .map(format -> new Object[]{format.getSymbol()})
                 .toArray(Object[][]::new);
            return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #invalidP2WPKHAddressesValidBase58()}.
     */
    public final static String DATA_PROVIDER_INVALID_P2WPKH_ADDRESSES_VALID_BASE58 = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#invalidP2WPKHAddressesValidBase58";

    public static Stream<Arguments> invalidP2WPKHAddressesValidBase58() {
        Object[][] _data = new Object[][]{
            {"bc1zqyqsywvzqeeeeeee", "5347dec05b6f03de6cc004c1ec33000000000000"},  // bitcoin
            {"bc1zqyqsywvzqeeeeeeeee", "183a5c6b17b17eced6cd0b3e8443d6b300000000"},  // bitcoin
            {"bc1zqyqsywvzqeeeeeeeeeeeeee1", "b3d1231a68d6dbb47594deac07a0a9fe8352188e"},  // bitcoin
            {"vtc1zqyqsywvzqe", "51b9d9757bfedb535b3100000000000000000000"}, // vertcoin
            {"dgb1zqyqsywvzqe", "edaa0ede31d01c768b3100000000000000000000"}, // digibyte
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #invalidBech32WitnessVersion2()}.
     */
    public final static String DATA_PROVIDER_INVALID_BECH32_WITNESS_VERSION_2 = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#invalidBech32WitnessVersion2";

    public static Stream<Arguments> invalidBech32WitnessVersion2() {
        Object[][] _data = new Object[][]{
            // Not sure where this address comes from, but it has a witness version of 2 and a witness program length of 2
            {"bc1zqyqsywvzqe"},  // bitcoin
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #invalidBase58()}.
     */
    public final static String DATA_PROVIDER_INVALID_BASE58 = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#invalidBase58";

    public static Stream<Arguments> invalidBase58() {
        Object[][] _data = new Object[][]{
            // P2PKH
            {"1Wr0ngAddressFormat"},
            {"1WrongAddressFormat0"},
            {"1WrongIAddressFormat"},
            {"1WronglAddressFormat"},
            // P2WPKH
            // l (small L) is appended and not a valid base58 char
            {"bc1zqyqsywvzqel"},  // bitcoin
            {"vtc1zqyqsywvzqel"}, // vertcoin
            {"dgb1zqyqsywvzqel"}, // digibyte
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #bitcoinAddressesCorrectBase58()}.
     * A correct base58 format should be parsed anyway.
     */
    public final static String DATA_PROVIDER_BITCOIN_ADDRESSES_CORRECT_BASE_58 = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bitcoinAddressesCorrectBase58";

    public static Stream<Arguments> bitcoinAddressesCorrectBase58() {
        Object[][] _data = new Object[][]{
            {"1WrongAddressFormat","01667b78604490800f88b15c77a5000000000000"},
            {"1WrongAddressFormat2","5137f945cf88bd0384f82ef31b63000000000000"},
            {"1Wrong1Address2Format3","042b438790b52de2b8235712dbd6e2e400000000"},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #correctBase58()}.
     * A correct base58 format should be parsed anyway.
     */
    public final static String DATA_PROVIDER_CORRECT_BASE_58 = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#correctBase58";

    public static Stream<Arguments> correctBase58() {
        Object[][] _data = new Object[][]{
            {"1","0000000000000000000000000000000000000000"},
            {"15T","0102000000000000000000000000000000000000"},
            {"Ldp","0203000000000000000000000000000000000000"},
            {"7bWpTW","0203040500000000000000000000000000000000"},
            {"t3JZcvsuaXE6ygokL4XUiZSTrQBUoLyYfwu","0000000000000000000000000000000000000000"},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #srcPos()}.
     */
    public final static String DATA_PROVIDER_SRC_POS = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#srcPos";

    public static Stream<Arguments> srcPos() {
        Object[][] _data = new Object[][]{
            {1},{2},{3},{4},{5},{6},{7},{8},{9},{10},
            {11},{12},{13},{14},{15},{16},{17},{18},{19},{20},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #bitcoinCashAddressesChecksumInvalid()}.
     */
    public final static String DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_CHECKSUM_INVALID = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bitcoinCashAddressesChecksumInvalid";

    public static Stream<Arguments> bitcoinCashAddressesChecksumInvalid() {
        Object[][] _data = new Object[][]{
            {"dSn3treXZQfJRktvoApJKM","27225957c54a53b10e4f4e00b2562af400000000"},
            {"bq2ZTwe8pt3hyCuy5MudVG","1a0b606a1f1de7a130aae81dc66c665700000000"},
            {"W6xvVjvtRobnz9dDdjEugV","ae340f03861fd08558312b97e7926a0000000000"},
            {"N1JULkP6RqW3LcbpWvgryV","1aadd1f5457c0f8c08763e55745ff80000000000"},
            {"8cE2K6rzN1dVjQXfX3SFcZ","9b072e05be8ff2d1a26e73c694644e0000000000"},
            {"kGuKp2vSFzNQ5SJftineWu","5e715d6cc9f93ed922579fd06c47017200000000"},
            {"5QmydJKnwKPJb8m1zEQpUV","b661051dd8b1893dd2c10fae9c00de0000000000"},
            {"FyXF2p5s8qbonKTuB4MWFr","443ac752c9c5e5445abfe6898dad010000000000"},
            {"rWMr7gq4bDKs3T1TgWpTrg","90e9360283cee7ba62e81fe2ef67dcf100000000"},
            {"d6qya271t3cYzW8qEEui4E","2459e48a008f3c73c00f9f6e9b24ff0f00000000"},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #bitcoinCashAddressesInternalPurpose()}.
     */
    public final static String DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_INTERNAL_PURPOSE = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bitcoinCashAddressesInternalPurpose";

    public static Stream<Arguments> bitcoinCashAddressesInternalPurpose() {
        Object[][] _data = new Object[][]{
            {"d-32551cbc0d16a34c5995b4057c3f027c"},
            {"d-29a0bd5b4cfbb05b493a11e0b69cedcc"},
            {"d-732cbc077831c75aba49f95eb629bc32"},
            {"d-f92fe84dd1620a12daea311393b37549"},
            {"d-ca0cf82e6bd2261f3a648a06090dc815"},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #createSecretBaseLogged()}.
     */
    public final static String DATA_PROVIDER_CREATE_SECRET_BASE_LOGGED = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#createSecretBaseLogged";

    public static Stream<Arguments> createSecretBaseLogged() {
        Object[][] _data = new Object[][]{
            {"ABCDEF", 2, "0000000000000000000000000000000000000000000000000000000000abcdec", "secretBase: 0000000000000000000000000000000000000000000000000000000000abcdec/2", "secret BigInteger: 11259375", "secret as byte array: 0000000000000000000000000000000000000000000000000000000000abcdef", "killBits: 03", "secretBase: 11259372", "secretBase as byte array: 0000000000000000000000000000000000000000000000000000000000abcdec"},
            {"FEDCBA", 2, "0000000000000000000000000000000000000000000000000000000000fedcb8", "secretBase: 0000000000000000000000000000000000000000000000000000000000fedcb8/2", "secret BigInteger: 16702650", "secret as byte array: 0000000000000000000000000000000000000000000000000000000000fedcba", "killBits: 03", "secretBase: 16702648", "secretBase as byte array: 0000000000000000000000000000000000000000000000000000000000fedcb8"},
            {"ABCDEF", 21, "0000000000000000000000000000000000000000000000000000000000a00000", "secretBase: 0000000000000000000000000000000000000000000000000000000000a00000/21", "secret BigInteger: 11259375", "secret as byte array: 0000000000000000000000000000000000000000000000000000000000abcdef", "killBits: 1fffff", "secretBase: 10485760", "secretBase as byte array: 0000000000000000000000000000000000000000000000000000000000a00000"},
            {"FEDCBA", 21, "0000000000000000000000000000000000000000000000000000000000e00000", "secretBase: 0000000000000000000000000000000000000000000000000000000000e00000/21", "secret BigInteger: 16702650", "secret as byte array: 0000000000000000000000000000000000000000000000000000000000fedcba", "killBits: 1fffff", "secretBase: 14680064", "secretBase as byte array: 0000000000000000000000000000000000000000000000000000000000e00000"},
            {"123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", 21, "123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000", "secretBase: 123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000/21", "secret BigInteger: 8234104123542484900769178205574010627627573691361805720124810878238590820095", "secret as byte array: 123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", "killBits: 1fffff", "secretBase: 8234104123542484900769178205574010627627573691361805720124810878238588928000", "secretBase as byte array: 123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000"},
            {"00d456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", 21, "00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000", "secretBase: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000/21", "secret BigInteger: 375168379408231402782670922269509069226925318059052594399906494889018056447", "secret as byte array: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", "killBits: 1fffff", "secretBase: 375168379408231402782670922269509069226925318059052594399906494889016164352", "secretBase as byte array: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000"},
            {PublicKeyBytes.MAX_PRIVATE_KEY_HEX.toLowerCase(), 2, "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "secretBase: fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140/2", "secret BigInteger: 115792089237316195423570985008687907852837564279074904382605163141518161494337", "secret as byte array: fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", "killBits: 03", "secretBase: 115792089237316195423570985008687907852837564279074904382605163141518161494336", "secretBase as byte array: fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140"},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #staticP2PKHAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_P2PKH_ADDRESSES = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#staticP2PKHAddresses";

    public static Stream<Arguments> staticP2PKHAddresses() {
        Object[][] _data = transformFlatToObjectArrayArray(P2PKH.values());
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #staticP2SHAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_P2SH_ADDRESSES = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#staticP2SHAddresses";

    public static Stream<Arguments> staticP2SHAddresses() {
        Object[][] _data = transformFlatToObjectArrayArray(P2SH.values());
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #staticP2SHAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_P2WPKH_ADDRESSES = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#staticP2WPKHAddresses";

    public static Stream<Arguments> staticP2WPKHAddresses() {
        Object[][] _data = transformFlatToObjectArrayArray(P2WPKH.values());
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #staticUnsupportedAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_UNSUPPORTED_ADDRESSES = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#staticUnsupportedAddresses";

    public static Stream<Arguments> staticUnsupportedAddresses() {
        Object[][] _data = transformFlatToObjectArrayArray(StaticUnsupportedAddress.values());
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    private static Object[][] transformFlatToObjectArrayArray(Object[] object) {
        Object[][] objectArray = new Object[object.length][1];
        for (int i = 0; i < objectArray.length; i++) {
            objectArray[i][0] = object[i];
        }
        return objectArray;
    }
    
    /**
     * For {@link ByteBufferUtility}.
     * Use allocate direct.
     */
    public final static String DATA_PROVIDER_ALLOCATE_DIRECT = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#allocateDirect";

    public static Stream<Arguments> allocateDirect() {
        Object[][] _data = new Object[][]{
            {true},
            {false},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * Data provider for testing with Bloom filter enabled and disabled.
     */
    public final static String DATA_PROVIDER_BLOOM_FILTER_ENABLED = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bloomFilterEnabled";

    public static Stream<Arguments> bloomFilterEnabled() {
        Object[][] _data = new Object[][]{
            {true},
            {false},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #largePrivateKeys()}.
     */
    public final static String DATA_PROVIDER_LARGE_PRIVATE_KEYS = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#largePrivateKeys";

    public static Stream<Arguments> largePrivateKeys() {
        Object[][] _data = new Object[][]{
            {new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8C00000000", 16)},
            {new BigInteger("F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0", 16)},
            {new BigInteger("F000000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger("F000000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger("8000000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger("C000000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger("F000000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger( "F00000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(  "F0000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger("1000000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger( "100000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(  "10000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(   "1000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(    "100000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(     "10000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(      "1000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(       "100000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(        "10000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(         "1000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(          "100000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(           "10000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(            "1000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(             "100000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(              "10000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(               "1000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(                "100000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(                 "10000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(                  "1000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(                   "100000000000000000000000000000000000000000000", 16)},
            {new BigInteger(                    "10000000000000000000000000000000000000000000", 16)},
            {new BigInteger(                     "1000000000000000000000000000000000000000000", 16)},
            {new BigInteger(                      "100000000000000000000000000000000000000000", 16)},
            {new BigInteger(                       "10000000000000000000000000000000000000000", 16)},
            {new BigInteger(                        "1000000000000000000000000000000000000000", 16)},
            {new BigInteger(                         "100000000000000000000000000000000000000", 16)},
            {new BigInteger(                          "10000000000000000000000000000000000000", 16)},
            {new BigInteger(                           "1000000000000000000000000000000000000", 16)},
            {new BigInteger(                            "100000000000000000000000000000000000", 16)},
            {new BigInteger(                             "10000000000000000000000000000000000", 16)},
            {new BigInteger(                              "1000000000000000000000000000000000", 16)},
            {new BigInteger(                               "100000000000000000000000000000000", 16)},
            {new BigInteger(                                "10000000000000000000000000000000", 16)},
            {new BigInteger(                                 "1000000000000000000000000000000", 16)},
            {new BigInteger(                                  "100000000000000000000000000000", 16)},
            {new BigInteger(                                   "10000000000000000000000000000", 16)},
            {new BigInteger(                                    "1000000000000000000000000000", 16)},
            {new BigInteger(                                     "100000000000000000000000000", 16)},
            {new BigInteger(                                      "10000000000000000000000000", 16)},
            {new BigInteger(                                       "1000000000000000000000000", 16)},
            {new BigInteger(                                        "100000000000000000000000", 16)},
            {new BigInteger(                                         "10000000000000000000000", 16)},
            {new BigInteger(                                          "1000000000000000000000", 16)},
            {new BigInteger(                                           "100000000000000000000", 16)},
            {new BigInteger(                                            "10000000000000000000", 16)},
            {new BigInteger(                                             "1000000000000000000", 16)},
            {new BigInteger(                                              "100000000000000000", 16)},
            {new BigInteger(                                               "10000000000000000", 16)},
            {new BigInteger(                                                "1000000000000000", 16)},
            {new BigInteger(                                                 "100000000000000", 16)},
            {new BigInteger(                                                  "10000000000000", 16)},
            {new BigInteger(                                                   "1000000000000", 16)},
            {new BigInteger(                                                    "100000000000", 16)},
            {new BigInteger(                                                     "10000000000", 16)},
            {new BigInteger(                                                      "1000000000", 16)},
            {new BigInteger(                                                       "100000000", 16)},
            {new BigInteger(                                                        "10000000", 16)},
            {new BigInteger("8000000000000000", 16)},
            {new BigInteger("C000000000000000", 16)},
            {new BigInteger("FF00000000000000", 16)},
            {new BigInteger("80000000000000000000000000000000", 16)},
            {new BigInteger("FF000000000000000000000000000000", 16)},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #privateKeysTooLargeWithChunkSize()}.
     */
    public final static String DATA_PROVIDER_PRIVATE_KEYS_TOO_LARGE_WITH_CHUNK_SIZE = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#privateKeysTooLargeWithChunkSize";

    public static Stream<Arguments> privateKeysTooLargeWithChunkSize() {
        Object[][] _data = new Object[][]{
            {PublicKeyBytes.MAX_TECHNICALLY_PRIVATE_KEY, PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY},
            {PublicKeyBytes.MAX_PRIVATE_KEY, PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #privateKeys32ByteRequiringStrip()}.
     */
    public final static String DATA_PROVIDER_PRIVATE_KEYS_32_BYTE_REQUIRING_STRIP = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#privateKeys32ByteRequiringStrip";

    public static Stream<Arguments> privateKeys32ByteRequiringStrip() {
        Object[][] _data = new Object[][]{
            {new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8C00000000", 16)},
            {new BigInteger("F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0", 16)},
            {new BigInteger("F000000000000000000000000000000000000000000000000000000000000000", 16)},
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
    
    /**
     * For {@link #bigIntegerVariants()}.
     */
    public final static String DATA_PROVIDER_BIG_INTEGER_VARIANTS = "net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bigIntegerVariants";
    
    public static Stream<Arguments> bigIntegerVariants() {
        Object[][] _data = new Object[][] {
            { new BigInteger("00", 16), 0, (byte) 0x00 },
            { new BigInteger("01", 16), 1, (byte) 0x01 },
            { new BigInteger("7F", 16), 1, (byte) 0x7F },
            { new BigInteger("FF", 16), 1, (byte) 0xFF },
            { new BigInteger(1, new byte[]{0x00, 0x01}), 1, (byte) 0x01 },
            { new BigInteger(1, new byte[]{0x00, (byte)0x80}), 1, (byte) 0x80 },
            { new BigInteger(1, new byte[]{(byte) 0x00, (byte) 0xFF}), 1, (byte) 0xFF },
            { new BigInteger("FFFFFFFF", 16), 4, (byte) 0xFF },
            { PublicKeyBytes.MAX_TECHNICALLY_PRIVATE_KEY, 32, (byte) 0xFF }
        };
        return java.util.Arrays.stream(_data).map(row -> Arguments.of(row));
    }
}
