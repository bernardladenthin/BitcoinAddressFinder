// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;

public class MockConsumer implements Consumer {

    public List<PublicKeyBytes[]> publicKeyBytesArrayList = new ArrayList<>();

    @Override
    public void consumeKeys(PublicKeyBytes[] publicKeyBytes) throws InterruptedException {
        publicKeyBytesArrayList.add(publicKeyBytes);
    }

    @Override
    public void startConsumer() {}

    @Override
    public void interrupt() {}
}
