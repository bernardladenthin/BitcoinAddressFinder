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

public interface Persistence extends AutoCloseable {

    void init();
    boolean isClosed();
    long count();
    Coin getAmount(ByteBuffer hash160);
    boolean containsAddress(ByteBuffer hash160);

    void writeAllAmountsToAddressFile(File file, CAddressFileOutputFormat addressFileOutputFormat, AtomicBoolean shouldRun) throws IOException;

    /**
     * @param hash160 the hash160 to change its amount
     * @param amountToChange positive means add, negative means substract the amount
     */
    void changeAmount(ByteBuffer hash160, Coin amountToChange);

    void putNewAmount(ByteBuffer hash160, Coin toWrite);
    void putAllAmounts(Map<ByteBuffer, Coin> amounts) throws IOException;

    Coin getAllAmountsFromAddresses(List<ByteBuffer> hash160s);
    
    long getDatabaseSize();
    
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
