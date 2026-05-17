// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static java.lang.Boolean.FALSE;
import static org.hamcrest.Matchers.is;
import org.junit.Assume;

/**
 * Platform assumption for conditionally running LMDB tests.
 * Enabled by default, can be disabled with the system property: -DdisableLMDBTest
 */
public class LMDBPlatformAssume implements PlatformAssume {

    public void assumeLMDBExecution() {
        // If the system property is set, disable LMDB tests
        boolean disableLMDB = Boolean.getBoolean("net.ladenthin.bitcoinaddressfinder.disableLMDBTest");
        Assume.assumeThat("LMDB tests are disabled via -DdisableLMDBTest", disableLMDB, is(FALSE));
    }
}