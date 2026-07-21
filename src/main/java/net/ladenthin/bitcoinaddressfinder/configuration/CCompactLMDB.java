// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configuration for the {@code CompactLMDB} command: write a compacted copy of an existing LMDB
 * database using LMDB's {@code MDB_CP_COMPACT}.
 */
@ToString
@EqualsAndHashCode
public class CCompactLMDB {

    /** Creates a new {@link CCompactLMDB}. */
    public CCompactLMDB() {}

    /** LMDB read-only configuration identifying the source database ({@code lmdbDirectory}). */
    public CLMDBConfigurationReadOnly lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();

    /**
     * Destination directory for the compacted copy. It is created if missing; the compacted database is
     * written as {@code targetDirectory/data.mdb}. Must differ from the source directory. An existing
     * {@code data.mdb} in the target is replaced.
     */
    public String targetDirectory = "";
}
