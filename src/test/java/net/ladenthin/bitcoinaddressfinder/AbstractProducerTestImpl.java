// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;

public class AbstractProducerTestImpl extends AbstractProducer {

    public AbstractProducerTestImpl(CProducer cProducer, Consumer consumer, KeyUtility keyUtility, KeyProducer keyProducer, BitHelper bitHelper) {
        super(cProducer, consumer, keyUtility, keyProducer, bitHelper);
    }

    @Override
    public void produceKeys() {
    }

    @Override
    public void processSecretBase(BigInteger secretBase) {
    }

    @Override
    public void processSecrets(BigInteger[] secret) {
    }
    
}