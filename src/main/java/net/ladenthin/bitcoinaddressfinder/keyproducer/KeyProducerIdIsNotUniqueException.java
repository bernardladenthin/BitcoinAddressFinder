// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.Finder} during
 * producer initialisation when two or more producers share the same
 * {@code keyProducerId}. Each producer must be identified by a unique string
 * so that hits can be attributed to the correct producer.
 */
public class KeyProducerIdIsNotUniqueException extends RuntimeException {

    /** The duplicate key-producer id supplied at construction time. */
    private final String id;

    /**
     * Creates a new exception for the offending duplicate id.
     *
     * @param id the duplicate key-producer id
     */
    public KeyProducerIdIsNotUniqueException(String id) {
        super("Key producer id must be unique: " + id);
        this.id = id;
    }

    /**
     * Returns the duplicate id that caused the exception.
     *
     * @return the duplicate id that caused the exception
     */
    public String getId() {
        return id;
    }
}
