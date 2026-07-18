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
     * Whether to open the environment with {@code MDB_NORDAHEAD}, disabling the OS read-ahead the
     * kernel would otherwise apply to the memory-mapped database file.
     *
     * <p><b>Set this when the database is larger than available RAM.</b> The store is written in
     * random hash160 order, so LMDB B-tree pages that are adjacent in <em>key</em> order are
     * scattered in <em>file</em> order. A key-ordered cursor walk — which is exactly what every
     * in-RAM backend performs while populating — therefore jumps across the whole file, and OS
     * read-ahead drags in neighbouring pages that are evicted again before their turn arrives. Once
     * RAM is exhausted this degenerates into thrashing: measured while building over the 61&nbsp;GB
     * / 1.377&nbsp;B-entry database, the NVMe ran 86&nbsp;% busy delivering 332&nbsp;MB/s while only
     * ~19&nbsp;MB/s of that was useful entry data — roughly <b>17&times; read amplification</b>.
     *
     * <p><b>Leave this off when the database fits in RAM.</b> For a store the OS can cache in full
     * (for example the ~5.8&nbsp;GB light database on a 64&nbsp;GB machine) read-ahead is a genuine
     * win, and disabling it is a pessimisation. This is why the flag is configurable rather than
     * always-on.
     *
     * <p>Defaults to {@code false}, preserving the previous behaviour exactly.
     */
    public boolean useNoReadAhead = false;

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
     * {@link AddressLookupBackend#LMDB_ONLY}: no in-RAM filter, LMDB stays open and answers
     * every lookup exactly. This is the safest default — an exact backend can never report a
     * false positive as a hit — and the in-RAM filters ({@code BLOOM}, {@code BINARY_FUSE_8},
     * the exact snapshots) are opt-in latency optimisations. The GPU pre-filter
     * ({@code producerOpenCL.enableGpuFilter}) is independent of this setting and works with the
     * {@code LMDB_ONLY} default.
     */
    public AddressLookupBackend addressLookupBackend = AddressLookupBackend.LMDB_ONLY;

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
