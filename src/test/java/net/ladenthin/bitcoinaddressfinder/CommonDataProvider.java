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
package net.ladenthin.bitcoinaddressfinder;

import com.tngtech.java.junit.dataprovider.DataProvider;
import java.math.BigInteger;
import java.util.Arrays;
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
    public final static String DATA_PROVIDER_LARGE_SECRETS_AS_HEX = "largeSecretsAsHex";

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
    @DataProvider
    public static Object[][] largeSecretsAsHex() {
        return new Object[][] {
            {"0000000000000000000000000000000000000000000000000000000000000000"},
            {"0000000000000000000000000000000000000000000000000000000000000001"},
            {"0000000000000000000000000000000000000000000000400000000000000000"},
            {"00000000000000000000000000000000000000000000007fffffffffffffffff"},
            {"2c7419465eaba472fd5ff50055a363e55936567a72995be2788aebb4ae74f3ff"},
            {"a6eaa2a8fa07686f3ef73736ea4668f5dbcc1f7c178b99afcacdadb64f0ce8bf"}, // must remain 64 hex chars; don't truncate/pad during conversion
            {PublicKeyBytes.MAX_PRIVATE_KEY_HEX.toLowerCase()},
        };
    }

    /**
     * For {@link #cSecretFormat()}.
     */
    public final static String DATA_PROVIDER_CSECRET_FORMAT = "cSecretFormat";

    @DataProvider
    public static Object[][] cSecretFormat() {
        return transformFlatToObjectArrayArray(CSecretFormat.values());
    }
    
    /**
     * For {@link EndiannessConverterTest}.
     */
    public static final String DATA_PROVIDER_ENDIANNESS = "endiannessScenarios";

    @DataProvider
    public static Object[][] endiannessScenarios() {
        return new Object[][] {
            {ByteOrder.LITTLE_ENDIAN, ByteOrder.LITTLE_ENDIAN, false},
            {ByteOrder.BIG_ENDIAN, ByteOrder.BIG_ENDIAN, false},
            {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN, true},
            {ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN, true},
        };
    }
    
    public static enum KeyProducerTypesLocal {
        KeyProducerJavaBip39,
        KeyProducerJavaIncremental,
        KeyProducerJavaRandom,
        KeyProducerJavaSocket,
        KeyProducerJavaWebSocket,
        KeyProducerJavaZmq;
    }
    
    /**
     * For {@link FinderTest}.
     */
    public static final String DATA_PROVIDER_KEY_PRODUCER_TYPES = "keyProducerTypes";

    @DataProvider
    public static Object[][] keyProducerTypes() {
        return Arrays.stream(KeyProducerTypesLocal.values())
            .map(type -> new Object[]{type})
            .toArray(Object[][]::new);
    }
    
    /**
    * For tests validating combinations of key producer types and bit sizes.
    */
   public static final String DATA_PROVIDER_JAVA_KEY_PRODUCER_AND_BIT_SIZE = "keyProducerTypeAndBitSize";

    @DataProvider
    public static Object[][] keyProducerTypeAndBitSize() {
        return mergeMany(
            keyProducerTypes(),    // e.g., Socket, ZMQ, etc.
            bitSizesAtMost24()     // e.g., 0–24
        );
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
    public final static String DATA_PROVIDER_BITS_TO_SIZE = "bitsToSize";

    @DataProvider
    public static Object[][] bitsToSize() {
        return new Object[][]{
            {0, 1},
            {1, 2},
            {2, 4},
            {3, 8},
            {8, 256},
        };
    }

    /**
     * For {@link #killBits()}.
     */
    public final static String DATA_PROVIDER_KILL_BITS = "killBits";

    @DataProvider
    public static Object[][] killBits() {
        return new Object[][]{
            {0, BigInteger.valueOf(0L)},
            {1, BigInteger.valueOf(1L)},
            {2, BigInteger.valueOf(3L)},
            {3, BigInteger.valueOf(7L)},
            {8, BigInteger.valueOf(255L)},
        };
    }

    /**
     * For {@link #bytesToMib()}.
     */
    public final static String DATA_PROVIDER_BYTES_TO_MIB = "bytesToMib";

    @DataProvider
    public static Object[][] bytesToMib() {
        return new Object[][]{
            {1L, 0.00000095367431640625d},
            {1024L * 1024L, 1.0d},
            {1024L * 1024L, 1.0d},
            {1024L * 1024L * 10, 10.0d},
        };
    }
    
    /**
     * For {@link #mibToBytes()}.
     */
    public final static String DATA_PROVIDER_MIB_TO_BYTES = "mibToBytes";

    @DataProvider
    public static Object[][] mibToBytes() {
        return new Object[][]{
            {1L, 1024L*1024L},
            {2L, 1024L*1024L *2L},
            {1024L, 1024L*1024L * 1024L},
        };
    }
    
    /**
     * For {@link #lmdbAmounts()}.
     */
    public final static String DATA_PROVIDER_LMDB_AMOUNTS = "lmdbAmounts";

    @DataProvider
    public static Object[][] lmdbAmounts() {
        long randomAmount = 13371337L;
        return new Object[][]{
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
    }

    /**
     * For {@link #lmdbIncreaseSize()}.
     */
    public final static String DATA_PROVIDER_LMDB_INCREASE_SIZE = "lmdbIncreaseSize";

    @DataProvider
    public static Object[][] lmdbIncreaseSize() {
        return new Object[][]{
            {1024L},
            {2048L},
            {4096L}
        };
    }

    /**
     * For {@link #bitSizesAtMost24()}.
     */
    public final static String DATA_PROVIDER_BIT_SIZES_AT_MOST_24 = "bitSizesAtMost24";

    @DataProvider
    public static Object[][] bitSizesAtMost24() {
        // if the constant was changed, the dataprovider and its test must be changed also
        // this constant can not change because of the maximum size in 32-bit systems
        if(PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY != 24) {
            throw new IllegalStateException("Adapt data provider for max chunks: " + PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY);
        }
        
        return new Object[][]{
            {0},
            {1},
            {2},
            {3},
            {4},
            {5},
            {6},
            {7},
            {8},
            {9},
            {10},
            {11},
            {12},
            {13},
            {14},
            {15},
            {16},
            {17},
            {18},
            {19},
            {20},
            {21},
            {22},
            {23},
            {PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY}
        };
    }
    
    /**
     * For {@link #compressedAndAmount()}.
     */
    public final static String DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT = "compressedAndStaticAmount";

    @DataProvider
    public static Object[][] compressedAndStaticAmount() {
        return new Object[][]{
            {true, true},
            {false, true},
            {true, false},
            {false, false}
        };
    }
    
    /**
     * For {@link #staticAmount()}.
     */
    public final static String DATA_PROVIDER_STATIC_AMOUNT = "staticAmount";

    @DataProvider
    public static Object[][] staticAmount() {
        return new Object[][]{
            {true},
            {false},
        };
    }
    
    /**
     * For {@link #compressed()}.
     */
    public final static String DATA_PROVIDER_COMPRESSED = "compressed";

    @DataProvider
    public static Object[][] compressed() {
        return new Object[][]{
            {true},
            {false},
        };
    }
    
    /**
     * For {@link #addressSeperator()}.
     */
    public final static String DATA_PROVIDER_ADDRESS_SEPARATOR = "addressSeperator";

    @DataProvider
    public static Object[][] addressSeperator() {
            return Arrays.stream(SeparatorFormat.values())
                 .map(format -> new Object[]{format.getSymbol()})
                 .toArray(Object[][]::new);
    }
    
    /**
     * For {@link #invalidP2WPKHAddressesValidBase58()}.
     */
    public final static String DATA_PROVIDER_INVALID_P2WPKH_ADDRESSES_VALID_BASE58 = "invalidP2WPKHAddressesValidBase58";

    @DataProvider
    public static Object[][] invalidP2WPKHAddressesValidBase58() {
        return new Object[][]{
            {"bc1zqyqsywvzqeeeeeee", "5347dec05b6f03de6cc004c1ec33000000000000"},  // bitcoin
            {"bc1zqyqsywvzqeeeeeeeee", "183a5c6b17b17eced6cd0b3e8443d6b300000000"},  // bitcoin
            {"bc1zqyqsywvzqeeeeeeeeeeeeee1", "b3d1231a68d6dbb47594deac07a0a9fe8352188e"},  // bitcoin
            {"vtc1zqyqsywvzqe", "51b9d9757bfedb535b3100000000000000000000"}, // vertcoin
            {"dgb1zqyqsywvzqe", "edaa0ede31d01c768b3100000000000000000000"}, // digibyte
        };
    }
    
    /**
     * For {@link #invalidBech32WitnessVersion2()}.
     */
    public final static String DATA_PROVIDER_INVALID_BECH32_WITNESS_VERSION_2 = "invalidBech32WitnessVersion2";

    @DataProvider
    public static Object[][] invalidBech32WitnessVersion2() {
        return new Object[][]{
            // Not sure where this address comes from, but it has a witness version of 2 and a witness program length of 2
            {"bc1zqyqsywvzqe"},  // bitcoin
        };
    }
    
    /**
     * For {@link #invalidBase58()}.
     */
    public final static String DATA_PROVIDER_INVALID_BASE58 = "invalidBase58";

    @DataProvider
    public static Object[][] invalidBase58() {
        return new Object[][]{
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
    }
    
    /**
     * For {@link #bitcoinAddressesCorrectBase58()}.
     * A correct base58 format should be parsed anyway.
     */
    public final static String DATA_PROVIDER_BITCOIN_ADDRESSES_CORRECT_BASE_58 = "bitcoinAddressesCorrectBase58";

    @DataProvider
    public static Object[][] bitcoinAddressesCorrectBase58() {
        return new Object[][]{
            {"1WrongAddressFormat","01667b78604490800f88b15c77a5000000000000"},
            {"1WrongAddressFormat2","5137f945cf88bd0384f82ef31b63000000000000"},
            {"1Wrong1Address2Format3","042b438790b52de2b8235712dbd6e2e400000000"},
        };
    }
    
    /**
     * For {@link #correctBase58()}.
     * A correct base58 format should be parsed anyway.
     */
    public final static String DATA_PROVIDER_CORRECT_BASE_58 = "correctBase58";

    @DataProvider
    public static Object[][] correctBase58() {
        return new Object[][]{
            {"1","0000000000000000000000000000000000000000"},
            {"15T","0102000000000000000000000000000000000000"},
            {"Ldp","0203000000000000000000000000000000000000"},
            {"7bWpTW","0203040500000000000000000000000000000000"},
            {"t3JZcvsuaXE6ygokL4XUiZSTrQBUoLyYfwu","0000000000000000000000000000000000000000"},
        };
    }
    
    /**
     * For {@link #srcPos()}.
     */
    public final static String DATA_PROVIDER_SRC_POS = "srcPos";

    @DataProvider
    public static Object[][] srcPos() {
        return new Object[][]{
            {1},
            {2},
            {3},
            {4},
            {5},
            {6},
            {7},
            {8},
            {9},
            {10},
            {11},
            {12},
            {13},
            {14},
            {15},
            {16},
            {17},
            {18},
            {19},
            {20},
        };
    }
    
    /**
     * For {@link #bitcoinCashAddressesChecksumInvalid()}.
     * TODO: I don't know if this is right. It seems like it's a base58 format.
     * I've asked Blockchair and they've answered: "The addresses you listed are for internal purposes.".
     */
    public final static String DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_CHECKSUM_INVALID = "bitcoinCashAddressesChecksumInvalid";

    @DataProvider
    public static Object[][] bitcoinCashAddressesChecksumInvalid() {
        return new Object[][]{
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
    }
    
    /**
     * For {@link #bitcoinCashAddressesInternalPurpose()}.
     * TODO: I don't know if this is right. It seems like it's a hex format.
     * I've asked Blockchair and they've answered: "The addresses you listed are for internal purposes.".
     */
    public final static String DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_INTERNAL_PURPOSE = "bitcoinCashAddressesInternalPurpose";

    @DataProvider
    public static Object[][] bitcoinCashAddressesInternalPurpose() {
        return new Object[][]{
            {"d-32551cbc0d16a34c5995b4057c3f027c"},
            {"d-29a0bd5b4cfbb05b493a11e0b69cedcc"},
            {"d-732cbc077831c75aba49f95eb629bc32"},
            {"d-f92fe84dd1620a12daea311393b37549"},
            {"d-ca0cf82e6bd2261f3a648a06090dc815"},
        };
    }
    
    /**
     * For {@link #createSecretBaseLogged()}.
     */
    public final static String DATA_PROVIDER_CREATE_SECRET_BASE_LOGGED = "createSecretBaseLogged";

    @DataProvider
    public static Object[][] createSecretBaseLogged() {
        return new Object[][]{
            // small key, batchSizeInBits: 2
            {"ABCDEF", 2, "0000000000000000000000000000000000000000000000000000000000abcdec", "secretBase: 0000000000000000000000000000000000000000000000000000000000abcdec/2", "secret BigInteger: 11259375", "secret as byte array: 0000000000000000000000000000000000000000000000000000000000abcdef", "killBits: 03", "secretBase: 11259372", "secretBase as byte array: 0000000000000000000000000000000000000000000000000000000000abcdec"},
            {"FEDCBA", 2, "0000000000000000000000000000000000000000000000000000000000fedcb8", "secretBase: 0000000000000000000000000000000000000000000000000000000000fedcb8/2", "secret BigInteger: 16702650", "secret as byte array: 0000000000000000000000000000000000000000000000000000000000fedcba", "killBits: 03", "secretBase: 16702648", "secretBase as byte array: 0000000000000000000000000000000000000000000000000000000000fedcb8"},
            // small key, batchSizeInBits: 21
            {"ABCDEF", 21, "0000000000000000000000000000000000000000000000000000000000a00000", "secretBase: 0000000000000000000000000000000000000000000000000000000000a00000/21", "secret BigInteger: 11259375", "secret as byte array: 0000000000000000000000000000000000000000000000000000000000abcdef", "killBits: 1fffff", "secretBase: 10485760", "secretBase as byte array: 0000000000000000000000000000000000000000000000000000000000a00000"},
            {"FEDCBA", 21, "0000000000000000000000000000000000000000000000000000000000e00000", "secretBase: 0000000000000000000000000000000000000000000000000000000000e00000/21", "secret BigInteger: 16702650", "secret as byte array: 0000000000000000000000000000000000000000000000000000000000fedcba", "killBits: 1fffff", "secretBase: 14680064", "secretBase as byte array: 0000000000000000000000000000000000000000000000000000000000e00000"},
            // large key, batchSizeInBits: 21
            {"123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", 21, "123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000", "secretBase: 123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000/21", "secret BigInteger: 8234104123542484900769178205574010627627573691361805720124810878238590820095", "secret as byte array: 123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", "killBits: 1fffff", "secretBase: 8234104123542484900769178205574010627627573691361805720124810878238588928000", "secretBase as byte array: 123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000"},
            // large key with odd number, batchSizeInBits: 21
            {"00d456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", 21, "00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000", "secretBase: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000/21", "secret BigInteger: 375168379408231402782670922269509069226925318059052594399906494889018056447", "secret as byte array: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", "killBits: 1fffff", "secretBase: 375168379408231402782670922269509069226925318059052594399906494889016164352", "secretBase as byte array: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000"},
            {PublicKeyBytes.MAX_PRIVATE_KEY_HEX.toLowerCase(), 2, "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "secretBase: fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140/2", "secret BigInteger: 115792089237316195423570985008687907852837564279074904382605163141518161494337", "secret as byte array: fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", "killBits: 03", "secretBase: 115792089237316195423570985008687907852837564279074904382605163141518161494336", "secretBase as byte array: fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140"},
        };
    }
    
    /**
     * For {@link #staticP2PKHAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_P2PKH_ADDRESSES = "staticP2PKHAddresses";

    @DataProvider
    public static Object[][] staticP2PKHAddresses() {
        return transformFlatToObjectArrayArray(P2PKH.values());
    }
    
    /**
     * For {@link #staticP2SHAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_P2SH_ADDRESSES = "staticP2SHAddresses";

    @DataProvider
    public static Object[][] staticP2SHAddresses() {
        return transformFlatToObjectArrayArray(P2SH.values());
    }
    
    /**
     * For {@link #staticP2SHAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_P2WPKH_ADDRESSES = "staticP2WPKHAddresses";

    @DataProvider
    public static Object[][] staticP2WPKHAddresses() {
        return transformFlatToObjectArrayArray(P2WPKH.values());
    }
    
    /**
     * For {@link #staticUnsupportedAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_UNSUPPORTED_ADDRESSES = "staticUnsupportedAddresses";

    @DataProvider
    public static Object[][] staticUnsupportedAddresses() {
        return transformFlatToObjectArrayArray(StaticUnsupportedAddress.values());
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
    public final static String DATA_PROVIDER_ALLOCATE_DIRECT = "allocateDirect";

    @DataProvider
    public static Object[][] allocateDirect() {
        return new Object[][]{
            {true},
            {false},
        };
    }
    
    /**
     * Data provider for testing with Bloom filter enabled and disabled.
     * <p>
     * Supplies {@code true} (Bloom filter active) and {@code false} (Bloom filter inactive),
     * to verify correctness and performance behavior in both configurations.
     */
    public final static String DATA_PROVIDER_BLOOM_FILTER_ENABLED = "bloomFilterEnabled";

    @DataProvider
    public static Object[][] bloomFilterEnabled() {
        return new Object[][]{
            {true},
            {false},
        };
    }
    
    /**
     * For {@link #largePrivateKeys()}.
     */
    public final static String DATA_PROVIDER_LARGE_PRIVATE_KEYS = "largePrivateKeys";

    @DataProvider
    public static Object[][] largePrivateKeys() {
        return new Object[][]{
            // ⚠️ Important: Do not include keys that are near or equal to the maximum valid private key (e.g., MAX_PRIVATE_KEY + offset).
            // Since we use grid-based key derivation (e.g., k + i), these values can overflow the valid secp256k1 range and cause failures.
            // {PublicKeyBytes.MAX_PRIVATE_KEY},
            //
            // Custom crafted BigIntegers with MSB set (highest bit in first byte = 1)
            // These will be encoded with a leading zero byte (i.e., total of 33 bytes)
            {new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8C00000000", 16)},
            {new BigInteger("F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0", 16)},
            {new BigInteger("F000000000000000000000000000000000000000000000000000000000000000", 16)},

            // Additional examples that force 33-byte encoding due to high bit in first byte
            {new BigInteger("F000000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger("8000000000000000000000000000000000000000000000000000000000000000", 16)}, // Only MSB set
            {new BigInteger("C000000000000000000000000000000000000000000000000000000000000000", 16)}, // First 2 bits set

            // Variants with slight length differences, still 256-bit aligned or close
            {new BigInteger("F000000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger( "F00000000000000000000000000000000000000000000000000000000000000", 16)},
            {new BigInteger(  "F0000000000000000000000000000000000000000000000000000000000000", 16)},
            
            //
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
            
            // Smallest values that still result in 33-byte encoding due to high bit
            {new BigInteger("8000000000000000", 16)},     // 64-bit with MSB set
            {new BigInteger("C000000000000000", 16)},     // 64-bit with top two bits set
            {new BigInteger("FF00000000000000", 16)},     // 64-bit with top byte fully set
            {new BigInteger("80000000000000000000000000000000", 16)}, // 128-bit with MSB set
            {new BigInteger("FF000000000000000000000000000000", 16)}, // 128-bit, top byte set
        };
    }
    
    /**
     * For {@link #privateKeysTooLargeWithChunkSize()}.
     */
    public final static String DATA_PROVIDER_PRIVATE_KEYS_TOO_LARGE_WITH_CHUNK_SIZE = "privateKeysTooLargeWithChunkSize";

    @DataProvider
    public static Object[][] privateKeysTooLargeWithChunkSize() {
        return new Object[][]{
            {PublicKeyBytes.MAX_TECHNICALLY_PRIVATE_KEY, PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY},
            {PublicKeyBytes.MAX_PRIVATE_KEY, PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY},
        };
    }
    
    /**
     * For {@link #largePrivate32ByteKeys()}.
     */
    public final static String DATA_PROVIDER_PRIVATE_KEYS_32_BYTE_REQUIRING_STRIP = "privateKeys32ByteRequiringStrip";

    @DataProvider
    public static Object[][] privateKeys32ByteRequiringStrip() {
        return new Object[][]{
            // Custom crafted BigIntegers with MSB set (highest bit in first byte = 1)
            // These will be encoded with a leading zero byte (i.e., total of 33 bytes)
            {new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8C00000000", 16)},
            {new BigInteger("F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0", 16)},
            {new BigInteger("F000000000000000000000000000000000000000000000000000000000000000", 16)},
        };
    }
    
    /**
     * For {@link bigIntegerVariants}.
     */
    public final static String DATA_PROVIDER_BIG_INTEGER_VARIANTS = "bigIntegerVariants";
    
    @DataProvider
    public static Object[][] bigIntegerVariants() {
        return new Object[][] {
            { new BigInteger("00", 16), 0, (byte) 0x00 }, // 0-value, empty result
            { new BigInteger("01", 16), 1, (byte) 0x01 },
            { new BigInteger("7F", 16), 1, (byte) 0x7F },
            { new BigInteger("FF", 16), 1, (byte) 0xFF }, // highest byte without sign extension
            { new BigInteger(1, new byte[]{0x00, 0x01}), 1, (byte) 0x01 }, // explicit leading zero
            { new BigInteger(1, new byte[]{0x00, (byte)0x80}), 1, (byte) 0x80 }, // zero removed, keep sign
            { new BigInteger(1, new byte[]{(byte) 0x00, (byte) 0xFF}), 1, (byte) 0xFF }, // zero removed
            { new BigInteger("FFFFFFFF", 16), 4, (byte) 0xFF },
            // Max technically private key (leading 0x00 byte expected)
            { PublicKeyBytes.MAX_TECHNICALLY_PRIVATE_KEY, 32, (byte) 0xFF }
        };
    }
}
