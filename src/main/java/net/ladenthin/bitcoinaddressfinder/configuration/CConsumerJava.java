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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CConsumerJava {
    public @NonNull CLMDBConfigurationReadOnly lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
    public int printStatisticsEveryNSeconds = 60;
    public int threads = 4;
    /**
     * in ms.
     */
    public long delayEmptyConsumer = 100;
    public int queueSize = 10;
    
    /**
     * Enables runtime verification of public key calculation.
     * 
     * This performs a full consistency check for each generated key by comparing it
     * against a known-correct Java-based calculation. Use this option only in very specific
     * debugging or validation scenarios, such as:
     * 
     * <ul>
     *   <li>Suspected errors in the OpenCL implementation</li>
     *   <li>Hardware defects causing incorrect calculations</li>
     *   <li>Regression testing during development</li>
     * </ul>
     * 
     * <b>Warning:</b> This has a significant performance impact.
     * <ul>
     *   <li>Enabled: ~20k keys/second</li>
     *   <li>Disabled (default): ~10 million keys/second (LMDB benchmark)</li>
     * </ul>
     * 
     * <p>Do not enable in production or performance benchmarking environments.</p>
     */
    public boolean runtimePublicKeyCalculationCheck;
    
    public boolean enableVanity = false;
    
    public @Nullable String vanityPattern;
}
