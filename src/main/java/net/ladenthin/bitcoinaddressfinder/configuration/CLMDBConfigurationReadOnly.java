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
     * If true, all LMDB keys will be loaded into an in-memory {@link java.util.Set} cache during initialization.
     * 
     * This allows faster repeated {@code containsAddress(...)} lookups by avoiding LMDB access entirely.
     * 
     * Note: Increases RAM usage linearly with the number of keys. Recommended for use cases
     * with frequent key queries and when the dataset fits comfortably in memory.
     * 
     * Especially useful for high-performance OpenCL-based key generation where LMDB access can become a bottleneck.
     * A Java {@link java.util.HashSet} offers constant-time (O(1)) lookups, ensuring minimal overhead during address scanning.
     */
    public boolean loadToMemoryCacheOnInit = false;

    /**
     * If true, {@code containsAddress(...)} will always return {@code false}, skipping both LMDB and in-memory lookups.
     *
     * This disables all address existence checks and is useful for performance benchmarking or dry-run scenarios
     * where address verification is intentionally omitted.
     */
    public boolean disableAddressLookup = false;
}
