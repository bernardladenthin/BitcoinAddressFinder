// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import org.jspecify.annotations.Nullable;

public class MockConsumer implements Consumer {

    public List<PublicKeyBytes[]> publicKeyBytesArrayList = new ArrayList<>();

    /** Bases seen alongside each batch, so a test can assert what the producer reported. */
    public List<@Nullable BigInteger> secretBaseList = new ArrayList<>();

    @Override
    public void consumeKeys(PublicKeyBytes[] publicKeyBytes, @Nullable BigInteger secretBase)
            throws InterruptedException {
        publicKeyBytesArrayList.add(publicKeyBytes);
        secretBaseList.add(secretBase);
    }

    @Override
    public void startConsumer() {}

    @Override
    public void interrupt() {}
}
