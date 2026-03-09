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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReadStatistic {

    public long successful = 0;
    public final Map<String, Long> unsupportedReasons = new LinkedHashMap<>();
    /**
     * In percent.
     */
    public double currentFileProgress;

    public final List<String> errors = new ArrayList<>();

    /**
     * Increments the counter for the given unsupported reason by one.
     * If the reason has not been seen before, it is added with a count of 1.
     *
     * @param reason the reason string from {@link AddressFormatNotAcceptedException#getReason()}
     */
    public void incrementUnsupported(String reason) {
        unsupportedReasons.merge(reason, 1L, Long::sum);
    }

    /**
     * Returns the total number of unsupported lines across all reasons.
     *
     * @return sum of all per-reason counts in {@link #unsupportedReasons}
     */
    public long getUnsupportedTotal() {
        return unsupportedReasons.values().stream().mapToLong(Long::longValue).sum();
    }

    @Override
    public String toString() {
        return "ReadStatistic{" + "successful=" + successful + ", unsupportedTotal=" + getUnsupportedTotal() + ", unsupportedReasons=" + unsupportedReasons + ", currentFileProgress=" + currentFileProgress + ", errors=" + errors + '}';
    }
}
