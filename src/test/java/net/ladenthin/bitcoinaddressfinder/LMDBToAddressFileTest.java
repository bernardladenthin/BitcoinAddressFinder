// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFileOutputFormat;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class LMDBToAddressFileTest extends LMDBBase {

    @BeforeEach
    public void init() throws IOException {
    }

    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#compressedAndStaticAmount")
    public void writeAllAmountsToAddressFileAsDynamicWidthBase58BitcoinAddressWithAmount(boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange
        AtomicBoolean shouldRun = new AtomicBoolean(true);
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false, false)) {
            // act
            File file = Files.createTempFile(folder, "", "").toFile();
            persistence.writeAllAmountsToAddressFile(file, CAddressFileOutputFormat.DynamicWidthBase58BitcoinAddressWithAmount, shouldRun);

            // assert
            List<String> contents = FileUtils.readLines(file, StandardCharsets.UTF_8);

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);

            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected = TestAddressesFiles.compressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else if(compressed) {
                expected = TestAddressesFiles.compressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else if(useStaticAmount) {
                expected = TestAddressesFiles.uncompressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else {
                expected = TestAddressesFiles.uncompressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount;
            }

            assertThat(contentsAsSet, is(equalTo(expected)));
            
        }
    }
    
    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#compressedAndStaticAmount")
    public void writeAllAmountsToAddressFileAsFixedWidthBase58BitcoinAddress(boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange
        AtomicBoolean shouldRun = new AtomicBoolean(true);
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false, false)) {

            // act
            File file = Files.createTempFile(folder, "", "").toFile();
            persistence.writeAllAmountsToAddressFile(file, CAddressFileOutputFormat.FixedWidthBase58BitcoinAddress, shouldRun);

            // assert
            List<String> contents = FileUtils.readLines(file, StandardCharsets.UTF_8);

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);

            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected = TestAddressesFiles.compressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress;
            } else if(compressed) {
                expected = TestAddressesFiles.compressedTestAddressesAsFixedWidthBase58BitcoinAddress;
            } else if(useStaticAmount) {
                expected = TestAddressesFiles.uncompressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress;
            } else {
                expected = TestAddressesFiles.uncompressedTestAddressesAsFixedWidthBase58BitcoinAddress;
            }

            assertThat(contentsAsSet, is(equalTo(expected)));
        }
    }
    
    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#compressedAndStaticAmount")
    public void writeAllAmountsToAddressFileAsHexHash(boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange
        AtomicBoolean shouldRun = new AtomicBoolean(true);
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false, false)) {
            // act
            File file = Files.createTempFile(folder, "", "").toFile();
            persistence.writeAllAmountsToAddressFile(file, CAddressFileOutputFormat.HexHash, shouldRun);

            // assert
            List<String> contents = FileUtils.readLines(file, StandardCharsets.UTF_8);

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);

            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected = TestAddressesFiles.compressedTestAddressesWithStaticAmountAsHexHash;
            } else if(compressed) {
                expected = TestAddressesFiles.compressedTestAddressesAsHexHash;
            } else if(useStaticAmount) {
                expected = TestAddressesFiles.uncompressedTestAddressesWithStaticAmountAsHexHash;
            } else {
                expected = TestAddressesFiles.uncompressedTestAddressesAsHexHash;
            }

            assertThat(contentsAsSet, is(equalTo(expected)));
        }
    }
}
