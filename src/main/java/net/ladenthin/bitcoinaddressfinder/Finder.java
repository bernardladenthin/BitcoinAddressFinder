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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaBrainwallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Finder implements Interruptable {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private final CFinder finder;

    private final AtomicBoolean shouldRun;
    
    private final List<ProducerOpenCL> openCLProducers = new ArrayList<>();
    private final List<ProducerJava> javaProducers = new ArrayList<>();
    private final List<ProducerJavaBrainwallet> javaProducersBrainwallet = new ArrayList<>();

    @Nullable
    private ConsumerJava consumerJava;

    public Finder(CFinder finder, AtomicBoolean shouldRun) {
        this.finder = finder;
        this.shouldRun = shouldRun;
    }

    public void startRunner() {
        ExecutorService producerExecutorService = Executors.newCachedThreadPool();

        if (finder.consumerJava != null) {
            consumerJava = new ConsumerJava(finder.consumerJava, shouldRun);
            consumerJava.initLMDB();
            consumerJava.startConsumer();
            consumerJava.startStatisticsTimer();
        }

        // It is already thread local, no need for {@link java.util.concurrent.ThreadLocalRandom}.
        final Random random;
        try {
            random = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        if (finder.producerJava != null) {
            for (CProducerJava cProducerJava : finder.producerJava) {
                cProducerJava.assertGridNumBitsCorrect();
                ProducerJava producerJava = new ProducerJava(cProducerJava, shouldRun, consumerJava, consumerJava.keyUtility, random);
                javaProducers.add(producerJava);
                producerJava.initProducer();
                producerExecutorService.submit(producerJava);
            }
        }

        if (finder.producerJavaBrainwallet != null) {
            for (CProducerJavaBrainwallet cProducerJavaBrainwallet : finder.producerJavaBrainwallet) {
                cProducerJavaBrainwallet.assertGridNumBitsCorrect();
                ProducerJavaBrainwallet producerJavaBrainwallet = new ProducerJavaBrainwallet(cProducerJavaBrainwallet, shouldRun, consumerJava, consumerJava.keyUtility, random);
                javaProducersBrainwallet.add(producerJavaBrainwallet);
                producerJavaBrainwallet.initProducer();
                producerExecutorService.submit(producerJavaBrainwallet);
            }
        }

        if (finder.producerOpenCL != null) {
            for (CProducerOpenCL cProducerOpenCL : finder.producerOpenCL) {
                cProducerOpenCL.assertGridNumBitsCorrect();
                ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, shouldRun, consumerJava, consumerJava.keyUtility, random);
                openCLProducers.add(producerOpenCL);
                producerOpenCL.initProducer();
                producerExecutorService.submit(producerOpenCL);
            }
        }
    }
    
    
    public void interrupt() {
        if (consumerJava != null) {
            consumerJava.timer.cancel();
        }
        logger.info("Shut down, please wait for remaining tasks.");

        for (ProducerOpenCL openCLProducer : openCLProducers) {
            openCLProducer.waitTillProducerNotRunning();
            openCLProducer.releaseProducers();
        }

        for (ProducerJava producerJava : javaProducers) {
            producerJava.waitTillProducerNotRunning();
            producerJava.releaseProducers();
        }
        logger.info("All producers released.");
    }


}
