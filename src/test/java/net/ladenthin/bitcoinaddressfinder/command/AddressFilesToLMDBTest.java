// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.LMDBBase;
import net.ladenthin.bitcoinaddressfinder.LMDBHandle;
import net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.*;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.AddressesFileSpecialUsecases;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.AddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import nl.altindag.log.LogCaptor;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.LegacyAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Direct unit tests for {@link AddressFilesToLMDB}.
 *
 * Tests the run() and interrupt() methods with parameterized test data from existing test providers.
 */
public class AddressFilesToLMDBTest extends LMDBBase {

    @BeforeEach
    public void init() throws IOException {}

    @Test
    public void addressFilesToLMDB_addressFileDoesNotExists_throwsIllegalArgumentException() throws IOException {
        // arrange, act
        CAddressFilesToLMDB addressFilesToLMDBConfigurationWrite = new CAddressFilesToLMDB();

        addressFilesToLMDBConfigurationWrite.addressesFiles.add("thisFileDoesNotExists.txt");
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite = new CLMDBConfigurationWrite();
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();
        String lmdbFolderPath = lmdbFolder.getAbsolutePath();
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.lmdbDirectory = lmdbFolderPath;
        AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(addressFilesToLMDBConfigurationWrite);
        assertThrows(IllegalArgumentException.class, () -> addressFilesToLMDB.run());
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT)
    public void addressFilesToLMDB_createLMDB_containingTestAddressesHashesWithCorrectAmount(
            boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange, act
        AddressesFiles addressesFiles = new TestAddressesFiles(compressed);
        try (LMDBHandle handle = createAndFillAndOpenLMDB(useStaticAmount, addressesFiles, false, false)) {
            // assert
            assertThat(handle.persistence().count(), is(equalTo(6L)));

            Coin[] amounts = new Coin[TestAddressesFiles.NUMBER_OF_ADRESSES];
            String[] base58Adresses =
                    addressesFiles.getTestAddresses().getAsBase58StringList().toArray(new String[0]);

            for (int i = 0; i < amounts.length; i++) {
                String base58Adresse = base58Adresses[i];
                LegacyAddress fromBase58 = LegacyAddress.fromBase58(base58Adresse, network);
                ByteBuffer hash160 = keyUtility.addressToByteBuffer(fromBase58);
                amounts[i] = handle.persistence().getAmount(hash160);
                if (useStaticAmount) {
                    assertThat(amounts[i], is(equalTo(Coin.ZERO)));
                } else {
                    assertThat(amounts[i], is(equalTo(TestAddressesFiles.AMOUNTS[i])));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_STATIC_AMOUNT)
    public void addressFilesToLMDB_createLMDBWithStaticAddresses_containingStaticHashes(boolean useStaticAmount)
            throws Exception {
        // arrange, act
        StaticAddressesFiles staticAddressesFiles = new StaticAddressesFiles();

        // assert
        try (LMDBHandle handle = createAndFillAndOpenLMDB(useStaticAmount, staticAddressesFiles, false, false)) {
            assertThat(handle.persistence().count(), is(equalTo((long)
                    staticAddressesFiles.getSupportedAddresses().size())));

            for (P2PKH staticTestAddress : P2PKH.values()) {
                ByteBuffer hash160AsByteBuffer = staticTestAddress.getPublicKeyHashAsByteBuffer();
                boolean contains = handle.lookup().containsAddress(hash160AsByteBuffer);
                assertThat(contains, is(equalTo(Boolean.TRUE)));
            }
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BLOOM_FILTER_ENABLED)
    public void containsAddress_behavesCorrectly_withOrWithoutBloomFilter(boolean useBloomFilter) throws Exception {
        AddressesFiles addressesFiles = new TestAddressesFiles(true);

        try (LMDBHandle handle = createAndFillAndOpenLMDB(false, addressesFiles, false, useBloomFilter)) {
            TestAddresses testAddresses = addressesFiles.getTestAddresses();
            ByteBuffer hash160 = testAddresses.getIndexAsHash160ByteBuffer(0);

            boolean contains = handle.lookup().containsAddress(hash160);
            assertThat("Should find known address", contains, is(true));
        }
    }

    /**
     * I got in the past the exception:
     * {@link java.nio.BufferUnderflowException} because zero values are stored with {@code byteBuffer.capacity() == 0}.
     */
    @Test
    public void addressFilesToLMDB_addressWithAmountOfZero_noExceptionThrown() throws Exception {
        // arrange, act
        AddressesFileSpecialUsecases addressesFileSpecialUsecases = new AddressesFileSpecialUsecases();
        // TestAddressesFiles testAddressesFiles = new TestAddressesFiles(true);

        // assert
        try (LMDBHandle handle = createAndFillAndOpenLMDB(false, addressesFileSpecialUsecases, false, false)) {
            assertThat(handle.persistence().count(), is(equalTo((long)
                    addressesFileSpecialUsecases.getAllAddresses().size())));

            TestAddresses testAddresses = addressesFileSpecialUsecases.getTestAddresses();

            for (int i = 0; i < testAddresses.getNumberOfAddresses(); i++) {
                ByteBuffer hash160 = testAddresses.getIndexAsHash160ByteBuffer(i);
                Coin amount = handle.persistence().getAmount(hash160);
                assertThat(amount, is(equalTo(Coin.ZERO)));

                boolean contains = handle.lookup().containsAddress(hash160);
                assertThat(contains, is(equalTo(Boolean.TRUE)));
            }
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BLOOM_FILTER_ENABLED)
    public void containsAddress_returnsFalseForUnknownAddress(boolean useBloomFilter) throws Exception {
        AddressesFiles addressesFiles = new TestAddressesFiles(true);

        try (LMDBHandle handle = createAndFillAndOpenLMDB(false, addressesFiles, false, useBloomFilter)) {
            ByteBuffer hash160 =
                    keyUtility.byteBufferUtility().byteArrayToByteBuffer(TestAddressesFiles.NON_EXISTING_ADDRESS);

            boolean contains = handle.lookup().containsAddress(hash160);
            assertThat(
                    "containsAddress() must return false for a known non-existing address used for negative testing.",
                    contains,
                    is(false));
        }
    }

    // <editor-fold defaultstate="collapsed" desc="multi-threaded import">

    /**
     * The whole address set must be imported regardless of the reader-thread count. {@code threads == 1}
     * is the deterministic single-threaded path; {@code 2} and {@code 4} read files in parallel through
     * the single LMDB writer. With {@code useStaticAmount == true} the order does not matter, so every
     * thread count must yield exactly the same, complete database.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    public void addressFilesToLMDB_multiThreaded_importsCompleteAddressSet(int threads) throws Exception {
        StaticAddressesFiles staticAddressesFiles = new StaticAddressesFiles();
        try (LMDBHandle handle =
                createAndFillAndOpenLMDB(true, staticAddressesFiles, false, AddressLookupBackend.LMDB_ONLY, threads)) {
            assertThat(handle.persistence().count(), is(equalTo((long)
                    staticAddressesFiles.getSupportedAddresses().size())));

            for (P2PKH staticTestAddress : P2PKH.values()) {
                boolean contains = handle.lookup().containsAddress(staticTestAddress.getPublicKeyHashAsByteBuffer());
                assertThat(contains, is(equalTo(Boolean.TRUE)));
            }
        }
    }

    /**
     * With the addresses spread across several files, {@code threads == 4} genuinely reads more than one
     * file at a time. The imported set must still be complete and correct.
     */
    @Test
    public void addressFilesToLMDB_multiThreadedAcrossMultipleFiles_importsAllAddresses() throws Exception {
        List<String> files = writeAddressFilesRoundRobin(base58P2PKHAddresses(), 8, "multi");

        File lmdbDir = runImport(4, true, files, "multi");

        LMDBPersistence lmdb = openReadOnly(lmdbDir);
        try {
            assertThat(lmdb.count(), is(equalTo((long) P2PKH.values().length)));
            for (P2PKH staticTestAddress : P2PKH.values()) {
                assertThat(lmdb.containsAddress(staticTestAddress.getPublicKeyHashAsByteBuffer()), is(true));
            }
        } finally {
            lmdb.close();
        }
    }

    /** Each finished file logs an "X/Y files" progress marker so a long parallel import stays observable. */
    @Test
    public void addressFilesToLMDB_logsPerFileProgress() throws Exception {
        List<String> files = writeAddressFilesRoundRobin(base58P2PKHAddresses(), 8, "progress");
        try (LogCaptor logCaptor = LogCaptor.forClass(AddressFilesToLMDB.class)) {
            runImport(4, true, files, "progress");
            assertThat(logCaptor.getInfoLogs(), hasItem(containsString("/8 files): ")));
            assertThat(logCaptor.getInfoLogs(), hasItem(containsString("finished (8/8 files): ")));
        }
    }

    /**
     * Order-sensitivity warning: it fires only when reading in parallel ({@code threads > 1}) can change
     * the write order <b>and</b> the stored amount depends on that order ({@code useStaticAmount == false}).
     */
    @Test
    public void addressFilesToLMDB_multiThreadedNonStaticAmount_logsOrderWarning() throws Exception {
        List<String> files = writeAddressFilesRoundRobin(base58P2PKHAddresses(), 4, "warn");
        try (LogCaptor logCaptor = LogCaptor.forClass(AddressFilesToLMDB.class)) {
            runImport(2, false, files, "warn");
            assertThat(logCaptor.getWarnLogs(), hasItem(containsString("useStaticAmount=false")));
        }
    }

    /** {@code useStaticAmount == true} is order-safe with any thread count, so no warning is logged. */
    @Test
    public void addressFilesToLMDB_multiThreadedStaticAmount_doesNotLogOrderWarning() throws Exception {
        List<String> files = writeAddressFilesRoundRobin(base58P2PKHAddresses(), 4, "nowarn");
        try (LogCaptor logCaptor = LogCaptor.forClass(AddressFilesToLMDB.class)) {
            runImport(2, true, files, "nowarn");
            assertThat(logCaptor.getWarnLogs(), is(empty()));
        }
    }

    /** {@code threads == 1} preserves the exact order, so it is safe with {@code useStaticAmount == false}. */
    @Test
    public void addressFilesToLMDB_singleThreadNonStaticAmount_doesNotLogOrderWarning() throws Exception {
        List<String> files = writeAddressFilesRoundRobin(base58P2PKHAddresses(), 4, "single");
        try (LogCaptor logCaptor = LogCaptor.forClass(AddressFilesToLMDB.class)) {
            runImport(1, false, files, "single");
            assertThat(logCaptor.getWarnLogs(), is(empty()));
        }
    }

    /**
     * A tiny {@code writeBatchSize} forces the writer through many flushes and a tiny
     * {@code queueCapacity} exercises the queue back-pressure; the imported set must still be complete.
     */
    @Test
    public void addressFilesToLMDB_smallBatchAndQueue_importsAllAddresses() throws Exception {
        List<String> files = writeAddressFilesRoundRobin(base58P2PKHAddresses(), 4, "batch");

        File lmdbDir = runImport(2, true, files, "batch", 3, 8);

        LMDBPersistence lmdb = openReadOnly(lmdbDir);
        try {
            assertThat(lmdb.count(), is(equalTo((long) P2PKH.values().length)));
            for (P2PKH staticTestAddress : P2PKH.values()) {
                assertThat(lmdb.containsAddress(staticTestAddress.getPublicKeyHashAsByteBuffer()), is(true));
            }
        } finally {
            lmdb.close();
        }
    }

    private List<String> base58P2PKHAddresses() {
        List<String> addresses = new ArrayList<>();
        for (P2PKH staticTestAddress : P2PKH.values()) {
            addresses.add(staticTestAddress.getPublicAddress());
        }
        return addresses;
    }

    private List<String> writeAddressFilesRoundRobin(List<String> addresses, int fileCount, String prefix)
            throws IOException {
        List<List<String>> buckets = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            buckets.add(new ArrayList<>());
        }
        for (int i = 0; i < addresses.size(); i++) {
            buckets.get(i % fileCount).add(addresses.get(i));
        }
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            File file = folder.resolve(prefix + "_" + i + ".txt").toFile();
            Files.write(file.toPath(), buckets.get(i));
            paths.add(file.getAbsolutePath());
        }
        return paths;
    }

    private File runImport(int threads, boolean useStaticAmount, List<String> files, String name) throws IOException {
        return runImport(threads, useStaticAmount, files, name, 10_000, 200_000);
    }

    private File runImport(
            int threads,
            boolean useStaticAmount,
            List<String> files,
            String name,
            int writeBatchSize,
            int queueCapacity)
            throws IOException {
        CAddressFilesToLMDB config = new CAddressFilesToLMDB();
        config.addressesFiles.addAll(files);
        config.threads = threads;
        config.writeBatchSize = writeBatchSize;
        config.queueCapacity = queueCapacity;
        config.lmdbConfigurationWrite = new CLMDBConfigurationWrite();
        config.lmdbConfigurationWrite.useStaticAmount = useStaticAmount;
        config.lmdbConfigurationWrite.staticAmount = 0L;
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb-" + name)).toFile();
        config.lmdbConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        new AddressFilesToLMDB(config).run();
        return lmdbFolder;
    }

    private LMDBPersistence openReadOnly(File lmdbDir) {
        CLMDBConfigurationReadOnly readOnly = new CLMDBConfigurationReadOnly();
        readOnly.lmdbDirectory = lmdbDir.getAbsolutePath();
        readOnly.addressLookupBackend = AddressLookupBackend.LMDB_ONLY;
        LMDBPersistence lmdb = new LMDBPersistence(readOnly, new PersistenceUtils(network));
        lmdb.init();
        return lmdb;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="progress/ETA formatting">

    /** Duration formatting is locale-independent (no decimals), so exact strings can be asserted. */
    @Test
    public void formatDuration_rendersCompactHoursMinutesSeconds() {
        AddressFilesToLMDB importer = new AddressFilesToLMDB(new CAddressFilesToLMDB());
        assertThat(importer.formatDuration(0L), is(equalTo("0s")));
        assertThat(importer.formatDuration(45L), is(equalTo("45s")));
        assertThat(importer.formatDuration(60L), is(equalTo("1m 0s")));
        assertThat(importer.formatDuration(125L), is(equalTo("2m 5s")));
        assertThat(importer.formatDuration(3_600L), is(equalTo("1h 0m")));
        assertThat(importer.formatDuration(8_100L), is(equalTo("2h 15m")));
        assertThat(importer.formatDuration(-1L), is(equalTo("?")));
    }

    /**
     * Byte formatting uses the default locale's decimal separator (matching the rest of the log), so
     * assert the chosen unit rather than the exact decimal, which keeps the test locale-independent.
     */
    @Test
    public void humanBytes_picksTheRightUnit() {
        AddressFilesToLMDB importer = new AddressFilesToLMDB(new CAddressFilesToLMDB());
        assertThat(importer.humanBytes(0L), is(equalTo("0 B")));
        assertThat(importer.humanBytes(512L), is(equalTo("512 B")));
        assertThat(importer.humanBytes(1L << 10), containsString("KiB"));
        assertThat(importer.humanBytes(1L << 20), containsString("MiB"));
        assertThat(importer.humanBytes(1L << 30), containsString("GiB"));
        assertThat(importer.humanBytes(58L << 30), containsString("GiB"));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="interrupt() edge cases">

    @Test
    public void interrupt_withNoCurrentFile_doesNotThrow() {
        // arrange
        CAddressFilesToLMDB config = new CAddressFilesToLMDB();
        AddressFilesToLMDB importer = new AddressFilesToLMDB(config);

        // act - should not throw exception
        importer.interrupt();

        // assert - method completed without exception
    }

    // </editor-fold>
}
