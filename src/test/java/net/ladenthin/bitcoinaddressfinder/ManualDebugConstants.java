// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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

/**
 * Constants used exclusively for manual debugging and verification.
 */
public final class ManualDebugConstants {

    private ManualDebugConstants() {
        // Utility class – no instantiation allowed
    }

    /**
     * Enables runtime validation of public key generation logic.
     * 
     * Intended only for debugging purposes such as verifying OpenCL results or testing hardware correctness.
     * 
     * <p><b>Performance impact:</b> Enabling this drastically reduces throughput.</p>
     * 
     * @see net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava#runtimePublicKeyCalculationCheck
     */
    public static final boolean ENABLE_RUNTIME_PUBLIC_KEY_CALCULATION_CHECK = false;
}
