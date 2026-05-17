// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.util.concurrent.TimeUnit;

public class TestTimeProvider {
    
    public final static TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    public final static int DEFAULT_DELAY = 500;
    public final static int DEFAULT_SEND_DELAY = 300;
    public final static int DEFAULT_SEND_WAIT = 1000;
    public final static int DEFAULT_SETTLE_DELAY = 300;
    public final static int DEFAULT_ESTABLISH_DELAY = 300;
    public final static int DEFAULT_TIMEOUT = 500;
    public final static int DEFAULT_SOCKET_TIMEOUT = 2_000;

    public final static int DEFAULT_RETRY_COUNT = 3;
    public final static int DEFAULT_CONNECTION_RETRY_COUNT = 10;
    public final static int DEFAULT_RETRY_DELAY = 500;
    public final static int SHORT_DELAY = 100;
    public final static int LONG_SOCKET_TIMEOUT = 3_000;
    public final static int SOCKET_ACCEPT_TIMEOUT = 1_000;
}
