// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configuration for opening the LMDB database in read-only mode.
 */
@ToString
@EqualsAndHashCode
public class CLMDBConfigurationReadOnly {

    /** Creates a new {@link CLMDBConfigurationReadOnly}. */
    public CLMDBConfigurationReadOnly() {}

    /**
     * The directory of the LMDB database.
     */
    public String lmdbDirectory = "";

    /**
     * Whether to use the optimal LMDB proxy implementation
     * (see {@link org.lmdbjava.ByteBufferProxy#PROXY_OPTIMAL}).
     */
    public boolean useProxyOptimal = true;

    /**
     * Whether to log detailed LMDB statistics when initializing the environment.
     * This causes a full iteration over the database, which can be expensive on large datasets,
     * but also results in the data being fully cached by the OS.
     */
    public boolean logStatsOnInit = false;

    /**
     * Whether to log LMDB statistics when closing the environment.
     */
    public boolean logStatsOnClose = false;

    /**
     * Selects the address-lookup chain placed in front of LMDB. See
     * {@link AddressLookupBackend} for the full trade-off matrix. Defaults to
     * {@link AddressLookupBackend#BLOOM} which matches the historical behaviour of
     * {@code useBloomFilter = true}.
     */
    public AddressLookupBackend addressLookupBackend = AddressLookupBackend.BLOOM;

    /**
     * The expected false positive probability (FPP) for the Bloom filter. Consulted only
     * when {@link #addressLookupBackend} is {@link AddressLookupBackend#BLOOM}.
     *
     * <p>Recommended values:
     * <ul>
     *   <li><b>0.01</b> &#x2013; ~1% false positives: high accuracy, higher memory usage</li>
     *   <li><b>0.05</b> &#x2013; ~5% false positives: balanced performance and memory</li>
     *   <li><b>0.1</b> or <b>0.2</b> &#x2013; 10&#x2013;20% false positives: very memory-efficient,
     *       suitable when occasional extra DB reads are acceptable</li>
     * </ul>
     */
    public double bloomFilterFpp = 0.1;

    /**
     * If true, {@code containsAddress(...)} will always return {@code false}, skipping both LMDB and in-memory lookups.
     *
     * This disables all address existence checks and is useful for performance benchmarking or dry-run scenarios
     * where address verification is intentionally omitted.
     */
    public boolean disableAddressLookup = false;
}
