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

    /**
     * Generous timeout for async receiver-thread driven tests (e.g. ZMQ, WebSocket)
     * where the message path goes through one or more background threads. Sized so
     * that loaded CI runners don't fail tests when the background thread is briefly
     * starved by the OS scheduler; tests still complete in well under a second when
     * the system is idle.
     */
    public final static int RECEIVER_THREAD_TIMEOUT = 20_000;
}
