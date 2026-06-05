// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.ToString;

/**
 * Common configuration for key producers that receive secrets from an external transport.
 */
@ToString(callSuper = true)
public class CKeyProducerJavaReceiver extends CKeyProducerJava {

    /** Creates a new {@link CKeyProducerJavaReceiver}. */
    public CKeyProducerJavaReceiver() {}

    /** Enable logging of each received secret as hex */
    public boolean logReceivedSecret = false;
}
