// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

public interface Consumer extends Interruptable {

    void consumeKeys(PublicKeyBytes[] publicKeyBytes) throws InterruptedException;

    void startConsumer();
}
