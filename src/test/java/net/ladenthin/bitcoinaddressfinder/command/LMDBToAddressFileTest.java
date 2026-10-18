// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.LMDBBase;
import net.ladenthin.bitcoinaddressfinder.LMDBHandle;
import net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFileOutputFormat;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBToAddressFile;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import nl.altindag.log.LogCaptor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class LMDBToAddressFileTest extends LMDBBase {

    @BeforeEach
    public void init() throws IOException {}

    /** An export stopped by {@code interrupt()} is incomplete and must not be reported as done. */
    @Test
    public void run_interruptedBeforeTheExport_doesNotReportTheExportAsDone() throws Exception {
        // arrange
        File lmdbDirectory =
                new TestAddressesLMDB().createTestLMDB(folder, new TestAddressesFiles(false), false, false);
        CLMDBToAddressFile configuration = new CLMDBToAddressFile();
        configuration.lmdbConfigurationReadOnly.lmdbDirectory = lmdbDirectory.getAbsolutePath();
        configuration.addressesFile = folder.resolve("export.txt").toFile().getAbsolutePath();
        LMDBToAddressFile lmdbToAddressFile = new LMDBToAddressFile(configuration);
        lmdbToAddressFile.interrupt();

        try (LogCaptor logCaptor = LogCaptor.forClass(LMDBToAddressFile.class)) {
            // act
            lmdbToAddressFile.run();

            // assert
            assertThat(logCaptor.getInfoLogs(), not(hasItem("writeAllAmounts done")));
            assertThat(logCaptor.getWarnLogs(), hasItem(containsString("interrupted")));
        }
    }

    /**
     * Every export format is read back by {@code AddressFilesToLMDB}: the re-imported database holds
     * exactly the hash160 set of the exported one, and the format that carries the amounts keeps them.
     */
    @ParameterizedTest
    @EnumSource(CAddressFileOutputFormat.class)
    public void exportThenImport_everyFormat_roundTripsTheHash160Set(CAddressFileOutputFormat format) throws Exception {
        // arrange
        File source = new TestAddressesLMDB().createTestLMDB(folder, new TestAddressesFiles(false), false, false);
        Map<String, Long> exported = readAll(source);
        File exportFile = folder.resolve("export-" + format + ".txt").toFile();
        try (LMDBPersistence lmdb = openReadOnly(source)) {
            lmdb.writeAllAmountsToAddressFile(exportFile, format, new AtomicBoolean(true));
        }

        // act
        CAddressFilesToLMDB importConfig = new CAddressFilesToLMDB();
        importConfig.addressesFiles.add(exportFile.getAbsolutePath());
        importConfig.lmdbConfigurationWrite = new CLMDBConfigurationWrite();
        importConfig.lmdbConfigurationWrite.useStaticAmount = false;
        File reimported =
                Files.createDirectory(folder.resolve("reimported-" + format)).toFile();
        importConfig.lmdbConfigurationWrite.lmdbDirectory = reimported.getAbsolutePath();
        new AddressFilesToLMDB(importConfig).run();

        // assert
        Map<String, Long> imported = readAll(reimported);
        assertThat(imported.keySet(), is(equalTo(exported.keySet())));
        if (format == CAddressFileOutputFormat.DynamicWidthBase58BitcoinAddressWithAmount) {
            assertThat(imported, is(equalTo(exported)));
        }
    }

    private static LMDBPersistence openReadOnly(File lmdbDirectory) {
        CLMDBConfigurationReadOnly readOnly = new CLMDBConfigurationReadOnly();
        readOnly.lmdbDirectory = lmdbDirectory.getAbsolutePath();
        readOnly.addressLookupBackend = AddressLookupBackend.LMDB_ONLY;
        LMDBPersistence lmdb =
                new LMDBPersistence(readOnly, new PersistenceUtils(new NetworkParameterFactory().getNetwork()));
        lmdb.init();
        return lmdb;
    }

    /** Every hash160 of the database (hex) with its amount in satoshi. */
    private static Map<String, Long> readAll(File lmdbDirectory) {
        Map<String, Long> all = new HashMap<>();
        try (LMDBPersistence lmdb = openReadOnly(lmdbDirectory);
                Stream<ByteBuffer> addresses = lmdb.addresses()) {
            addresses.forEach(hash160 -> {
                byte[] bytes = new byte[hash160.remaining()];
                hash160.duplicate().get(bytes);
                all.put(Hex.encodeHexString(bytes), lmdb.getAmount(hash160).getValue());
            });
        }
        return all;
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT)
    public void writeAllAmountsToAddressFileAsDynamicWidthBase58BitcoinAddressWithAmount(
            boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange
        AtomicBoolean shouldRun = new AtomicBoolean(true);
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        try (LMDBHandle handle = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false, false)) {
            // act
            File file = Files.createTempFile(folder, "junit", "").toFile();
            handle.persistence()
                    .writeAllAmountsToAddressFile(
                            file, CAddressFileOutputFormat.DynamicWidthBase58BitcoinAddressWithAmount, shouldRun);

            // assert
            List<String> contents = FileUtils.readLines(file, StandardCharsets.UTF_8);

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);

            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected =
                        TestAddressesFiles
                                .compressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else if (compressed) {
                expected = TestAddressesFiles.compressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else if (useStaticAmount) {
                expected =
                        TestAddressesFiles
                                .uncompressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else {
                expected = TestAddressesFiles.uncompressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount;
            }

            assertThat(contentsAsSet, is(equalTo(expected)));
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT)
    public void writeAllAmountsToAddressFileAsFixedWidthBase58BitcoinAddress(
            boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange
        AtomicBoolean shouldRun = new AtomicBoolean(true);
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        try (LMDBHandle handle = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false, false)) {

            // act
            File file = Files.createTempFile(folder, "junit", "").toFile();
            handle.persistence()
                    .writeAllAmountsToAddressFile(
                            file, CAddressFileOutputFormat.FixedWidthBase58BitcoinAddress, shouldRun);

            // assert
            List<String> contents = FileUtils.readLines(file, StandardCharsets.UTF_8);

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);

            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected = TestAddressesFiles.compressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress;
            } else if (compressed) {
                expected = TestAddressesFiles.compressedTestAddressesAsFixedWidthBase58BitcoinAddress;
            } else if (useStaticAmount) {
                expected = TestAddressesFiles.uncompressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress;
            } else {
                expected = TestAddressesFiles.uncompressedTestAddressesAsFixedWidthBase58BitcoinAddress;
            }

            assertThat(contentsAsSet, is(equalTo(expected)));
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT)
    public void writeAllAmountsToAddressFileAsHexHash(boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange
        AtomicBoolean shouldRun = new AtomicBoolean(true);
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        try (LMDBHandle handle = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false, false)) {
            // act
            File file = Files.createTempFile(folder, "junit", "").toFile();
            handle.persistence().writeAllAmountsToAddressFile(file, CAddressFileOutputFormat.HexHash, shouldRun);

            // assert
            List<String> contents = FileUtils.readLines(file, StandardCharsets.UTF_8);

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);

            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected = TestAddressesFiles.compressedTestAddressesWithStaticAmountAsHexHash;
            } else if (compressed) {
                expected = TestAddressesFiles.compressedTestAddressesAsHexHash;
            } else if (useStaticAmount) {
                expected = TestAddressesFiles.uncompressedTestAddressesWithStaticAmountAsHexHash;
            } else {
                expected = TestAddressesFiles.uncompressedTestAddressesAsHexHash;
            }

            assertThat(contentsAsSet, is(equalTo(expected)));
        }
    }
}
