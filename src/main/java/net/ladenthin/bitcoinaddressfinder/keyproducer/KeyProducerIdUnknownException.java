// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import org.jspecify.annotations.Nullable;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.Finder} when a
 * configured {@code keyProducerId} cannot be resolved to a registered producer.
 * This typically indicates a misconfiguration: the requested producer type is
 * not supported or the id was misspelled.
 */
public class KeyProducerIdUnknownException extends RuntimeException {

    /** The unresolvable key-producer id supplied at construction time. */
    private final @Nullable String id;

    /**
     * Creates a new exception for the unknown id.
     *
     * @param id the unknown key-producer id
     */
    public KeyProducerIdUnknownException(@Nullable String id) {
        super("Key producer id is unknown: " + id);
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
