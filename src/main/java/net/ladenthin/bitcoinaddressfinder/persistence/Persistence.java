// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFileOutputFormat;
import org.bitcoinj.base.Coin;

/**
 * Persistence abstraction for the address &#x2192; amount mapping used by the consumer.
 *
 * <p>Extends {@link AddressLookup} so accelerators (Bloom filter, HashMap cache,
 * sorted-array prefix) can be composed in front of any full backend by implementing
 * the smaller {@link AddressLookup} contract instead of this full surface.
 */
public interface Persistence extends AddressLookup, AutoCloseable {

    /** Opens the underlying storage. */
    void init();

    /**
     * Releases the underlying resources. Implementations are expected not to throw
     * checked exceptions from this method; the narrower declaration here overrides
     * {@link AutoCloseable#close()}'s {@code throws Exception} so try-with-resources
     * callers do not have to declare or catch a phantom {@code Exception} — in
     * particular {@code InterruptedException}, which javac's {@code -Xlint:try}
     * warns about specifically because silently swallowing it loses the interrupt
     * signal.
     */
    @Override
    void close();

    /**
     * Indicates whether the persistence layer has been closed.
     *
     * @return {@code true} if the persistence has been closed
     */
    boolean isClosed();

    /**
     * Returns the number of entries currently stored.
     *
     * @return the number of entries currently stored
     */
    long count();

    /**
     * Writes all (hash160, amount) entries to {@code file} in the given output format.
     *
     * @param file                    the destination file
     * @param addressFileOutputFormat the output format
     * @param shouldRun               cancellation flag checked between rows
     * @throws IOException if writing fails
     */
    void writeAllAmountsToAddressFile(
            File file, CAddressFileOutputFormat addressFileOutputFormat, AtomicBoolean shouldRun) throws IOException;

    /**
     * Adds or subtracts an amount from the value stored for {@code hash160}.
     *
     * @param hash160 the hash160 to change its amount
     * @param amountToChange positive means add, negative means substract the amount
     */
    void changeAmount(ByteBuffer hash160, Coin amountToChange);

    /**
     * Inserts a new (hash160, amount) entry.
     *
     * @param hash160 the address hash
     * @param toWrite the amount to associate with the address
     */
    void putNewAmount(ByteBuffer hash160, Coin toWrite);

    /**
     * Writes multiple (hash160, amount) entries in a single transaction.
     *
     * @param amounts the entries to write
     * @throws IOException if writing fails
     */
    void putAllAmounts(Map<ByteBuffer, Coin> amounts) throws IOException;

    /**
     * Sums the stored amounts of all given addresses.
     *
     * @param hash160s the address hashes to sum over
     * @return the total amount aggregated across the supplied addresses
     */
    Coin getAllAmountsFromAddresses(List<ByteBuffer> hash160s);

    /**
     * Returns the current on-disk database size.
     *
     * @return the current on-disk database size in bytes
     */
    long getDatabaseSize();

    /**
     * Grows the underlying map size by the given amount.
     *
     * @param toIncrease the additional capacity in bytes
     */
    void increaseDatabaseSize(long toIncrease);

    /**
     * Retrieves the current value of the increased counter.
     *
     * @return the value of the increased counter as a long.
     */
    long getIncreasedCounter();

    /**
     * Returns the total sum of all increments applied to the persistence storage.
     *
     * @return the accumulated sum of all increment operations in the persistence storage
     */
    long getIncreasedSum();

    /**
     * Attention: This method might me take a lot of time.
     */
    void logStats();
}
