// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.engine.Finder} during
 * producer initialisation when two or more producers share the same
 * {@code keyProducerId}. Each producer must be identified by a unique string
 * so that hits can be attributed to the correct producer.
 */
public class KeyProducerIdIsNotUniqueException extends RuntimeException {

    /** Prefix prepended to every constructed exception message. */
    private static final String MESSAGE_PREFIX = "Key producer id must be unique: ";

    /** The duplicate key-producer id supplied at construction time. */
    private final String id;

    /**
     * Creates a new exception for the offending duplicate id.
     *
     * @param id the duplicate key-producer id; returned by {@link #getId()}
     */
    public KeyProducerIdIsNotUniqueException(String id) {
        super(MESSAGE_PREFIX + id);
        this.id = id;
    }

    /**
     * Creates a new exception for the offending duplicate id, chaining a cause.
     *
     * @param id    the duplicate key-producer id; returned by {@link #getId()}
     * @param cause the underlying cause
     */
    public KeyProducerIdIsNotUniqueException(String id, Throwable cause) {
        super(MESSAGE_PREFIX + id, cause);
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
