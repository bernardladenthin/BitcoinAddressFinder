// @formatter:off
/**
 * Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
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
import java.nio.file.Path;
import net.ladenthin.bitcoinaddressfinder.cli.Main;

public class MainTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // <editor-fold defaultstate="collapsed" desc="testRoundtrip">
    @Test
    public void testRoundtrip_configurationsGiven_lmdbCreatedExportedAndRunFindSecretsFile() throws IOException, InterruptedException {
        // arrange
        Path resourceDirectory = Path.of("src","test","resources");
        Path testRoundtripDirectory = resourceDirectory.resolve("testRoundtrip");
        
        Path config_AddressFilesToLMDB_js = testRoundtripDirectory.resolve("config_AddressFilesToLMDB.js");
        Path config_LMDBToAddressFile_js = testRoundtripDirectory.resolve("config_LMDBToAddressFile.js");
        Path config_Find_SecretsFile_js = testRoundtripDirectory.resolve("config_Find_SecretsFile.js");
        
        Main mainAddressFilesToLMDB = Main.createFromConfigurationFile(config_AddressFilesToLMDB_js);
        mainAddressFilesToLMDB.run();
        
        Main mainLMDBToAddressFile = Main.createFromConfigurationFile(config_LMDBToAddressFile_js);
        mainLMDBToAddressFile.run();
        
        Main mainFind_SecretsFile = Main.createFromConfigurationFile(config_Find_SecretsFile_js);
        mainFind_SecretsFile.run();
    // </editor-fold>
    }
}
