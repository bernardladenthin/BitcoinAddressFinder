// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.lmdb;

import static org.lmdbjava.DbiFlags.MDB_CREATE;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFileOutputFormat;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.io.SeparatorFormat;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.ByteConversion;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.LegacyAddress;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lmdbjava.BufferProxy;
import org.lmdbjava.ByteBufferProxy;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LMDB-backed implementation of {@link Persistence}. Pure backend: holds no accelerator
 * state itself. Accelerator chains (Bloom, HashSet, SortedArray) are built externally
 * via the {@code populateFrom} factories on each accelerator class, consuming this
 * instance through {@link AddressIterable}.
 */
@ToString
public class LMDBPersistence implements Persistence, AddressIterable {

    private static final String DB_NAME_HASH160_TO_COINT = "hash160toCoin";
    private static final int DB_COUNT = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(LMDBPersistence.class);

    private final @NonNull PersistenceUtils persistenceUtils;
    private final @Nullable CLMDBConfigurationWrite lmdbConfigurationWrite;
    private final @Nullable CLMDBConfigurationReadOnly lmdbConfigurationReadOnly;
    private final @NonNull KeyUtility keyUtility;

    // LMDB native handle wrappers — toString is just the native pointer identity.
    @ToString.Exclude
    private @Nullable Env<ByteBuffer> env;

    @ToString.Exclude
    private @Nullable Dbi<ByteBuffer> lmdb_h160ToAmount;

    private final java.util.concurrent.atomic.AtomicLong increasedCounter =
            new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong increasedSum = new java.util.concurrent.atomic.AtomicLong(0);
    /**
     * Creates a new writable LMDB-backed persistence instance.
     *
     * @param lmdbConfigurationWrite the writable LMDB configuration
     * @param persistenceUtils       persistence helper providing the network
     */
    public LMDBPersistence(CLMDBConfigurationWrite lmdbConfigurationWrite, PersistenceUtils persistenceUtils) {
        this.lmdbConfigurationReadOnly = null;
        this.lmdbConfigurationWrite = lmdbConfigurationWrite;
        this.persistenceUtils = persistenceUtils;
        this.keyUtility = new KeyUtility(persistenceUtils.network, new ByteBufferUtility(true));
    }

    /**
     * Creates a new read-only LMDB-backed persistence instance.
     *
     * @param lmdbConfigurationReadOnly the read-only LMDB configuration
     * @param persistenceUtils          persistence helper providing the network
     */
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
            throw new IllegalArgumentException(getClass().getSimpleName()
                    + ".init() requires either lmdbConfigurationWrite or"
                    + " lmdbConfigurationReadOnly to be set; both are null"
                    + " (check the <persistence> block in your finder configuration JSON)");
        }

        logStatsIfConfigured(true);
    }

    private void initReadOnly() {
        CLMDBConfigurationReadOnly localLmdbConfigurationReadOnly = Objects.requireNonNull(lmdbConfigurationReadOnly);
        BufferProxy<ByteBuffer> bufferProxy =
                getBufferProxyByUseProxyOptimal(localLmdbConfigurationReadOnly.useProxyOptimal);
        // MDB_NORDAHEAD is opt-in: it helps when the database exceeds RAM (a key-ordered walk over a
        // randomly-keyed store is physically scattered, so OS read-ahead fetches pages that are
        // evicted before use) and hurts when the database fits, where read-ahead is a genuine win.
        // See CLMDBConfigurationReadOnly#useNoReadAhead for the measured amplification figures.
        EnvFlags[] envFlags = localLmdbConfigurationReadOnly.useNoReadAhead
                ? new EnvFlags[] {EnvFlags.MDB_RDONLY_ENV, EnvFlags.MDB_NOLOCK, EnvFlags.MDB_NORDAHEAD}
                : new EnvFlags[] {EnvFlags.MDB_RDONLY_ENV, EnvFlags.MDB_NOLOCK};
        if (localLmdbConfigurationReadOnly.useNoReadAhead) {
            LOGGER.info("Opening LMDB read-only with MDB_NORDAHEAD (OS read-ahead disabled).");
        }
        env = Env.create(bufferProxy)
                .setMaxDbs(DB_COUNT)
                .open(new File(localLmdbConfigurationReadOnly.lmdbDirectory), envFlags);
        lmdb_h160ToAmount = env.openDbi(DB_NAME_HASH160_TO_COINT);
    }

    private void initWritable() {
        CLMDBConfigurationWrite localLmdbConfigurationWrite = Objects.requireNonNull(lmdbConfigurationWrite);

        // -Xmx10G -XX:MaxDirectMemorySize=5G
        // We always need an Env. An Env owns a physical on-disk storage file. One
        // Env can store many different databases (ie sorted maps).
        File lmdbDirectory = new File(localLmdbConfigurationWrite.lmdbDirectory);
        if (!lmdbDirectory.mkdirs() && !lmdbDirectory.isDirectory()) {
            throw new IllegalStateException("Failed to create LMDB directory: " + lmdbDirectory);
        }

        BufferProxy<ByteBuffer> bufferProxy =
                getBufferProxyByUseProxyOptimal(localLmdbConfigurationWrite.useProxyOptimal);

        env = Env.create(bufferProxy)
                // LMDB also needs to know how large our DB might be. Over-estimating is OK.
                .setMapSize(new ByteConversion().mibToBytes(localLmdbConfigurationWrite.initialMapSizeInMiB))
                // LMDB also needs to know how many DBs (Dbi) we want to store in this Env.
                .setMaxDbs(DB_COUNT)
                // Now let's open the Env. The same path can be concurrently opened and
                // used in different processes, but do not open the same path twice in
                // the same process at the same time.

                // https://github.com/kentnl/CHI-Driver-LMDB
                .open(
                        lmdbDirectory,
                        EnvFlags.MDB_NOSYNC,
                        EnvFlags.MDB_NOMETASYNC,
                        EnvFlags.MDB_WRITEMAP,
                        EnvFlags.MDB_MAPASYNC);
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
        // Null-safe on purpose: close() runs in the caller's finally block, so if init() threw
        // before these fields were assigned (e.g. an InaccessibleObjectException because the JVM was
        // launched without the required --add-opens), requireNonNull here would raise a fresh NPE
        // that MASKS the original failure. The operator would then see a NullPointerException from
        // close() instead of the real cause. Skip whatever was never opened and let the original
        // exception propagate.
        Dbi<ByteBuffer> localLmdb_h160ToAmount = lmdb_h160ToAmount;
        Env<ByteBuffer> localEnv = env;

        if (localLmdb_h160ToAmount == null || localEnv == null) {
            return;
        }

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

        try (Txn<ByteBuffer> txn = localEnv.txnRead()) {
            ByteBuffer byteBuffer = localLmdb_h160ToAmount.get(txn, hash160);
            return byteBuffer != null;
        }
    }

    /**
     * LMDB is queried directly on every {@code containsAddress} call through its live
     * {@code Env}, so the env (and its mmap) must stay open for as long as this instance
     * is used as the lookup. It is the backing storage itself, not a self-contained
     * in-memory snapshot, so it must not be closed after the lookup chain is wired.
     *
     * @return always {@code true}
     */
    @Override
    public boolean requiresBackend() {
        return true;
    }

    /**
     * Streams every hash160 stored in the database. Each emitted {@link ByteBuffer} is a
     * cursor-owned view; callers MUST copy bytes out before advancing the stream.
     *
     * <p>Closing the returned stream releases the LMDB cursor and the read transaction.
     */
    @Override
    public Stream<ByteBuffer> addresses() {
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);

        Txn<ByteBuffer> txn = localEnv.txnRead();
        CursorIterable<ByteBuffer> iterable;
        try {
            iterable = localLmdb_h160ToAmount.iterate(txn, KeyRange.all());
        } catch (RuntimeException e) {
            txn.close();
            throw e;
        }

        Stream<CursorIterable.KeyVal<ByteBuffer>> rawStream =
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterable.iterator(), 0), false);
        return rawStream.map(CursorIterable.KeyVal::key).onClose(() -> {
            iterable.close();
            txn.close();
        });
    }

    /**
     * Lower-overhead override of {@link AddressIterable#forEachAddress(Consumer)}: iterates a raw
     * LMDB {@link Cursor} directly, avoiding the {@code Stream}/{@code Spliterator}/{@code Iterator}
     * per-entry dispatch of {@link #addresses()}. This is the throughput path used when populating
     * the in-memory / GPU filters from a large database. The buffer passed to {@code action} is the
     * cursor's key view, valid only until the next advance — read it immediately, do not retain it.
     */
    @Override
    public void forEachAddress(Consumer<ByteBuffer> action) {
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        try (Txn<ByteBuffer> txn = localEnv.txnRead();
                Cursor<ByteBuffer> cursor = localLmdb_h160ToAmount.openCursor(txn)) {
            boolean hasNext = cursor.first();
            while (hasNext) {
                action.accept(cursor.key());
                hasNext = cursor.next();
            }
        }
    }

    @Override
    public void writeAllAmountsToAddressFile(
            File file, CAddressFileOutputFormat addressFileOutputFormat, AtomicBoolean shouldRun) throws IOException {
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        try (Txn<ByteBuffer> txn = localEnv.txnRead()) {
            try (CursorIterable<ByteBuffer> iterable = localLmdb_h160ToAmount.iterate(txn, KeyRange.all())) {
                try (FileWriter writer = new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    for (final CursorIterable.KeyVal<ByteBuffer> kv : iterable) {
                        if (!shouldRun.get()) {
                            return;
                        }
                        ByteBuffer addressAsByteBuffer = kv.key();
                        if (LOGGER.isTraceEnabled()) {
                            String hexFromByteBuffer =
                                    new ByteBufferUtility(false).getHexFromByteBuffer(addressAsByteBuffer);
                            LOGGER.trace("Process address: " + hexFromByteBuffer);
                        }
                        LegacyAddress address = keyUtility.byteBufferToAddress(addressAsByteBuffer);
                        final String line =
                                switch (addressFileOutputFormat) {
                                    case HexHash -> Hex.encodeHexString(address.getHash()) + System.lineSeparator();
                                    case FixedWidthBase58BitcoinAddress ->
                                        String.format("%-34s", address.toBase58()) + System.lineSeparator();
                                    case DynamicWidthBase58BitcoinAddressWithAmount -> {
                                        ByteBuffer value = kv.val();
                                        Coin coin = getCoinFromByteBuffer(value);
                                        yield address.toBase58()
                                                + SeparatorFormat.COMMA.getSymbol()
                                                + coin.getValue()
                                                + System.lineSeparator();
                                    }
                                    default ->
                                        throw new IllegalArgumentException(
                                                "Unknown addressFileOutputFormat: " + addressFileOutputFormat);
                                };
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
                // It is possible that the exception will be thrown again, in this case increaseSizeInMiB should be
                // changed and it's a configuration issue.
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
            applyEntry(localLmdbConfigurationWrite, localLmdb_h160ToAmount, txn, hash160, amount);
            txn.commit();
        }
    }

    /**
     * Writes many entries in a <b>single</b> write transaction (one commit for the whole batch) instead
     * of one transaction per entry. Since LMDB commits are the dominant cost of a bulk import, this is
     * the primary import speedup. The two lists are parallel: entry {@code i} is
     * {@code (hash160s.get(i), amounts.get(i))}. Order is preserved. On a full map the whole batch is
     * retried after growing the map, matching {@link #putNewAmount(ByteBuffer, Coin)}.
     *
     * <p>Not thread-safe: LMDB permits only one write transaction at a time, so a single writer thread
     * must own all calls.
     *
     * @param hash160s the 20-byte hashes to write, in order
     * @param amounts  the amounts, aligned one-to-one with {@code hash160s}
     */
    public void putNewAmounts(List<ByteBuffer> hash160s, List<Coin> amounts) {
        if (hash160s.size() != amounts.size()) {
            throw new IllegalArgumentException(
                    "hash160s and amounts must have the same size: " + hash160s.size() + " != " + amounts.size());
        }
        CLMDBConfigurationWrite localLmdbConfigurationWrite = Objects.requireNonNull(lmdbConfigurationWrite);

        try {
            putNewAmountsUnsafe(hash160s, amounts);
        } catch (org.lmdbjava.Env.MapFullException e) {
            if (localLmdbConfigurationWrite.increaseMapAutomatically) {
                increaseDatabaseSize(new ByteConversion().mibToBytes(lmdbConfigurationWrite.increaseSizeInMiB));
                putNewAmountsUnsafe(hash160s, amounts);
            } else {
                throw e;
            }
        }
    }

    private void putNewAmountsUnsafe(List<ByteBuffer> hash160s, List<Coin> amounts) {
        CLMDBConfigurationWrite localLmdbConfigurationWrite = Objects.requireNonNull(lmdbConfigurationWrite);
        Dbi<ByteBuffer> localLmdb_h160ToAmount = Objects.requireNonNull(lmdb_h160ToAmount);
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        try (Txn<ByteBuffer> txn = localEnv.txnWrite()) {
            for (int i = 0; i < hash160s.size(); i++) {
                applyEntry(localLmdbConfigurationWrite, localLmdb_h160ToAmount, txn, hash160s.get(i), amounts.get(i));
            }
            txn.commit();
        }
    }

    /** Applies one hash160/amount to the open transaction (respecting deleteEmptyAddresses/useStaticAmount). */
    private void applyEntry(
            CLMDBConfigurationWrite config, Dbi<ByteBuffer> dbi, Txn<ByteBuffer> txn, ByteBuffer hash160, Coin amount) {
        if (config.deleteEmptyAddresses && amount.isZero()) {
            dbi.delete(txn, hash160);
        } else {
            long amountAsLong = config.useStaticAmount ? config.staticAmount : amount.longValue();
            dbi.put(txn, hash160, persistenceUtils.longToByteBufferDirect(amountAsLong));
        }
    }

    @Override
    public Coin sumAmountsForAddresses(List<ByteBuffer> hash160s) {
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

        // O(1): LMDB maintains the exact entry count in the database metadata (Stat.entries), so read
        // it directly instead of iterating every entry with a cursor. The cursor scan is a full pass
        // over the database — a few seconds on small databases, but tens of minutes on billion-entry
        // ones (and it ran once here just to size the fingerprint array before construction).
        try (Txn<ByteBuffer> txn = localEnv.txnRead()) {
            return localLmdb_h160ToAmount.stat(txn).entries;
        }
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

        increasedCounter.incrementAndGet();
        increasedSum.addAndGet(toIncrease);
        long newSize = getDatabaseSize() + toIncrease;
        localEnv.setMapSize(newSize);
    }

    @Override
    public long getIncreasedCounter() {
        return increasedCounter.get();
    }

    @Override
    public long getIncreasedSum() {
        return increasedSum.get();
    }

    @Override
    public void logStats() {
        Env<ByteBuffer> localEnv = Objects.requireNonNull(env);

        LOGGER.info("##### BEGIN: LMDB stats #####");
        LOGGER.info("... this may take a lot of time ...");
        LOGGER.info("DatabaseSize: " + new ByteConversion().bytesToMib(getDatabaseSize()) + " MiB");
        LOGGER.info("IncreasedCounter: " + getIncreasedCounter());
        LOGGER.info("IncreasedSum: " + new ByteConversion().bytesToMib(getIncreasedSum()) + " MiB");
        LOGGER.info("Stat: " + localEnv.stat());
        long count = count();
        LOGGER.info("LMDB contains " + count + " unique entries.");
        LOGGER.info("##### END: LMDB stats #####");
    }
}
