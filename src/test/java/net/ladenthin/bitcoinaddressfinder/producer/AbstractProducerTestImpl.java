// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;

public class AbstractProducerTestImpl extends AbstractProducer {

    public AbstractProducerTestImpl(
            CProducer cProducer,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper,
            RuntimeStatistics runtimeStatistics) {
        super(cProducer, consumer, keyUtility, keyProducer, bitHelper, runtimeStatistics);
    }

    @Override
    protected ProducerType producerType() {
        return ProducerType.CPU;
    }

    @Override
    public void produceKeys() {}

    @Override
    public void processSecretBase(BigInteger secretBase) {}

    @Override
    public void processSecrets(BigInteger[] secret) {}
}
