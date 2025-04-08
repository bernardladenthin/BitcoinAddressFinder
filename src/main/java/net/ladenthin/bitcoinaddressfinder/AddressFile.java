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

import java.io.File;
import java.util.function.Consumer;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.NonNull;

public class AddressFile extends AbstractPlaintextFile {

    @NonNull
    private final Network network;
    @NonNull
    private final KeyUtility keyUtility;
    @NonNull
    private final Consumer<AddressToCoin> addressConsumer;
    @NonNull
    private final Consumer<String> unsupportedConsumer;

    public AddressFile(@NonNull File file, ReadStatistic readStatistic, @NonNull Network network, @NonNull Consumer<AddressToCoin> addressConsumer, @NonNull Consumer<String> unsupportedConsumer) {
        super(file, readStatistic);
        this.network = network;
        this.addressConsumer = addressConsumer;
        this.unsupportedConsumer = unsupportedConsumer;
        keyUtility = new KeyUtility(network, new ByteBufferUtility(true));
    }

    @Override
    protected void processLine(String line) {
        AddressTxtLine addressTxtLine = new AddressTxtLine();
        AddressToCoin addressToCoin = addressTxtLine.fromLine(line, keyUtility);
        if (addressToCoin != null) {
            addressConsumer.accept(addressToCoin);
            readStatistic.successful++;
        } else {
            unsupportedConsumer.accept(line);
            readStatistic.unsupported++;
        }
    }
}
