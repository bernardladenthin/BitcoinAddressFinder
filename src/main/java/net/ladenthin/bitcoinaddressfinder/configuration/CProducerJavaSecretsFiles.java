// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configuration for the producer that reads secrets from one or more files.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CProducerJavaSecretsFiles extends CProducerJava {
    /** Creates a new {@link CProducerJavaSecretsFiles}. */
    public CProducerJavaSecretsFiles() {}

    /**
     * The list of secrets files to read. Each entry is a file path resolved relative to the
     * working directory; every line in a file is one secret, parsed according to
     * {@link #secretFormat}.
     */
    public List<String> secretsFiles = new ArrayList<>();

    /**
     * The line format of every file in {@link #secretsFiles}.
     */
    public CSecretFormat secretFormat = CSecretFormat.STRING_SHA256;
}
