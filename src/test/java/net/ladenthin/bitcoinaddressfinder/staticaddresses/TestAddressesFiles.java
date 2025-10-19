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
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.SeparatorFormat;
import static net.ladenthin.bitcoinaddressfinder.SeparatorFormat.COMMA;
import static net.ladenthin.bitcoinaddressfinder.SeparatorFormat.SEMICOLON;
import static net.ladenthin.bitcoinaddressfinder.SeparatorFormat.TAB_SPLIT;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2WPKH;
import org.bitcoinj.base.Coin;
import org.junit.rules.TemporaryFolder;

public class TestAddressesFiles implements AddressesFiles {

    public final static Set<String> compressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount = new HashSet<>();
    public final static Set<String> uncompressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount = new HashSet<>();
    public final static Set<String> compressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount = new HashSet<>();
    public final static Set<String> uncompressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount = new HashSet<>();
    
    public final static Set<String> compressedTestAddressesAsFixedWidthBase58BitcoinAddress = new HashSet<>();
    public final static Set<String> uncompressedTestAddressesAsFixedWidthBase58BitcoinAddress = new HashSet<>();
    public final static Set<String> compressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress = new HashSet<>();
    public final static Set<String> uncompressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress = new HashSet<>();
    
    public final static Set<String> compressedTestAddressesAsHexHash = new HashSet<>();
    public final static Set<String> uncompressedTestAddressesAsHexHash = new HashSet<>();
    public final static Set<String> compressedTestAddressesWithStaticAmountAsHexHash = new HashSet<>();
    public final static Set<String> uncompressedTestAddressesWithStaticAmountAsHexHash = new HashSet<>();

    private final static String ADDRESS_FILE_ONE = "addressesOne.txt";
    private final static String ADDRESS_FILE_TWO = "addressesTwo.txt";
    private final static String ADDRESS_FILE_THREE = "addressesThree.txt";
    public final static int NUMBER_OF_ADRESSES = 5;

    public static final Coin amountFirstAddress = Coin.FIFTY_COINS;
    public static final Coin amountOtherAddresses = Coin.SATOSHI;
    
    public static final String AMOUNT_FIRST_ADDRESS_AS_STRING = "5000000000";
    public static final String AMOUNT_OTHER_ADDRESSES_AS_STRING = "1";
    
    public static final String STATIC_EMPTY_AMOUNT_AS_STRING = "0";

    public final static Coin[] AMOUNTS = {
        amountFirstAddress,
        amountOtherAddresses,
        amountOtherAddresses,
        amountOtherAddresses,
        amountOtherAddresses
    };

    private final TestAddresses42 testAddresses;
    
    private static final String COMMA_SEPARATOR = SeparatorFormat.COMMA.getSymbol();
    private static final List<String> NO_AMOUNTS = List.of();
    
    /**
     * A 20-byte test address (hash160) that is guaranteed not to exist in the generated test datasets.
     * <p>
     * This address is used in negative unit tests to verify that lookup methods
     * (e.g. {@code containsAddress}) correctly return {@code false} or {@code null}
     * when queried with a hash160 value that is not present.
     * <p>
     * The array contains exactly {@link PublicKeyBytes#RIPEMD160_HASH_NUM_BYTES} distinct byte values.
     * The final byte is explicitly set to the constant value itself to emphasize the expected length.
     * <p>
     * Note: This address does not collide with any hash160 values in {@link TestAddresses42}.
     */
    public static final byte[] NON_EXISTING_ADDRESS = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
        11, 12, 13, 14, 15, 16, 17, 18, 19, PublicKeyBytes.RIPEMD160_HASH_NUM_BYTES
    };
    
    public TestAddressesFiles(boolean compressed) {
        testAddresses = new TestAddresses42(NUMBER_OF_ADRESSES, compressed);

        TestAddresses42 uc = new TestAddresses42(NUMBER_OF_ADRESSES, false);
        TestAddresses42 co = new TestAddresses42(NUMBER_OF_ADRESSES, true);
        
        final String witnessProgramAsBase58 = P2WPKH.BitcoinP2WPKH.getWitnessProgramAsBase58();
        final String witnessProgramAsHex = P2WPKH.BitcoinP2WPKH.getWitnessProgramAsHex();

        // DynamicWidthBase58BitcoinAddressWithAmount
        addFormattedAddresses(
            uncompressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount,
            uc::getIndexAsBase58String,
            () -> witnessProgramAsBase58,
            List.of(AMOUNT_FIRST_ADDRESS_AS_STRING, AMOUNT_OTHER_ADDRESSES_AS_STRING)
        );

        addFormattedAddresses(
            compressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount,
            co::getIndexAsBase58String,
            () -> witnessProgramAsBase58,
            List.of(AMOUNT_FIRST_ADDRESS_AS_STRING, AMOUNT_OTHER_ADDRESSES_AS_STRING)
        );

        addFormattedAddresses(
            uncompressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount,
            uc::getIndexAsBase58String,
            () -> witnessProgramAsBase58,
            List.of(STATIC_EMPTY_AMOUNT_AS_STRING)
        );

        addFormattedAddresses(
            compressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount,
            co::getIndexAsBase58String,
            () -> witnessProgramAsBase58,
            List.of(STATIC_EMPTY_AMOUNT_AS_STRING)
        );

        // FixedWidthBase58BitcoinAddress (no amounts)
        addFormattedAddresses(
            uncompressedTestAddressesAsFixedWidthBase58BitcoinAddress,
            uc::getIndexAsBase58String,
            () -> witnessProgramAsBase58,
            NO_AMOUNTS
        );

        addFormattedAddresses(
            compressedTestAddressesAsFixedWidthBase58BitcoinAddress,
            co::getIndexAsBase58String,
            () -> witnessProgramAsBase58,
            NO_AMOUNTS
        );

        addFormattedAddresses(
            uncompressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress,
            uc::getIndexAsBase58String,
            () -> witnessProgramAsBase58,
            NO_AMOUNTS
        );

        addFormattedAddresses(
            compressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress,
            co::getIndexAsBase58String,
            () -> witnessProgramAsBase58,
            NO_AMOUNTS
        );

        // HexHash (no amounts)
        addFormattedAddresses(
            uncompressedTestAddressesAsHexHash,
            uc::getIndexAsHash160HexEncoded,
            () -> witnessProgramAsHex,
            NO_AMOUNTS
        );

        addFormattedAddresses(
            compressedTestAddressesAsHexHash,
            co::getIndexAsHash160HexEncoded,
            () -> witnessProgramAsHex,
            NO_AMOUNTS
        );

        addFormattedAddresses(
            uncompressedTestAddressesWithStaticAmountAsHexHash,
            uc::getIndexAsHash160HexEncoded,
            () -> witnessProgramAsHex,
            NO_AMOUNTS
        );

        addFormattedAddresses(
            compressedTestAddressesWithStaticAmountAsHexHash,
            co::getIndexAsHash160HexEncoded,
            () -> witnessProgramAsHex,
            NO_AMOUNTS
        );
    }

    private void addFormattedAddresses(
        Set<String> targetSet,
        Function<Integer, String> addressGenerator,
        Supplier<String> staticAddressSupplier,
        List<String> amounts
    ) {
        boolean includeAmounts = !amounts.isEmpty();
        String fallbackAmount = includeAmounts ? amounts.getLast() : null;

        for (int i = 0; i < NUMBER_OF_ADRESSES; i++) {
            String address = addressGenerator.apply(i);
            if (includeAmounts) {
                String amount = i < amounts.size() ? amounts.get(i) : fallbackAmount;
                targetSet.add(address + COMMA_SEPARATOR + amount);
            } else {
                targetSet.add(address);
            }
        }

        String staticAddress = staticAddressSupplier.get();
        targetSet.add(includeAmounts
            ? staticAddress + COMMA_SEPARATOR + fallbackAmount
            : staticAddress
        );
    }

    @Override
    public List<String> createAddressesFiles(TemporaryFolder folder, boolean addInvalidAddresses) throws IOException {
        File one = folder.newFile(ADDRESS_FILE_ONE);
        File two = folder.newFile(ADDRESS_FILE_TWO);
        File three = folder.newFile(ADDRESS_FILE_THREE);

        Files.write(one.toPath(), Arrays.asList(
                testAddresses.getIndexAsBase58String(0) + COMMA.getSymbol() + amountFirstAddress,
                testAddresses.getIndexAsBase58String(1) + TAB_SPLIT.getSymbol() + amountOtherAddresses,
                testAddresses.getIndexAsBase58String(2) + SEMICOLON.getSymbol() + "1"
        ));
        Files.write(two.toPath(), Collections.singletonList(
                testAddresses.getIndexAsBase58String(3)
        ));
        
        List<String> listThree = new ArrayList<>();
        
        {
            listThree.add("# Test");
            listThree.add("1WrOngAddressFormat");
            listThree.add(P2WPKH.BitcoinP2WPKH.getPublicAddress());
            listThree.add(testAddresses.getIndexAsBase58String(4));

            if (addInvalidAddresses) {
                // secret : 1
                listThree.add("1EHNa6Q4Jz2uvNExL497mE43ikXhwF6kZm");
                listThree.add("1BgGZ9tcN4rm9KBzDn7KprQz87SZ26SAMH");
            }   
        }
        
        Files.write(three.toPath(), listThree);
        List<String> addresses = new ArrayList<>();
        addresses.add(one.getAbsolutePath());
        addresses.add(two.getAbsolutePath());
        addresses.add(three.getAbsolutePath());
        return addresses;
    }

    @Override
    public TestAddresses getTestAddresses() {
        return testAddresses; 
    }

}
