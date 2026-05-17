// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import org.junit.Before;
import org.junit.Test;

public class CProducerTest {
    
    private final BitHelper bitHelper = new BitHelper();
    
    @Before
    public void init() throws IOException {
    }
    
    // <editor-fold defaultstate="collapsed" desc="default parameter for batchSizeInBits">
    @Test
    public void batchSizeInBits_configurationConstantsSet_isValidDefaultValue() throws IOException {
        CProducer cProducer = new CProducer();
        bitHelper.assertBatchSizeInBitsIsInRange(cProducer.batchSizeInBits);
    }
    // </editor-fold>
}
