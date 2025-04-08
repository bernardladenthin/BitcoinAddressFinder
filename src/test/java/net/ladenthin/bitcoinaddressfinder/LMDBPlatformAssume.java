// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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