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

import ch.qos.logback.classic.Level;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.cli.Main;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Ignore;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;

public class MainTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    private final Path resourceDirectory = Path.of("src","test","resources");
    private final Path testRoundtripDirectory = resourceDirectory.resolve("testRoundtrip");
    private final Path testOpenCLInfoDirectory = resourceDirectory.resolve("testOpenCLInfo");
    
    private final Path config_AddressFilesToLMDB_json = testRoundtripDirectory.resolve("config_AddressFilesToLMDB.json");
    private final Path config_LMDBToAddressFile_json = testRoundtripDirectory.resolve("config_LMDBToAddressFile.json");
    private final Path config_Find_SecretsFile_json = testRoundtripDirectory.resolve("config_Find_SecretsFile.json");
    
    private final Path config_Find_1OpenCLDevice_json = testRoundtripDirectory.resolve("config_Find_1OpenCLDevice.json");
    
    private final Path config_OpenCLInfo_json = testOpenCLInfoDirectory.resolve("config_OpenCLInfo.json");
    
    private static final long DEFAULT_INTERRUPT_DELAY_SECONDS = 10;
    
    private void interruptAfterDelay(Main main, long delaySeconds) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            main.interrupt();
        }, delaySeconds, TimeUnit.SECONDS);
    }

    // <editor-fold defaultstate="collapsed" desc="testRoundtrip">
    @Test
    @Ignore
    public void testRoundtrip_configurationsGiven_lmdbCreatedExportedAndRunFindSecretsFile() throws IOException, InterruptedException {
        // arrange, act, assert
        Main.main(new String[]{config_AddressFilesToLMDB_json.toAbsolutePath().toString()});
        
        Main.main(new String[]{config_LMDBToAddressFile_json.toAbsolutePath().toString()});
        
        Main.main(new String[]{config_Find_SecretsFile_json.toAbsolutePath().toString()});
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="testRoundtrip OpenCL producer">
    @Test(timeout = 60000) // 60 seconds
    @OpenCLTest
    public void testRoundtripOpenCLProducer_configurationsGiven_lmdbCreatedAndRunFindOpenCLDevice() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        Main mainAddressFilesToLMDB = new Main(Main.fromJson(Main.readString(config_AddressFilesToLMDB_json)));
        mainAddressFilesToLMDB.logConfigurationTransformation();
        mainAddressFilesToLMDB.run();
        
        new LogLevelChange().setLevel(Level.DEBUG);
        
        Main mainFind_1OpenCLDevice = new Main(Main.fromJson(Main.readString(config_Find_1OpenCLDevice_json)));
        
        // interrupt the act after 10 seconds
        interruptAfterDelay(mainFind_1OpenCLDevice, DEFAULT_INTERRUPT_DELAY_SECONDS);
        
        // act
        mainFind_1OpenCLDevice.logConfigurationTransformation();
        mainFind_1OpenCLDevice.run();
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="testRoundtrip">
    @Test
    @OpenCLTest
    public void testOpenCLInfo_configurationGiven_noExceptionThrown() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        Main mainFind_SecretsFile = new Main(Main.fromJson(Main.readString(config_OpenCLInfo_json)));
        mainFind_SecretsFile.logConfigurationTransformation();
        mainFind_SecretsFile.run();
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="invalidArgument">
    @Test
    public void main_noArgumentGiven_errorLogged() throws IOException, InterruptedException {
        // arrange
        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(true);
        Main.logger = logger;
        
        // act
        Main.main(new String[0]);
        
        // assert
        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        List<String> arguments = logCaptor.getAllValues();
        verify(logger, times(1)).error(logCaptor.capture());
        assertThat(arguments.get(0), is(equalTo("Invalid arguments. Pass path to configuration as first argument.")));
    }
    // </editor-fold>
}
