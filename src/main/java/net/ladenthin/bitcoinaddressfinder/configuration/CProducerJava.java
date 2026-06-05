// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configuration for the Java (CPU) producer.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CProducerJava extends CProducer {
    /** Creates a new {@link CProducerJava}. */
    public CProducerJava() {}
}
