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
     * The configuration to write a LMDB database.
     */
    public @Nullable CLMDBConfigurationWrite lmdbConfigurationWrite;
}
