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

public class CKeyProducerJavaZmq extends CKeyProducerJavaReceiver {
    
    public enum Mode {
        CONNECT,
        BIND
    }

    /** The ZMQ address, e.g., tcp://localhost:5555 */
    public String address = "tcp://localhost:5555";

    /** Whether this side binds to the address or connects */
    public Mode mode = Mode.BIND;

    /**
     * Timeout for the receive operation in milliseconds.
     *
     * A value of:
     * - `0` means `recv` returns immediately, returning null if no message is available.
     * - `-1` (default) means `recv` will block until a message is available (infinite wait).
     * - Any positive value causes `recv` to wait that amount of time before giving up and returning null.
     *
     * This maps to the ZMQ socket option `RCVTIMEO`.
     */
    public int timeout = -1;
}
