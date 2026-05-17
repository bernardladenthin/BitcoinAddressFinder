// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

public class CKeyProducerJavaSocket extends CKeyProducerJavaReceiver {
    
    public enum Mode {
        CLIENT,
        SERVER
    }
    
    /** Hostname or IP address for client mode; ignored in server mode */
    public String host = "localhost";

    /** Port number to connect to or listen on */
    public int port = 12345;

    /** Operating mode: client or server */
    public Mode mode = Mode.SERVER;

    /** Socket read and connection timeout in milliseconds */
    public int timeout = 3000;

    /** Number of attempts to reconnect if connection fails */
    public int connectionRetryCount = 5;

    /** Number of attempts to retry reading a secret after I/O failure */
    public int readRetryCount = 5;

    /** Delay in milliseconds between retry attempts */
    public int retryDelayMillisConnect = 1000;

    /** Delay in milliseconds between retry attempts */
    public int retryDelayMillisRead = 1000;

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
