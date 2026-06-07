// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import org.jspecify.annotations.Nullable;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.engine.Finder} when a
 * configured {@code keyProducerId} cannot be resolved to a registered producer.
 * This typically indicates a misconfiguration: the requested producer type is
 * not supported or the id was misspelled.
 */
public class KeyProducerIdUnknownException extends RuntimeException {

    /** Prefix prepended to every constructed exception message. */
    private static final String MESSAGE_PREFIX = "Key producer id is unknown: ";

    /** The unresolvable key-producer id supplied at construction time. */
    private final @Nullable String id;

    /**
     * Creates a new exception for the unknown id.
     *
     * @param id the unknown key-producer id; returned by {@link #getId()}
     */
    public KeyProducerIdUnknownException(@Nullable String id) {
        super(MESSAGE_PREFIX + id);
        this.id = id;
    }

    /**
     * Creates a new exception for the unknown id, chaining a cause.
     *
     * @param id    the unknown key-producer id; returned by {@link #getId()}
     * @param cause the underlying cause
     */
    public KeyProducerIdUnknownException(@Nullable String id, Throwable cause) {
        super(MESSAGE_PREFIX + id, cause);
        this.id = id;
    }

    /**
     * Returns the unresolvable id that caused the exception.
     *
     * @return the unresolvable id that caused the exception
     */
    public @Nullable String getId() {
        return id;
    }
}
