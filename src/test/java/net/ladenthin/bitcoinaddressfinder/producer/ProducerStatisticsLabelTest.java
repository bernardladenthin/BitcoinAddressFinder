// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import net.ladenthin.bitcoinaddressfinder.MockConsumer;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;

/**
 * Verifies the per-producer statistics label ({@code "<keyProducerId> (<Strategy>, <CPU|GPU>)"})
 * and that {@code consumeSecrets} increments the shared runtime metrics under it.
 */
public class ProducerStatisticsLabelTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    private KeyProducerJavaRandom randomKeyProducer(String id) {
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = id;
        return new KeyProducerJavaRandom(cKeyProducerJavaRandom, keyUtility, bitHelper);
    }

    @Test
    public void producerLabel_cpuProducerWithRandomStrategy_idStrategyBackend() {
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = "exampleRandom";
        ProducerJava producer = new ProducerJava(
                cProducerJava,
                new MockConsumer(),
                keyUtility,
                randomKeyProducer("exampleRandom"),
                bitHelper,
                new RuntimeStatistics());

        assertThat(producer.producerLabel(), is(equalTo("exampleRandom (Random, CPU)")));
    }

    @Test
    public void producerLabel_gpuProducerWithRandomStrategy_backendIsGpu() {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.keyProducerId = "exampleRandom";
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        try {
            ProducerOpenCL producer = new ProducerOpenCL(
                    cProducerOpenCL,
                    new MockConsumer(),
                    keyUtility,
                    randomKeyProducer("exampleRandom"),
                    bitHelper,
                    new RuntimeStatistics(),
                    pool);

            assertThat(producer.producerLabel(), is(equalTo("exampleRandom (Random, GPU)")));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void consumeSecrets_incrementsBatchCountUnderProducerLabel() {
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = "exampleRandom";
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        ProducerJava producer = new ProducerJava(
                cProducerJava,
                new MockConsumer(),
                keyUtility,
                randomKeyProducer("exampleRandom"),
                bitHelper,
                runtimeStatistics);

        producer.consumeSecrets(BigInteger.ONE);

        assertThat(runtimeStatistics.batchesByProducerSnapshot().get("exampleRandom (Random, CPU)"), is(equalTo(1L)));
    }
}
