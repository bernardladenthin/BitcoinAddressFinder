// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import net.ladenthin.bitcoinaddressfinder.LMDBBase;
import net.ladenthin.bitcoinaddressfinder.configuration.CCompactLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link LMDBCompact}.
 */
public class LMDBCompactTest extends LMDBBase {

    /** A compacted copy must be a readable LMDB with exactly the same entries as the source. */
    @Test
    public void compactLMDB_producesReadableCompactedCopyWithSameContent() throws Exception {
        // arrange: build a source LMDB
        StaticAddressesFiles staticAddressesFiles = new StaticAddressesFiles();
        File sourceDirectory = new TestAddressesLMDB().createTestLMDB(folder, staticAddressesFiles, true, false);
        File targetDirectory = folder.resolve("lmdb_compact").toFile();

        CCompactLMDB config = new CCompactLMDB();
        config.lmdbConfigurationReadOnly.lmdbDirectory = sourceDirectory.getAbsolutePath();
        config.targetDirectory = targetDirectory.getAbsolutePath();

        // act
        new LMDBCompact(config).run();

        // assert: the compacted data.mdb exists
        assertThat(new File(targetDirectory, "data.mdb").exists(), is(true));

        // and the compacted database is readable with identical content
        CLMDBConfigurationReadOnly readOnly = new CLMDBConfigurationReadOnly();
        readOnly.lmdbDirectory = targetDirectory.getAbsolutePath();
        try (LMDBPersistence compacted = new LMDBPersistence(readOnly, new PersistenceUtils(network))) {
            compacted.init();
            assertThat(compacted.count(), is(equalTo((long)
                    staticAddressesFiles.getSupportedAddresses().size())));
            for (P2PKH staticTestAddress : P2PKH.values()) {
                assertThat(compacted.containsAddress(staticTestAddress.getPublicKeyHashAsByteBuffer()), is(true));
            }
        }
    }

    /** The target must differ from the source directory, otherwise it would clobber the source. */
    @Test
    public void compactLMDB_targetEqualsSource_throwsIllegalArgumentException() throws Exception {
        StaticAddressesFiles staticAddressesFiles = new StaticAddressesFiles();
        File sourceDirectory = new TestAddressesLMDB().createTestLMDB(folder, staticAddressesFiles, true, false);

        CCompactLMDB config = new CCompactLMDB();
        config.lmdbConfigurationReadOnly.lmdbDirectory = sourceDirectory.getAbsolutePath();
        config.targetDirectory = sourceDirectory.getAbsolutePath();

        assertThrows(IllegalArgumentException.class, () -> new LMDBCompact(config).run());
    }
}
