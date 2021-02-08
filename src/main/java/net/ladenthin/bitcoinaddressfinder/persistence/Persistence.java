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
package net.ladenthin.bitcoinaddressfinder.persistence;

import org.bitcoinj.core.Coin;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFileOutputFormat;

public interface Persistence {

    void init();
    void close();
    long count();
    Coin getAmount(ByteBuffer hash160);
    boolean containsAddress(ByteBuffer hash160);

    /**
     * 
     * @param file
     * @param addressFileOutputFormat the format
     * @throws IOException 
     */
    void writeAllAmountsToAddressFile(File file, CAddressFileOutputFormat addressFileOutputFormat) throws IOException;

    /**
     * @param hash160 the hash160 to change its amount
     * @param amountToChange positive means add, negative means substract the amount
     */
    void changeAmount(ByteBuffer hash160, Coin amountToChange);

    void putNewAmount(ByteBuffer hash160, Coin toWrite);
    void putAllAmounts(Map<ByteBuffer, Coin> amounts) throws IOException;

    Coin getAllAmountsFromAddresses(List<ByteBuffer> hash160s);
    
    String getStatsAsString();
    
    long getDatabaseSize();
    
    void increaseDatabaseSize(long toIncrease);
    
    /**
     * Counter of increase.
     * @return 
     */
    long getIncreasedCounter();
    
    /**
     * The sum of increase in bytes.
     * @return 
     */
    long getIncreasedSum();
}
