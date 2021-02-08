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

import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.Coin;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import net.ladenthin.bitcoinaddressfinder.AddressTxtLine;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.ByteConversion;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFileOutputFormat;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import org.apache.commons.codec.binary.Hex;
import org.lmdbjava.BufferProxy;
import org.lmdbjava.ByteBufferProxy;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.Env.create;
import org.lmdbjava.EnvInfo;

public class LMDBPersistence implements Persistence {

    private static final String DB_NAME_HASH160_TO_COINT = "hash160toCoin";
    private static final int DB_COUNT = 1;

    private final PersistenceUtils persistenceUtils;
    private final CLMDBConfigurationWrite lmdbConfigurationWrite;
    private final CLMDBConfigurationReadOnly lmdbConfigurationReadOnly;
    private final KeyUtility keyUtility;
    private Env<ByteBuffer> env;
    private Dbi<ByteBuffer> lmdb_h160ToAmount;
    private long increasedCounter = 0;
    private long increasedSum = 0;

    public LMDBPersistence(CLMDBConfigurationWrite lmdbConfigurationWrite, PersistenceUtils persistenceUtils) {
        this.lmdbConfigurationReadOnly = null;
        this.lmdbConfigurationWrite = lmdbConfigurationWrite;
        this.persistenceUtils = persistenceUtils;
        this.keyUtility = new KeyUtility(persistenceUtils.networkParameters, new ByteBufferUtility(true));
    }

    public LMDBPersistence(CLMDBConfigurationReadOnly lmdbConfigurationReadOnly, PersistenceUtils persistenceUtils) {
        this.lmdbConfigurationReadOnly = lmdbConfigurationReadOnly;
        lmdbConfigurationWrite = null;
        this.persistenceUtils = persistenceUtils;
        this.keyUtility = new KeyUtility(persistenceUtils.networkParameters, new ByteBufferUtility(true));
    }
    
    @Override
    public void init() {
        if (lmdbConfigurationWrite != null) {
            // -Xmx10G -XX:MaxDirectMemorySize=5G
            // We always need an Env. An Env owns a physical on-disk storage file. One
            // Env can store many different databases (ie sorted maps).
            File lmdbDirectory = new File(lmdbConfigurationWrite.lmdbDirectory);
            lmdbDirectory.mkdirs();

            BufferProxy<ByteBuffer> bufferProxy = getBufferProxyByUseProxyOptimal(lmdbConfigurationWrite.useProxyOptimal);

            env = create(bufferProxy)
                    // LMDB also needs to know how large our DB might be. Over-estimating is OK.
                    .setMapSize(new ByteConversion().mibToBytes(lmdbConfigurationWrite.initialMapSizeInMiB))
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
        } else if (lmdbConfigurationReadOnly != null) {
            BufferProxy<ByteBuffer> bufferProxy = getBufferProxyByUseProxyOptimal(lmdbConfigurationReadOnly.useProxyOptimal);
            env = create(bufferProxy).setMaxDbs(DB_COUNT).open(new File(lmdbConfigurationReadOnly.lmdbDirectory), EnvFlags.MDB_RDONLY_ENV, EnvFlags.MDB_NOLOCK);
            lmdb_h160ToAmount = env.openDbi(DB_NAME_HASH160_TO_COINT);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * https://github.com/lmdbjava/lmdbjava/wiki/Buffers
     *
     * @param useProxyOptimal
     * @return
     */
    private BufferProxy<ByteBuffer> getBufferProxyByUseProxyOptimal(boolean useProxyOptimal) {
        if (useProxyOptimal) {
            return ByteBufferProxy.PROXY_OPTIMAL;
        } else {
            return ByteBufferProxy.PROXY_SAFE;
        }
    }

    @Override
    public void close() {
        lmdb_h160ToAmount.close();
    }

    @Override
    public Coin getAmount(ByteBuffer hash160) {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer byteBuffer = lmdb_h160ToAmount.get(txn, hash160);
            txn.close();

            return getCoinFromByteBuffer(byteBuffer);
        }
    }
    
    private Coin getCoinFromByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer != null) {
            if (byteBuffer.capacity() == 0) {
                return Coin.ZERO;
            } else {
                return Coin.valueOf(byteBuffer.getLong());
            }
        } else {
            return Coin.ZERO;
        }
    }

    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer byteBuffer = lmdb_h160ToAmount.get(txn, hash160);
            txn.close();
            return byteBuffer != null;
        }
    }

    @Override
    public void writeAllAmountsToAddressFile(File file, CAddressFileOutputFormat addressFileOutputFormat) throws IOException {
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            try (CursorIterable<ByteBuffer> iterable = lmdb_h160ToAmount.iterate(txn, KeyRange.all())) {
                try (FileWriter writer = new FileWriter(file)) {
                    for (final CursorIterable.KeyVal<ByteBuffer> kv : iterable) {
                        ByteBuffer addressAsByteBuffer = kv.key();
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
                                line = address.toBase58() + AddressTxtLine.COMMA + coin.getValue() + System.lineSeparator();
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
     * If an {@link org.lmdbjava.Env.MapFullException} was thrown during a put. The map might be increased if configured.
     * The increase value needs to be high enough. Otherwise the next put fails nevertheless.
     */
    private void putNewAmountWithAutoIncrease(ByteBuffer hash160, Coin amount) {
        try {
            putNewAmountUnsafe(hash160, amount);
        } catch (org.lmdbjava.Env.MapFullException e) {
            if (lmdbConfigurationWrite.increaseMapAutomatically == true) {
                increaseDatabaseSize(new ByteConversion().mibToBytes(lmdbConfigurationWrite.increaseSizeInMiB));
                /**
                 * It is possible that the exception will be thrown again, in this case increaseSizeInMiB should be changed and it's a configuration issue.
                 * See {@link CLMDBConfigurationWrite#increaseSizeInMiB}.
                 */
                putNewAmountUnsafe(hash160, amount);
            } else {
                throw e;
            }
        }
    }
    
    private void putNewAmountUnsafe(ByteBuffer hash160, Coin amount) {
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            if (lmdbConfigurationWrite.deleteEmptyAddresses && amount.isZero()) {
                lmdb_h160ToAmount.delete(txn, hash160);
            } else {
                long amountAsLong = amount.longValue();
                if (lmdbConfigurationWrite.useStaticAmount) {
                    amountAsLong = lmdbConfigurationWrite.staticAmount;
                }
                lmdb_h160ToAmount.put(txn, hash160, persistenceUtils.longToByteBufferDirect(amountAsLong));
            }
            txn.commit();
            txn.close();
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
    public String getStatsAsString() {
        return getStats().toString();
    }

    public Stat getStats() {
        return env.stat();
    }

    @Override
    public long count() {
        long count = 0;
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            try (CursorIterable<ByteBuffer> iterable = lmdb_h160ToAmount.iterate(txn, KeyRange.all())) {
                for (final CursorIterable.KeyVal<ByteBuffer> kv : iterable) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public long getDatabaseSize() {
        EnvInfo info = env.info();
        return info.mapSize;
    }

    @Override
    public void increaseDatabaseSize(long toIncrease) {
        increasedCounter++;
        increasedSum += toIncrease;
        long newSize = getDatabaseSize() + toIncrease;
        env.setMapSize(newSize);
    }

    @Override
    public long getIncreasedCounter() {
        return increasedCounter;
    }

    @Override
    public long getIncreasedSum() {
        return increasedSum;
    }
}
