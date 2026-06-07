// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import nl.altindag.log.LogCaptor;
import nl.altindag.log.model.LogEvent;
import org.junit.jupiter.api.Test;

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
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="init">
    @OpenCLTest
    @Test
    public void init_defaultConfiguration_logsSelectedDeviceInfo() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        try (LogCaptor logCaptor = LogCaptor.forClass(OpenCLContext.class)) {
            // act
            openCLContext.init();

            // assert
            assertThat(
                    logCaptor.getLogEvents().stream()
                            .filter(e -> "INFO".equals(e.getLevel()))
                            .map(LogEvent::getFormattedMessage)
                            .anyMatch(m -> m.startsWith("Selected OpenCL device:")
                                    && m.contains("--- Info for OpenCL device:")),
                    is(true));
        } finally {
            openCLContext.close();
        }
    }
    // </editor-fold>
}
