// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.util.concurrent.TimeUnit;

public class TestTimeProvider {

    public static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    public static final int DEFAULT_DELAY = 500;
    public static final int DEFAULT_SEND_DELAY = 300;
    public static final int DEFAULT_SEND_WAIT = 1000;
    public static final int DEFAULT_SETTLE_DELAY = 300;
    public static final int DEFAULT_ESTABLISH_DELAY = 300;
    public static final int DEFAULT_TIMEOUT = 500;
    public static final int DEFAULT_SOCKET_TIMEOUT = 2_000;

    public static final int DEFAULT_RETRY_COUNT = 3;
    public static final int DEFAULT_CONNECTION_RETRY_COUNT = 10;
    public static final int DEFAULT_RETRY_DELAY = 500;
    public static final int SHORT_DELAY = 100;
    public static final int LONG_SOCKET_TIMEOUT = 3_000;
    public static final int SOCKET_ACCEPT_TIMEOUT = 1_000;
}
