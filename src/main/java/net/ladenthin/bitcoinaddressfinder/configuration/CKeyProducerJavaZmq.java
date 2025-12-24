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
     * Timeout for receiving secrets, in milliseconds.
     *
     * <p>This timeout is applied consistently to:
     * <ul>
     *   <li>the underlying ZMQ socket receive operation (RCVTIMEO)</li>
     *   <li>the internal secret queue polling</li>
     * </ul>
     *
     * <p>Semantics:
     * <ul>
     *   <li>Must be a strictly positive value (&gt; 0)</li>
     *   <li>Specifies the maximum time to wait for each receive operation</li>
     * </ul>
     *
     * <p>Notes:
     * <ul>
     *   <li>Infinite blocking is intentionally not supported</li>
     *   <li>Values less than or equal to zero are invalid and may lead to undefined behavior</li>
     *   <li>Timeouts are enforced to guarantee responsive shutdown and deterministic behavior</li>
     * </ul>
     *
     * <p>This value maps directly to the ZMQ socket option {@code RCVTIMEO}.
     *
     * <p>Default: {@code 1000} ms
     */
    public int timeout = 1000;
}
