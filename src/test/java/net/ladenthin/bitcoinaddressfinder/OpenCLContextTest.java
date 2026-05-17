// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
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
