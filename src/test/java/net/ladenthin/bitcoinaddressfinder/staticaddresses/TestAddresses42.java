// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

public class TestAddresses42 extends AbstractTestAddresses {

    public final static int RANDOM_SEED = 42;

    public TestAddresses42(int numberOfAddresses, boolean compressed) {
        super(RANDOM_SEED, numberOfAddresses, compressed);
    }

}
