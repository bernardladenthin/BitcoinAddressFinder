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
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.util.concurrent.TimeUnit;

public class TestTimeProvider {
    
    public final static TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    public final static int DEFAULT_DELAY = 500;
    public final static int DEFAULT_SEND_DELAY = 300;
    public final static int DEFAULT_SETTLE_DELAY = 300;
    public final static int DEFAULT_ESTABLISH_DELAY = 300;
    public final static int DEFAULT_TIMEOUT = 500;
    public final static int DEFAULT_SOCKET_TIMEOUT = 2_000;
}
