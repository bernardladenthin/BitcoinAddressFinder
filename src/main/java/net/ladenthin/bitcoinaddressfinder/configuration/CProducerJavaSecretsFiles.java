// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the producer that reads secrets from one or more files.
 */
public class CProducerJavaSecretsFiles extends CProducerJava {
    /** Creates a new {@link CProducerJavaSecretsFiles}. */
    public CProducerJavaSecretsFiles() {}

    /**
     * The list of strings files which should be read.
     */
    public List<String> files = new ArrayList<>();

    /**
     * The format of each line in the files.
     */
    public CSecretFormat secretFormat = CSecretFormat.STRING_DO_SHA256;
}
