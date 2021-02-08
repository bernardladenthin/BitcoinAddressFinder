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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;

public class FinderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();


    @Test
    public void addSchutdownHook_noExceptionThrown() throws IOException {
        
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        finder.addSchutdownHook();
    }

}
