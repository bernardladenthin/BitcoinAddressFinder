// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BlockedBloomAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BlockedBloomGpuFilterData;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the configured blocked-Bloom geometry
 * ({@code lmdbConfigurationReadOnly.blockedBloomBitsPerEntry} /
 * {@code blockedBloomK}) actually reaches the filter the consumer builds, and that an incomplete
 * configuration falls back to the class defaults instead of mixing one configured value with one
 * default.
 *
 * <p><b>Why this matters.</b> {@code k} and the density are a <em>measured pair</em>, not two
 * independent knobs: the optimum {@code k} grows sub-linearly with bits/entry (5 / 6 / 7 / 7 / 8 / 9
 * at 8 / 11 / 14 / 17 / 21 / 26). Mixing a configured {@code k} with the default density — or
 * silently ignoring the configuration outright — produces a filter that is neither the shipped
 * default nor the requested tuning. Nothing reports this: the filter still has no false negatives,
 * so the run looks healthy while the false-positive rate, and with it the load on the
 * single-threaded consumer, is off by a factor the operator never asked for. This is
 * silently-ignored configuration, which is invisible without a test.
 */
public class ConsumerJavaBlockedBloomGeometryTest {

    /** Mirrors {@code BlockedBloomAddressPresence.DEFAULT_K}, which is package-private there. */
    private static final int DEFAULT_K = 6;

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);

    /**
     * Builds a consumer over a small test LMDB with the {@code BLOCKED_BLOOM} backend and the given
     * geometry ({@code 0} meaning "not configured"), and returns the geometry of the filter that was
     * actually built.
     */
    private BlockedBloomGpuFilterData buildFilterGeometry(int k, int bitsPerEntry) throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        // A fresh sub-directory per build: several tests here build more than one filter, and the
        // address-file fixtures share fixed file names inside the target directory.
        Path caseFolder = Files.createTempDirectory(folder, "blockedBloomCase");
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(caseFolder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.lmdbConfigurationReadOnly.addressLookupBackend = AddressLookupBackend.BLOCKED_BLOOM;
        cConsumerJava.lmdbConfigurationReadOnly.blockedBloomK = k;
        cConsumerJava.lmdbConfigurationReadOnly.blockedBloomBitsPerEntry = bitsPerEntry;

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        try {
            consumerJava.initLMDB();

            // The blocked Bloom filter is probabilistic, so it is wrapped by an accelerator that
            // verifies hits against the still-open LMDB.
            assertThat(consumerJava.lookup, is(instanceOf(BlockedBloomAccelerator.class)));
            return ((BlockedBloomAccelerator) consumerJava.lookup).getFilter().toGpuFilterData();
        } finally {
            consumerJava.interrupt();
        }
    }

    @Test
    public void initLMDB_blockedBloomGeometryConfigured_reachesTheBuiltFilter() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        final int configuredK = 9;
        // 512 bits/entry == exactly one block per entry, so the block count is an exact, tiny-database
        // -proof readout of the density that was applied (see the ratio assertion below).
        final int configuredBitsPerEntry = 512;

        BlockedBloomGpuFilterData geometry = buildFilterGeometry(configuredK, configuredBitsPerEntry);

        // k reaches the filter verbatim; a dropped configuration would show the default 6 here.
        assertThat(geometry.k(), is(equalTo(configuredK)));
        assertThat(geometry.numBlocks(), is(greaterThan(0)));
    }

    @Test
    public void initLMDB_blockedBloomDensityConfigured_scalesTheBlockCountProportionally() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        // Two densities that divide the 512-bit block size exactly, so the expected relation is
        // exact regardless of how many addresses the test database holds: 512 bits/entry gives one
        // block per entry, 1024 gives two. A configured density that never reached the sizing would
        // produce the same block count for both.
        BlockedBloomGpuFilterData oneBlockPerEntry = buildFilterGeometry(DEFAULT_K, 512);
        BlockedBloomGpuFilterData twoBlocksPerEntry = buildFilterGeometry(DEFAULT_K, 1024);

        assertThat(twoBlocksPerEntry.numBlocks(), is(equalTo(2 * oneBlockPerEntry.numBlocks())));
        assertThat(twoBlocksPerEntry.sizeInBytes(), is(equalTo(2 * oneBlockPerEntry.sizeInBytes())));
    }

    @Test
    public void initLMDB_blockedBloomGeometryUnset_usesTheClassDefaults() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        // 0 / 0 is the shipped configuration: both knobs unset -> the measured default pair.
        BlockedBloomGpuFilterData geometry = buildFilterGeometry(0, 0);

        assertThat(geometry.k(), is(equalTo(DEFAULT_K)));
    }

    @Test
    public void initLMDB_onlyKConfigured_fallsBackToBothDefaults() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        // Half a configuration is not a tuning. Honouring k while sizing at the default density
        // would build a geometry that was never measured — the pair is what was swept, so an
        // incomplete request must fall back wholesale rather than blend.
        BlockedBloomGpuFilterData geometry = buildFilterGeometry(9, 0);

        assertThat(geometry.k(), is(equalTo(DEFAULT_K)));
    }

    @Test
    public void initLMDB_onlyBitsPerEntryConfigured_fallsBackToBothDefaults() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        // Mirror of the case above: the density is set but k is not, so the default density must be
        // used too. Sizing at 1024 bits/entry while probing with the k measured for 11 is exactly
        // the mismatched pair this fallback exists to prevent.
        BlockedBloomGpuFilterData onlyDensity = buildFilterGeometry(0, 1024);
        BlockedBloomGpuFilterData bothUnset = buildFilterGeometry(0, 0);

        assertThat(onlyDensity.k(), is(equalTo(DEFAULT_K)));
        assertThat(onlyDensity.numBlocks(), is(equalTo(bothUnset.numBlocks())));
    }
}
