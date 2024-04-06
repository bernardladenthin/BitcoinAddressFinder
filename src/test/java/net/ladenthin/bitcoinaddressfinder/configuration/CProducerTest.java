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
