// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configuration for the {@code LMDBDelta} command: write every address that is present in one of the
 * "other" databases but <b>not</b> in the reference database to a plaintext file.
 */
@ToString
@EqualsAndHashCode
public class CLMDBDelta {

    /** Creates a new {@link CLMDBDelta}. */
    public CLMDBDelta() {}

    /** Reference database — addresses present here are excluded from the delta. */
    public CLMDBConfigurationReadOnly referenceLmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();

    /** The other databases diffed against the reference; their union minus the reference is the delta. */
    public List<CLMDBConfigurationReadOnly> lmdbConfigurationReadOnlyList = new ArrayList<>();

    /**
     * Destination file. One Base58 P2PKH ({@code 1...}) address per line, reconstructed from the stored
     * hash160 (round-trip-safe: re-importing yields the same hash160). Re-importable via
     * {@code AddressFilesToLMDB}.
     */
    public String deltaAddressesFile = "";
}
