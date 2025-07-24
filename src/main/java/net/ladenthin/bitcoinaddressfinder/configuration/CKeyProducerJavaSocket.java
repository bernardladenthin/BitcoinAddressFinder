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
package net.ladenthin.bitcoinaddressfinder.configuration;

import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;

public class CKeyProducerJavaSocket extends CKeyProducerJava {
    
    public enum Mode {
        CLIENT,
        SERVER
    }
    
    /** Hostname or IP address for client mode; ignored in server mode */
    public String host = "localhost";

    /** Port number to connect to or listen on */
    public int port = 12345;

    /** Socket read and connection timeout in milliseconds */
    public int timeout = 3000;

    /** Enable logging of each received secret as hex */
    public boolean logReceivedSecret = false;

    /** Operating mode: client or server */
    public Mode mode = Mode.SERVER;

    /** Number of attempts to reconnect if connection fails */
    public int connectionRetryCount = 5;

    /** Number of attempts to retry reading a secret after I/O failure */
    public int readRetryCount = 3;

    /** Delay in milliseconds between retry attempts */
    public int retryDelayMillisConnect = 1000;

    /** Delay in milliseconds between retry attempts */
    public int retryDelayMillisRead = 1000;
    
    /** Maximum allowed work size (number of secrets to generate) — 2^24 = 16,777,216*/
    public int maxWorkSize = 1 << PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY;

    /** Maximum retry attempts when partially reading a single secret */
    public int readPartialRetryCount = 5;

    /** Delay in milliseconds between partial read retry attempts */
    public int readPartialRetryDelayMillis = 20;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
