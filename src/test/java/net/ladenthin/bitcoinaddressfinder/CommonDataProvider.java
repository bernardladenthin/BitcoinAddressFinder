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
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticP2PKHAddress;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticP2SHAddress;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticUnsupportedAddress;

public class CommonDataProvider {

    /**
     * For {@link #cSecretFormat()}.
     */
    public final static String DATA_PROVIDER_CSECRET_FORMAT = "cSecretFormat";

    @DataProvider
    public static Object[][] cSecretFormat() {
        return transformFlatToObjectArrayArray(CSecretFormat.values());
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
     * For {@link #bitSizesLowerThan25()}.
     */
    public final static String DATA_PROVIDER_BIT_SIZES_LOWER_THAN_25 = "bitSizesLowerThan25";

    @DataProvider
    public static Object[][] bitSizesLowerThan25() {
        // if the constant was changed, the dataprovider and its test must be changed also
        // this constant can not change because of the maximum size in 32-bit systems
        if(PublicKeyBytes.BIT_COUNT_FOR_MAX_COORDINATE_PAIRS_ARRAY != 24) {
            throw new IllegalStateException("Adapt data provider.");
        }
        
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
            {21},
            {22},
            {23},
            {PublicKeyBytes.BIT_COUNT_FOR_MAX_COORDINATE_PAIRS_ARRAY}
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
        return new Object[][]{
            {AddressTxtLine.COMMA},
            {AddressTxtLine.SEMICOLON},
            {AddressTxtLine.TAB_SPLIT}
        };
    }
    
    /**
     * For {@link #bitcoinInvalidP2WPKHAddresses()}.
     */
    public final static String DATA_PROVIDER_BITCOIN_INVALID_P2WPKH_ADDRESSES = "bitcoinInvalidP2WPKHAddresses";

    @DataProvider
    public static Object[][] bitcoinInvalidP2WPKHAddresses() {
        return new Object[][]{
            {"bc1pmfr3p9j00pfxjh0zmgp99y8zftmd3s5pmedqhyptwy6lm87hf5ss52r5n8"},
            {"bc1pqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqs3wf0qm"},
            {"bc1zqyqsywvzqe"},
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
     * For {@link #bitcoinAddressesInvalidBase58()}.
     * A invalid base58 format can't be read.
     */
    public final static String DATA_PROVIDER_BITCOIN_ADDRESSES_INVALID_BASE_58 = "bitcoinAddressesInvalidBase58";

    @DataProvider
    public static Object[][] bitcoinAddressesInvalidBase58() {
        return new Object[][]{
            {"1Wr0ngAddressFormat"},
            {"1WrongAddressFormat0"},
            {"1WrongIAddressFormat"},
            {"1WronglAddressFormat"},
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
            // small key, 2 bit num grid
            {"ABCDEF", 2, "abcdec", "secretBase: abcdec/2", "secret BigInteger: -5517841", "secret as byte array: abcdef", "killBits: 03", "secretBase: -5517844", "secretBase as byte array: abcdec"},
            {"FEDCBA", 2, "fedcb8", "secretBase: fedcb8/2", "secret BigInteger: -74566", "secret as byte array: fedcba", "killBits: 03", "secretBase: -74568", "secretBase as byte array: fedcb8"},
            // small key, 21 bit num grid
            {"ABCDEF", 21, "a00000", "secretBase: a00000/21", "secret BigInteger: -5517841", "secret as byte array: abcdef", "killBits: 1fffff", "secretBase: -6291456", "secretBase as byte array: a00000"},
            {"FEDCBA", 21, "e00000", "secretBase: e00000/21", "secret BigInteger: -74566", "secret as byte array: fedcba", "killBits: 1fffff", "secretBase: -2097152", "secretBase as byte array: e00000"},
            // large key, 21 bit num grid
            {"123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", 21, "123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000", "secretBase: 123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000/21", "secret BigInteger: 8234104123542484900769178205574010627627573691361805720124810878238590820095", "secret as byte array: 123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", "killBits: 1fffff", "secretBase: 8234104123542484900769178205574010627627573691361805720124810878238588928000", "secretBase as byte array: 123456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000"},
            // large key with odd number, 21 bit num grid
            {"00d456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", 21, "00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000", "secretBase: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000/21", "secret BigInteger: 375168379408231402782670922269509069226925318059052594399906494889018056447", "secret as byte array: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeff", "killBits: 1fffff", "secretBase: 375168379408231402782670922269509069226925318059052594399906494889016164352", "secretBase as byte array: 00d456789abcdef0123456789abcdef0123456789abcdef0123456789aa00000"},
        };
    }
    
    /**
     * For {@link #staticP2PKHAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_P2PKH_ADDRESSES = "staticP2PKHAddresses";

    @DataProvider
    public static Object[][] staticP2PKHAddresses() {
        return transformFlatToObjectArrayArray(StaticP2PKHAddress.values());
    }
    
    /**
     * For {@link #staticP2SHAddresses()}.
     */
    public final static String DATA_PROVIDER_STATIC_P2SH_ADDRESSES = "staticP2SHAddresses";

    @DataProvider
    public static Object[][] staticP2SHAddresses() {
        return transformFlatToObjectArrayArray(StaticP2SHAddress.values());
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
}
