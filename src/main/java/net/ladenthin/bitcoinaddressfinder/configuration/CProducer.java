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
package net.ladenthin.bitcoinaddressfinder.configuration;

import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.jspecify.annotations.Nullable;

public class CProducer {
    
    public @Nullable String keyProducerId;
    
    /**
     * Range: {@code 0} (inclusive) to {@link PublicKeyBytes#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} (inclusive).
     */
    public int batchSizeInBits = 0;
    
    /**
     * The batch mode will use a private key increment internal to increase the performance.
     */
    public boolean batchUsePrivateKeyIncrement = true;
    
    /**
     * Enable the log output for the secret address.
     */
    public boolean logSecretBase;
    
    /**
     * Enable to let the producer run one time only.
     */
    public boolean runOnce = false;
    
    public int getOverallWorkSize(BitHelper bitHelper) {
        return bitHelper.convertBitsToSize(batchSizeInBits);
    }
}
