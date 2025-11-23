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
package net.ladenthin.bitcoinaddressfinder.persistence.lmdb;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.ByteConversion;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.SeparatorFormat;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFileOutputFormat;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.LegacyAddress;
import org.lmdbjava.BufferProxy;
import org.lmdbjava.ByteBufferProxy;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.Env.create;
import org.lmdbjava.EnvInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LMDBPersistence implements Persistence {

    private static final String DB_NAME_HASH160_TO_COINT = "hash160toCoin";
    private static final int DB_COUNT = 1;
    
    private final Logger logger = LoggerFactory.getLogger(LMDBPersistence.class);

    private final @NonNull PersistenceUtils persistenceUtils;
    private final @Nullable CLMDBConfigurationWrite lmdbConfigurationWrite;
    private final @Nullable CLMDBConfigurationReadOnly lmdbConfigurationReadOnly;
    private final @NonNull KeyUtility keyUtility;
    private @Nullable Env<ByteBuffer> env;
    private @Nullable Dbi<ByteBuffer> lmdb_h160ToAmount;
    private long increasedCounter = 0;
    private long increasedSum = 0;
    private @Nullable BloomFilter<byte[]> addressBloomFilter = null;


    public LMDBPersistence(CLMDBConfigurationWrite lmdbConfigurationWrite, PersistenceUtils persistenceUtils) {
        this.lmdbConfigurationReadOnly = null;
        this.lmdbConfigurationWrite = lmdbConfigurationWrite;
        this.persistenceUtils = persistenceUtils;
        this.keyUtility = new KeyUtility(persistenceUtils.network, new ByteBufferUtility(true));
    }

    public LMDBPersistence(CLMDBConfigurationReadOnly lmdbConfigurationReadOnly, PersistenceUtils persistenceUtils) {
        this.lmdbConfigurationReadOnly = lmdbConfigurationReadOnly;
        lmdbConfigurationWrite = null;
        this.persistenceUtils = persistenceUtils;
        this.keyUtility = new KeyUtility(persistenceUtils.network, new ByteBufferUtility(true));
    }
    
    @Override
    public void init() {
        if (lmdbConfigurationWrite != null) {
            initWritable();
        } else if (lmdbConfigurationReadOnly != null) {
            initReadOnly();
        } else {
            throw new IllegalArgumentException("Neither write nor read-only configuration provided.");
        }
        
        
        logStatsIfConfigured(true);
    }
    
    public void buildAddressBloomFilter() {
        logger.info("##### BEGIN: buildAddressBloomFilter #####");
        CLMDBConfigurationReadOnly localLmdbConfigurationReadOnly = Objects.requireNonNull(lmdbConfigurationReadOnly);
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);

        var localEnv = Objects.requireNonNull(env);
        // Attention: slow!
        long count = count();

        BloomFilter<byte[]> filter = BloomFilter.create(Funnels.byteArrayFunnel(), count, localLmdbConfigurationReadOnly.bloomFilterFpp);
        long inserted = 0;

        try (Txn<ByteBuffer> txn = localEnv.txnRead()) {
            try (CursorIterable<ByteBuffer> iterable = localLmdb_h160ToAmount.iterate(txn, KeyRange.all())) {
                for (CursorIterable.KeyVal<ByteBuffer> kv : iterable) {
                    ByteBuffer key = kv.key();
                    byte[] keyBytes = new byte[key.remaining()];
                    key.get(keyBytes);
                    key.rewind();
                    filter.put(keyBytes);
                    inserted++;
                }
            }
        }

        addressBloomFilter = filter;
        long size = getApproximateSizeBytes(filter);
        logger.info("Inserted {} addresses into BloomFilter with size of {}", inserted, formatSize(size));
        logger.info("##### END: buildAddressBloomFilter #####");
    }

    public void unloadBloomFilter() {
        addressBloomFilter = null;
    }
    
    private void initReadOnly() {
        CLMDBConfigurationReadOnly localLmdbConfigurationReadOnly = Objects.requireNonNull(lmdbConfigurationReadOnly);
        BufferProxy<ByteBuffer> bufferProxy = getBufferProxyByUseProxyOptimal(localLmdbConfigurationReadOnly.useProxyOptimal);
        env = create(bufferProxy).setMaxDbs(DB_COUNT).open(new File(localLmdbConfigurationReadOnly.lmdbDirectory), EnvFlags.MDB_RDONLY_ENV, EnvFlags.MDB_NOLOCK);
        lmdb_h160ToAmount = env.openDbi(DB_NAME_HASH160_TO_COINT);
        
        if (localLmdbConfigurationReadOnly.useBloomFilter) {
            buildAddressBloomFilter();
        }
    }

    private void initWritable() {
        CLMDBConfigurationWrite localLmdbConfigurationWrite = Objects.requireNonNull(lmdbConfigurationWrite);

        // -Xmx10G -XX:MaxDirectMemorySize=5G
        // We always need an Env. An Env owns a physical on-disk storage file. One
        // Env can store many different databases (ie sorted maps).
        File lmdbDirectory = new File(localLmdbConfigurationWrite.lmdbDirectory);
        lmdbDirectory.mkdirs();
        
        BufferProxy<ByteBuffer> bufferProxy = getBufferProxyByUseProxyOptimal(localLmdbConfigurationWrite.useProxyOptimal);
        
        env = create(bufferProxy)
                // LMDB also needs to know how large our DB might be. Over-estimating is OK.
                .setMapSize(new ByteConversion().mibToBytes(localLmdbConfigurationWrite.initialMapSizeInMiB))
                // LMDB also needs to know how many DBs (Dbi) we want to store in this Env.
                .setMaxDbs(DB_COUNT)
                // Now let's open the Env. The same path can be concurrently opened and
                // used in different processes, but do not open the same path twice in
                // the same process at the same time.
                
                //https://github.com/kentnl/CHI-Driver-LMDB
                .open(lmdbDirectory, EnvFlags.MDB_NOSYNC, EnvFlags.MDB_NOMETASYNC, EnvFlags.MDB_WRITEMAP, EnvFlags.MDB_MAPASYNC);
        // We need a Dbi for each DB. A Dbi roughly equates to a sorted map. The
        // MDB_CREATE flag causes the DB to be created if it doesn't already exist.
        lmdb_h160ToAmount = env.openDbi(DB_NAME_HASH160_TO_COINT, MDB_CREATE);
    }

    /**
     * Returns the appropriate ByteBuffer proxy implementation based on the optimization preference.
     * <p>
     * LMDB requires a proxy to handle direct ByteBuffers. Two implementations are available:
     * - PROXY_OPTIMAL: Uses JNI and unsafe operations for better performance but requires specific JVM permissions
     * - PROXY_SAFE: Uses pure Java implementation that's more portable but slower
     * <p>
     * For more details, see: https://github.com/lmdbjava/lmdbjava/wiki/Buffers
     *
     * @param useProxyOptimal true to use the optimized JNI implementation (PROXY_OPTIMAL),
     *                        false to use the safe Java implementation (PROXY_SAFE)
     * @return the selected {@link BufferProxy} implementation for ByteBuffer operations
     */
    private BufferProxy<ByteBuffer> getBufferProxyByUseProxyOptimal(boolean useProxyOptimal) {
        if (useProxyOptimal) {
            return ByteBufferProxy.PROXY_OPTIMAL;
        } else {
            return ByteBufferProxy.PROXY_SAFE;
        }
    }
    
    private void logStatsIfConfigured(boolean onInit) {
        if (isLoggingEnabled(lmdbConfigurationWrite, onInit) || isLoggingEnabled(lmdbConfigurationReadOnly, onInit)) {
            logStats();
        }
    }

    private boolean isLoggingEnabled(@Nullable CLMDBConfigurationReadOnly config, boolean onInit) {
        return config != null && (onInit ? config.logStatsOnInit : config.logStatsOnClose);
    }

    @Override
    public void close() {
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        logStatsIfConfigured(false);
        localLmdb_h160ToAmount.close();
        localEnv.close();
    }
    
    @Override
    public boolean isClosed() {
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);
        return localEnv.isClosed();
    }

    @Override
    public Coin getAmount(ByteBuffer hash160) {
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        try (Txn<ByteBuffer> txn = localEnv.txnRead()) {
            ByteBuffer byteBuffer = localLmdb_h160ToAmount.get(txn, hash160);
            return getCoinFromByteBuffer(byteBuffer);
        }
    }
    
    private Coin getCoinFromByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer == null || byteBuffer.capacity() == 0) {
            return Coin.ZERO;
        }
        return Coin.valueOf(byteBuffer.getLong());
    }

    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        CLMDBConfigurationReadOnly localLmdbConfigurationReadOnly = Objects.requireNonNull(lmdbConfigurationReadOnly);
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        if (localLmdbConfigurationReadOnly.disableAddressLookup) {
            return false;
        }
        
        byte[] hash160AsByteArray = new byte[hash160.remaining()];
        hash160.get(hash160AsByteArray);
        hash160.rewind();
        
        // Use Bloom filter if available for fast pre-check
        if (addressBloomFilter != null) {
            boolean mightContain = addressBloomFilter.mightContain(hash160AsByteArray);
            if (!mightContain) {
                return false; // definitely not present
            }
            // Possibly in DB, proceed to verify
        }
        
        // Perform LMDB lookup (always happens if no Bloom filter is present)
        try (Txn<ByteBuffer> txn = localEnv.txnRead()) {
            ByteBuffer byteBuffer = localLmdb_h160ToAmount.get(txn, hash160);
            return byteBuffer != null;
        }
    }

    @Override
    public void writeAllAmountsToAddressFile(File file, CAddressFileOutputFormat addressFileOutputFormat, AtomicBoolean shouldRun) throws IOException {
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        try (Txn<ByteBuffer> txn = localEnv.txnRead()) {
            try (CursorIterable<ByteBuffer> iterable = localLmdb_h160ToAmount.iterate(txn, KeyRange.all())) {
                try (FileWriter writer = new FileWriter(file)) {
                    for (final CursorIterable.KeyVal<ByteBuffer> kv : iterable) {
                        if (!shouldRun.get()) {
                            return;
                        }
                        ByteBuffer addressAsByteBuffer = kv.key();
                        if(logger.isTraceEnabled()) {
                            String hexFromByteBuffer = new ByteBufferUtility(false).getHexFromByteBuffer(addressAsByteBuffer);
                            logger.trace("Process address: " + hexFromByteBuffer);
                        }
                        LegacyAddress address = keyUtility.byteBufferToAddress(addressAsByteBuffer);
                        final String line;
                        switch(addressFileOutputFormat) {
                            case HexHash:
                                line = Hex.encodeHexString(address.getHash()) + System.lineSeparator();
                                break;
                            case FixedWidthBase58BitcoinAddress:
                                line = String.format("%-34s", address.toBase58()) + System.lineSeparator();
                                break;
                            case DynamicWidthBase58BitcoinAddressWithAmount:
                                ByteBuffer value = kv.val();
                                Coin coin = getCoinFromByteBuffer(value);
                                line = address.toBase58() + SeparatorFormat.COMMA.getSymbol() + coin.getValue() + System.lineSeparator();
                                break;
                            default:
                                throw new IllegalArgumentException("Unknown addressFileOutputFormat: " + addressFileOutputFormat);
                        }
                        writer.write(line);
                    }
                }
            }
        }
    }

    @Override
    public void putAllAmounts(Map<ByteBuffer, Coin> amounts) throws IOException {
        for (Map.Entry<ByteBuffer, Coin> entry : amounts.entrySet()) {
            ByteBuffer hash160 = entry.getKey();
            Coin coin = entry.getValue();
            putNewAmount(hash160, coin);
        }
    }

    @Override
    public void changeAmount(ByteBuffer hash160, Coin amountToChange) {
        Coin valueInDB = getAmount(hash160);
        Coin toWrite = valueInDB.add(amountToChange);
        putNewAmount(hash160, toWrite);
    }

    @Override
    public void putNewAmount(ByteBuffer hash160, Coin amount) {
        putNewAmountWithAutoIncrease(hash160, amount);
    }

    /**
     * Inserts a value into LMDB and optionally grows the map if it is full.
     *
     * <p>If {@link org.lmdbjava.Env.MapFullException} occurs and
     * {@link CLMDBConfigurationWrite#increaseMapAutomatically} is enabled,
     * the map size is increased by {@link CLMDBConfigurationWrite#increaseSizeInMiB}
     * and the insert is retried once.
     *
     * <p>If the second attempt also fails, the configured increase size is too small.
     * If automatic growth is disabled, the original exception is rethrown.
     */
    private void putNewAmountWithAutoIncrease(ByteBuffer hash160, Coin amount) {
        CLMDBConfigurationWrite localLmdbConfigurationWrite = Objects.requireNonNull(lmdbConfigurationWrite);

        try {
            putNewAmountUnsafe(hash160, amount);
        } catch (org.lmdbjava.Env.MapFullException e) {
            if (localLmdbConfigurationWrite.increaseMapAutomatically) {
                increaseDatabaseSize(new ByteConversion().mibToBytes(lmdbConfigurationWrite.increaseSizeInMiB));
                // It is possible that the exception will be thrown again, in this case increaseSizeInMiB should be changed and it's a configuration issue.
                // See {@link CLMDBConfigurationWrite#increaseSizeInMiB}.
                putNewAmountUnsafe(hash160, amount);
            } else {
                throw e;
            }
        }
    }
    
    private void putNewAmountUnsafe(ByteBuffer hash160, Coin amount) {
        CLMDBConfigurationWrite localLmdbConfigurationWrite = Objects.requireNonNull(lmdbConfigurationWrite);
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        try (Txn<ByteBuffer> txn = localEnv.txnWrite()) {
            if (localLmdbConfigurationWrite.deleteEmptyAddresses && amount.isZero()) {
                localLmdb_h160ToAmount.delete(txn, hash160);
            } else {
                long amountAsLong = amount.longValue();
                if (localLmdbConfigurationWrite.useStaticAmount) {
                    amountAsLong = lmdbConfigurationWrite.staticAmount;
                }
                localLmdb_h160ToAmount.put(txn, hash160, persistenceUtils.longToByteBufferDirect(amountAsLong));
            }
            txn.commit();
        }
    }

    @Override
    public Coin getAllAmountsFromAddresses(List<ByteBuffer> hash160s) {
        Coin allAmounts = Coin.ZERO;
        for (ByteBuffer hash160 : hash160s) {
            allAmounts = allAmounts.add(getAmount(hash160));
        }
        return allAmounts;
    }

    @Override
    public long count() {
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        long count = 0;
        try (Txn<ByteBuffer> txn = localEnv.txnRead()) {
            try (CursorIterable<ByteBuffer> iterable = localLmdb_h160ToAmount.iterate(txn, KeyRange.all())) {
                for (final CursorIterable.KeyVal<ByteBuffer> kv : iterable) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public long getDatabaseSize() {
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        EnvInfo info = localEnv.info();
        return info.mapSize;
    }

    @Override
    public void increaseDatabaseSize(long toIncrease) {
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        increasedCounter++;
        increasedSum += toIncrease;
        long newSize = getDatabaseSize() + toIncrease;
        localEnv.setMapSize(newSize);
    }

    @Override
    public long getIncreasedCounter() {
        return increasedCounter;
    }

    @Override
    public long getIncreasedSum() {
        return increasedSum;
    }
    
    @Override
    public void logStats() {
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        logger.info("##### BEGIN: LMDB stats #####");
        logger.info("... this may take a lot of time ...");
        logger.info("DatabaseSize: " + new ByteConversion().bytesToMib(getDatabaseSize()) + " MiB");
        logger.info("IncreasedCounter: " + getIncreasedCounter());
        logger.info("IncreasedSum: " + new ByteConversion().bytesToMib(getIncreasedSum()) + " MiB");
        logger.info("Stat: " + localEnv.stat());
        // Attention: slow!
        long count = count();
        logger.info("LMDB contains " + count + " unique entries.");
        logger.info("##### END: LMDB stats #####");
    }

    public static long getApproximateSizeBytes(BloomFilter<?> bloomFilter) {
        try {
            // Access private field: bits
            Field bitsField = BloomFilter.class.getDeclaredField("bits");
            bitsField.setAccessible(true);
            Object bits = bitsField.get(bloomFilter);

            // Access internal AtomicLongArray: data
            Field dataField = bits.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            AtomicLongArray data = (AtomicLongArray) dataField.get(bits);

            return (long) data.length() * Long.BYTES; // 8 bytes per long
        } catch (Exception e) {
            throw new RuntimeException("Failed to estimate BloomFilter size", e);
        }
    }

    public static String formatSize(long sizeInBytes) {
        if (sizeInBytes >= 1024 * 1024) {
            return String.format("%.2f MB", sizeInBytes / 1024.0 / 1024.0);
        } else if (sizeInBytes >= 1024) {
            return String.format("%.2f KB", sizeInBytes / 1024.0);
        } else {
            return sizeInBytes + " bytes";
        }
    }
}
