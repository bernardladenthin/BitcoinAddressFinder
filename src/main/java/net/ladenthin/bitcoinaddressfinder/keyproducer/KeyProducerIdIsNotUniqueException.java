// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.keyproducer;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.Finder} during
 * producer initialisation when two or more producers share the same
 * {@code keyProducerId}. Each producer must be identified by a unique string
 * so that hits can be attributed to the correct producer.
 */
public class KeyProducerIdIsNotUniqueException extends RuntimeException {

    private final String id;

    public KeyProducerIdIsNotUniqueException(String id) {
        super("Key producer id must be unique: " + id);
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
