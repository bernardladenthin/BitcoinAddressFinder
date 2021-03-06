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
     * The LMDB proxy (see {@link org.lmdbjava.ByteBufferProxy}).
     */
    public boolean useProxyOptimal = true;
    
    /**
     * Log the lmdb stats on init. This enforces a read of the whole database. It's in memory afterwards. This option may take a 
     */
    public boolean logStatsOnInit = false;
    
    /**
     * Log the lmdb stats on close.
     */
    public boolean logStatsOnClose = false;
}
