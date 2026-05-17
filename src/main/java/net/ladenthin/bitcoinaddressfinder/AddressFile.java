// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
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
        try {
            AddressToCoin addressToCoin = addressTxtLine.fromLine(line, keyUtility);
            addressConsumer.accept(addressToCoin);
            readStatistic.successful++;
        } catch (AddressFormatNotAcceptedException e) {
            unsupportedConsumer.accept(line);
            readStatistic.incrementUnsupported(e.getReason());
        }
    }
}
