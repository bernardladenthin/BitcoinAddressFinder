// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.configuration;

import org.jspecify.annotations.Nullable;

public class CConfiguration {
    public CCommand command = CCommand.OpenCLInfo;
    
    public @Nullable CLMDBToAddressFile lmdbToAddressFile;
    public @Nullable CAddressFilesToLMDB addressFilesToLMDB;
    public @Nullable CFinder finder;
}
