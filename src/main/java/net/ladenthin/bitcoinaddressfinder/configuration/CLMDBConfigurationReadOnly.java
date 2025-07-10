// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.configuration;

public class CLMDBConfigurationReadOnly {
    /**
     * The directory of the LMDB database.
     */
    public String lmdbDirectory;
    
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
     * If set to true, the LMDB database will load a Bloom filter into memory on initialization.
     * This filter is used to perform fast probabilistic membership checks for hash160 addresses.
     * 
     * The Bloom filter does not store the full set of addresses in memory, but instead uses a compact bit array
     * and multiple hash functions to determine whether an address might exist in the database.
     * 
     * Advantages:
     *  - Extremely fast `containsAddress()` lookups (O(1))
     *  - Low memory consumption even for millions or billions of entries
     * 
     * Tradeoff:
     *  - False positives are possible: an address might be reported as present, even if it is not.
     *    This may lead to unnecessary database lookups, but never to false negatives.
     * 
     * If this is set to false, LMDB will perform direct queries for each address using transactions.
     */
    public boolean useBloomFilter = true;
    
    /**
     * The expected false positive probability (FPP) for the Bloom filter.
     * <p>
     * Lower values reduce the chance of false positives but increase memory usage.
     * Higher values save memory but allow more false positives, potentially causing unnecessary LMDB lookups.
     * <p>
     * Recommended values:
     * <ul>
     *   <li><b>0.01</b> – ~1% false positives: High accuracy, higher memory usage</li>
     *   <li><b>0.05</b> – ~5% false positives: Balanced performance and memory</li>
     *   <li><b>0.1</b> or <b>0.2</b> – 10–20% false positives: Very memory-efficient, suitable if occasional extra DB reads are acceptable</li>
     * </ul>
     * <p>
     * This value is only used if {@link #useBloomFilter} is <code>true</code>.
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
