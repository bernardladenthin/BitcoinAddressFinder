// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.Finder} during
 * producer initialisation when a producer's {@code keyProducerId} is
 * {@code null}. Every producer must carry a non-null identifier so that
 * hits can be attributed to the correct producer.
 */
public class KeyProducerIdNullException extends RuntimeException {

    public KeyProducerIdNullException() {
        super("Key producer id must not be null.");
    }
}
