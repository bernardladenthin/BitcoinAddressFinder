// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Root configuration object loaded from the JSON config file.
 */
@ToString
public class CConfiguration {

    /** Creates a new {@link CConfiguration}. */
    public CConfiguration() {}

    /** Operation to perform when starting the tool. */
    public CCommand command = CCommand.OpenCLInfo;

    /** Configuration for the {@code LMDBToAddressFile} command. */
    public @Nullable CLMDBToAddressFile lmdbToAddressFile;
    /** Configuration for the {@code AddressFilesToLMDB} command. */
    public @Nullable CAddressFilesToLMDB addressFilesToLMDB;
    /** Configuration for the {@code Find} command. */
    public @Nullable CFinder finder;
}
