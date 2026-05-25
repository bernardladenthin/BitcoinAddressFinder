// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import org.junit.jupiter.api.Assumptions;

/**
 * Platform assumption for conditionally running LMDB tests.
 * Enabled by default, can be disabled with the system property: -DdisableLMDBTest
 */
public class LMDBPlatformAssume implements PlatformAssume {

    public void assumeLMDBExecution() {
        // If the system property is set, disable LMDB tests
        boolean disableLMDB = Boolean.getBoolean("net.ladenthin.bitcoinaddressfinder.disableLMDBTest");
        Assumptions.assumeTrue(!disableLMDB, "LMDB tests are disabled via -DdisableLMDBTest");
    }
}
