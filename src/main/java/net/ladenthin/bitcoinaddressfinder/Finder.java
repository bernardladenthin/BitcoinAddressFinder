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

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Finder implements Interruptable, SecretFactory {

    /**
     * We must define a maximum time to wait for terminate. Wait for 100 thousand years is enough.
     */
    @VisibleForTesting
    static Duration AWAIT_DURATION_TERMINATE = Duration.ofDays(365L * 1000L);
    
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private final CFinder finder;
    
    private final List<ProducerOpenCL> openCLProducers = new ArrayList<>();
    private final List<ProducerJava> javaProducers = new ArrayList<>();
    private final List<ProducerJavaSecretsFiles> javaProducersSecretsFiles = new ArrayList<>();
    
    /**
     * It is already thread local, no need for {@link java.util.concurrent.ThreadLocalRandom}.
     */
    private final Random random;

    @VisibleForTesting
    final ExecutorService producerExecutorService = Executors.newCachedThreadPool();
    
    private final NetworkParameters networkParameters = MainNetParams.get();
    private final KeyUtility keyUtility = new KeyUtility(networkParameters, new ByteBufferUtility(false));
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(networkParameters);
    
    @Nullable
    private ConsumerJava consumerJava;

    public Finder(CFinder finder) {
        this.finder = finder;
        try {
            random = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void startConsumer() {
        if (finder.consumerJava != null) {
            consumerJava = new ConsumerJava(finder.consumerJava, keyUtility, persistenceUtils);
            consumerJava.initLMDB();
            consumerJava.startConsumer();
            consumerJava.startStatisticsTimer();
        }
    }

    public void configureProducer() {
        if (finder.producerJava != null) {
            for (CProducerJava cProducerJava : finder.producerJava) {
                cProducerJava.assertGridNumBitsCorrect();
                ProducerJava producerJava = new ProducerJava(cProducerJava, consumerJava, keyUtility, this);
                javaProducers.add(producerJava);
            }
        }

        if (finder.producerJavaSecretsFiles != null) {
            for (CProducerJavaSecretsFiles cProducerJavaSecretsFiles : finder.producerJavaSecretsFiles) {
                cProducerJavaSecretsFiles.assertGridNumBitsCorrect();
                ProducerJavaSecretsFiles producerJavaSecretsFiles = new ProducerJavaSecretsFiles(cProducerJavaSecretsFiles, consumerJava, keyUtility, this);
                javaProducersSecretsFiles.add(producerJavaSecretsFiles);
            }
        }

        if (finder.producerOpenCL != null) {
            for (CProducerOpenCL cProducerOpenCL : finder.producerOpenCL) {
                cProducerOpenCL.assertGridNumBitsCorrect();
                ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, consumerJava, keyUtility, this);
                openCLProducers.add(producerOpenCL);
            }
        }
    }
    
    public void initProducer() {
        for (Producer producer : getAllProducers()) {
            producer.initProducer();
        }
    }
    
    public void startProducer() {
        for (Producer producer : getAllProducers()) {
            producerExecutorService.submit(producer);
        }
    }
    
    public void shutdownAndAwaitTermination() {
        try {
            producerExecutorService.shutdown();
            producerExecutorService.awaitTermination(AWAIT_DURATION_TERMINATE.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        
        // no producers are running anymore, the consumer can be interrupted
        if (consumerJava != null) {
            logger.info("Interrupt: " + consumerJava);
            consumerJava.interrupt();
            consumerJava = null;
        }
        logger.info("consumerJava released.");
    }
    
    @Override
    public void interrupt() {
        logger.info("interrupt called: delegate interrupt to all producer");
        for (Producer producer : getAllProducers()) {
            logger.info("Interrupt: " + producer);
            producer.interrupt();
            logger.info("waitTillProducerNotRunning ...");
            producer.waitTillProducerNotRunning();
            producer.releaseProducer();
        }
        freeAllProducers();
        logger.info("All producers released and freed.");
    }
    
    public List<Producer> getAllProducers() {
        List<Producer> producers = new ArrayList<>();
        producers.addAll(javaProducers);
        producers.addAll(javaProducersSecretsFiles);
        producers.addAll(openCLProducers);
        return producers;
    }
    
    public void freeAllProducers() {
        javaProducers.clear();
        javaProducersSecretsFiles.clear();
        openCLProducers.clear();
    }
    
    public List<Consumer> getAllConsumers() {
        List<Consumer> consumers = new ArrayList<>();
        if (consumerJava != null) {
            consumers.add(consumerJava);
        }
        return consumers;
    }

    @Override
    public BigInteger createSecret(int maximumBitLength) {
        BigInteger secret = keyUtility.createSecret(maximumBitLength, random);
        return secret;
    }

}
