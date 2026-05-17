// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

public class CKeyProducerJavaWebSocket extends CKeyProducerJavaReceiver {
    public int timeout = 1000;
    public int port = 8080;

    public int getPort() {
        return port;
    }
}
