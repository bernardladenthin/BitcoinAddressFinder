// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import org.junit.Test;
import org.slf4j.Logger;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class OpenCLContextTest {

    private final BitHelper bitHelper = new BitHelper();

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_defaultConstructor_noExceptionThrown() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        // act
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        // assert
        assertThat(openCLContext, is(notNullValue()));
    }

    @Test
    public void constructor_mockLoggerGiven_noExceptionThrown() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        Logger mockLogger = mock(Logger.class);

        // act
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper, mockLogger);

        // assert
        assertThat(openCLContext, is(notNullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="init">
    @OpenCLTest
    @Test
    public void init_defaultConfiguration_logsSelectedDeviceInfo() throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        Logger mockLogger = mock(Logger.class);
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper, mockLogger);

        try {
            // act
            openCLContext.init();

            // assert
            verify(mockLogger, times(1)).info(eq("Selected OpenCL device:\n{}"), argThat(
                (String s) -> s.contains("--- Info for OpenCL device:")
            ));
        } finally {
            openCLContext.close();
        }
    }
    // </editor-fold>
}
