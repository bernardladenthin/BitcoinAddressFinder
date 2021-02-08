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
package net.ladenthin.bitcoinaddressfinder;

import org.bitcoinj.core.Sha256Hash;

import java.util.Date;
import java.util.Objects;

public class Statistics {

    private final Sha256Hash currentTransactionHash;
    private final Date currentBlockTime;

    public Statistics(Sha256Hash currentTransactionHash, Date currentBlockTime) {
        this.currentTransactionHash = currentTransactionHash;
        this.currentBlockTime = currentBlockTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Statistics that = (Statistics) o;
        return Objects.equals(currentTransactionHash, that.currentTransactionHash)
                && Objects.equals(currentBlockTime, that.currentBlockTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentTransactionHash, currentBlockTime);
    }

    @Override
    public String toString() {
        return "Statistics{"
                + "currentTransactionHash='" + currentTransactionHash + '\''
                + ", currentBlockTime=" + currentBlockTime
                + '}';
    }
}
