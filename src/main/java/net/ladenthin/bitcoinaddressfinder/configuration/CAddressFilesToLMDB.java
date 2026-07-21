// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the {@code AddressFilesToLMDB} command.
 */
@ToString
@EqualsAndHashCode
public class CAddressFilesToLMDB {

    /** Creates a new {@link CAddressFilesToLMDB}. */
    public CAddressFilesToLMDB() {}

    /**
     * The list of addresses files which should be read.
     */
    public List<String> addressesFiles = new ArrayList<>();

    /**
     * Number of parallel file-reader threads. File reading and parsing are decoupled from the LMDB
     * write: reader threads parse address files and hand entries to a single writer (LMDB is a
     * single-writer store), so this only parallelises the CPU/IO-bound reading side.
     *
     * <p><b>{@code 1} (default) preserves the exact, deterministic import order</b> — files are read
     * in list order, lines in file order, and written in that same order, identical to the previous
     * single-threaded behaviour. This matters when {@code lmdbConfigurationWrite.useStaticAmount} is
     * {@code false}: for an address that appears in more than one file the last write wins, so the
     * stored amount depends on the order.
     *
     * <p><b>{@code 2} or more reads files in parallel</b>, so the write order — and therefore the
     * winning amount for duplicate addresses — becomes non-deterministic. This is harmless when
     * {@code useStaticAmount} is {@code true} (every address is stored with the same static amount
     * regardless of order); when it is {@code false} a warning is logged. The set of imported
     * addresses is the same either way.
     */
    public int threads = 1;

    /**
     * The configuration to write a LMDB database.
     */
    public @Nullable CLMDBConfigurationWrite lmdbConfigurationWrite;
}
