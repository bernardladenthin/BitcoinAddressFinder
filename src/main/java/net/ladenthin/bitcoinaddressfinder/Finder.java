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
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.*;

public class Finder implements Interruptable {

    /**
     * We must define a maximum time to wait for terminate. Wait for 100 thousand years is enough.
     */
    @VisibleForTesting
    static Duration AWAIT_DURATION_TERMINATE = Duration.ofDays(365L * 1000L);
    
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private final CFinder finder;
    
    private final Map<String, KeyProducer> keyProducers = new HashMap<>();
    
    @Nullable
    private ConsumerJava consumerJava;
    
    private final List<ProducerOpenCL> openCLProducers = new ArrayList<>();
    private final List<ProducerJava> javaProducers = new ArrayList<>();
    private final List<ProducerJavaSecretsFiles> javaProducersSecretsFiles = new ArrayList<>();
    
    @VisibleForTesting
    final ExecutorService producerExecutorService = Executors.newCachedThreadPool();
    
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);
    private final BitHelper bitHelper = new BitHelper();
    
    public Finder(CFinder finder) {
        this.finder = finder;
    }
    
    public void startKeyProducer() {
        logger.info("startKeyProducer");
        processKeyProducers(
            finder.keyProducerJavaRandom,
            cKeyProducerJavaRandom -> new KeyProducerJavaRandom(cKeyProducerJavaRandom, keyUtility, bitHelper),
            cKeyProducerJavaRandom -> cKeyProducerJavaRandom.keyProducerId,
            keyProducers
        );

        processKeyProducers(
            finder.keyProducerJavaBip39,
            cKeyProducerJavaBip39 -> new KeyProducerJavaBip39(cKeyProducerJavaBip39, keyUtility, bitHelper),
            cKeyProducerJavaBip39 -> cKeyProducerJavaBip39.keyProducerId,
            keyProducers
        );

        processKeyProducers(
            finder.keyProducerJavaIncremental,
            cKeyProducerJavaIncremental -> new KeyProducerJavaIncremental(cKeyProducerJavaIncremental, keyUtility, bitHelper),
            cKeyProducerJavaIncremental -> cKeyProducerJavaIncremental.keyProducerId,
            keyProducers
        );
    }
    
    private <T, K> void processKeyProducers(
        List<T> configList,
        Function<T, K> constructor,
        Function<T, String> getId,
        Map<String, K> keyProducers
    ) {
        if (configList != null) {
            for (T config : configList) {
                String keyProducerId = getId.apply(config);
                if (keyProducerId == null) {
                    throw new KeyProducerIdNullException();
                }
                if (keyProducers.containsKey(keyProducerId)) {
                    throw new KeyProducerIdIsNotUniqueException(keyProducerId);
                }
                K keyProducer = constructor.apply(config);
                keyProducers.put(keyProducerId, keyProducer);
            }
        }
    }

    public void startConsumer() {
        logger.info("startConsumer");
        if (finder.consumerJava != null) {
            consumerJava = new ConsumerJava(finder.consumerJava, keyUtility, persistenceUtils);
            consumerJava.initLMDB();
            consumerJava.startConsumer();
            consumerJava.startStatisticsTimer();
        }
    }

    public void configureProducer() {
        logger.info("configureProducer");
        processProducers(
            finder.producerJava,
            bitHelper::assertBatchSizeInBitsIsInRange,
            this::getKeyProducerJava,
            (config, keyProducer) -> new ProducerJava(config, consumerJava, keyUtility, keyProducer, bitHelper),
            javaProducers
        );

        processProducers(
            finder.producerJavaSecretsFiles,
            bitHelper::assertBatchSizeInBitsIsInRange,
            this::getKeyProducerJava,
            (config, keyProducer) -> new ProducerJavaSecretsFiles(config, consumerJava, keyUtility, keyProducer, bitHelper),
            javaProducersSecretsFiles
        );

        processProducers(
            finder.producerOpenCL,
            bitHelper::assertBatchSizeInBitsIsInRange,
            this::getKeyProducerJava,
            (config, keyProducer) -> new ProducerOpenCL(config, consumerJava, keyUtility, keyProducer, bitHelper),
            openCLProducers
        );
    }
    
    private <T extends CProducer, P> void processProducers(
        List<T> configs,
        java.util.function.Consumer<Integer> batchSizeAssert,
        Function<T, KeyProducer> getKeyProducer,
        BiFunction<T, KeyProducer, P> producerConstructor,
        Collection<P> targetCollection
    ) {
        if (configs != null) {
            for (T config : configs) {
                batchSizeAssert.accept(config.batchSizeInBits);
                KeyProducer keyProducer = getKeyProducer.apply(config);
                P producer = producerConstructor.apply(config, keyProducer);
                targetCollection.add(producer);
            }
        }
    }

    public KeyProducer getKeyProducerJava(CProducer cProducer) throws RuntimeException {
        KeyProducer keyProducer = keyProducers.get(cProducer.keyProducerId);
        if(keyProducer == null) {
            throw new KeyProducerIdUnknownException(cProducer.keyProducerId);
        }
        return keyProducer;
    }
    
    public void initProducer() {
        logger.info("initProducer");
        for (Producer producer : getAllProducers()) {
            producer.initProducer();
        }
    }
    
    public void startProducer() {
        logger.info("startProducer");
        for (Producer producer : getAllProducers()) {
            producerExecutorService.submit(producer);
        }
    }
    
    public void shutdownAndAwaitTermination() {
        logger.info("shutdownAndAwaitTermination");
        try {
            producerExecutorService.shutdown();
            producerExecutorService.awaitTermination(AWAIT_DURATION_TERMINATE.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        
        // no producers are running anymore, the consumer can be interrupted
        if (consumerJava != null) {
            logger.info("Interrupt: " + consumerJava.toString());
            consumerJava.interrupt();
            consumerJava = null;
        }
        logger.info("consumerJava released.");
    }
    
    @Override
    public void interrupt() {
        logger.info("interrupt called: delegate interrupt to all producer");
        for (Producer producer : getAllProducers()) {
            logger.info("Interrupt: " + producer.toString());
            producer.interrupt();
            logger.info("waitTillProducerNotRunning ...");
            producer.waitTillProducerNotRunning();
            producer.releaseProducer();
        }
        freeAllProducers();
        logger.info("All producers released and freed.");
    }
    
    public Map<String, KeyProducer> getKeyProducers() {
        return ImmutableMap.copyOf(keyProducers);
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
}
