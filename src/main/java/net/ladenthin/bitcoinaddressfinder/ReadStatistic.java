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
import java.util.List;

public class ReadStatistic {

    public long successful = 0;
    public long unsupported = 0;
    /**
     * In percent.
     */
    public double currentFileProgress;
    
    public final List<String> errors = new ArrayList<>();

    // generated
    @Override
    public String toString() {
        return "ReadStatistic{" + "successful=" + successful + ", unsupported=" + unsupported + ", currentFileProgress=" + currentFileProgress + ", errors=" + errors + '}';
    }
}
