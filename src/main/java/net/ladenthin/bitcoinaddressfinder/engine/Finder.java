// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.engine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.consumer.ConsumerJava;
import net.ladenthin.bitcoinaddressfinder.core.FireAndForget;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.core.Startable;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerIdIsNotUniqueException;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerIdNullException;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerIdUnknownException;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.producer.Producer;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerJava;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerState;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrator: wires up key producers, producers and the consumer based on the configuration and
 * manages their life cycle.
 */
@ToString
public class Finder implements Interruptable {

    /** SLF4J logger for the {@link Finder}. */
    private static final Logger LOGGER = LoggerFactory.getLogger(Finder.class);

    private final CFinder finder;

    private final Map<String, KeyProducer> keyProducers = new HashMap<>();

    /**
     * Shared runtime metrics sink wired into the consumer (which renders the statistics
     * line) and every producer (which increment their per-producer batch counters).
     */
    private final RuntimeStatistics runtimeStatistics = new RuntimeStatistics();

    // ConsumerJava is a stateful coordinator (executors + queue + lifecycle) — recursive/heavy.
    @ToString.Exclude
    private @Nullable ConsumerJava consumerJava;

    // The three producer lists hold mutable stateful coordinators — recursive/heavy in logs.
    @ToString.Exclude
    private final List<ProducerOpenCL> openCLProducers = new ArrayList<>();

    @ToString.Exclude
    private final List<ProducerJava> javaProducers = new ArrayList<>();

    @ToString.Exclude
    private final List<ProducerJavaSecretsFiles> javaProducersSecretsFiles = new ArrayList<>();

    // ExecutorService toString is verbose pool internals — not useful in aggregate logs.
    @ToString.Exclude
    private final ExecutorService producerExecutorService;

    private final KeyUtility keyUtility;
    private final PersistenceUtils persistenceUtils;
    private final BitHelper bitHelper = new BitHelper();

    /**
     * Creates a new finder with the default producer executor (a cached thread pool).
     *
     * @param finder the finder configuration
     */
    public Finder(CFinder finder) {
        this(finder, Executors.newCachedThreadPool());
    }

    /**
     * Test-friendly constructor that injects the producer executor service.
     *
     * <p>Production callers should use {@link #Finder(CFinder)}; this overload exists
     * so tests can substitute their own {@link ExecutorService} and assert on its
     * post-shutdown state without reaching into the finder's internal field.
     *
     * @param finder                  the finder configuration
     * @param producerExecutorService executor used to run registered producers
     */
    @VisibleForTesting
    Finder(CFinder finder, ExecutorService producerExecutorService) {
        this.finder = finder;
        this.producerExecutorService = producerExecutorService;
        Network network = new NetworkParameterFactory().getNetwork();
        this.keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        this.persistenceUtils = new PersistenceUtils(network);
    }

    /**
     * Instantiates and registers every configured key producer.
     */
    public void startKeyProducer() {
        LOGGER.info("startKeyProducer");
        processKeyProducers(
                finder.keyProducerJavaRandom,
                cKeyProducerJavaRandom -> new KeyProducerJavaRandom(cKeyProducerJavaRandom, keyUtility, bitHelper),
                cKeyProducerJavaRandom -> cKeyProducerJavaRandom.keyProducerId,
                keyProducers);

        processKeyProducers(
                finder.keyProducerJavaBip39,
                cKeyProducerJavaBip39 -> new KeyProducerJavaBip39(cKeyProducerJavaBip39, keyUtility, bitHelper),
                cKeyProducerJavaBip39 -> cKeyProducerJavaBip39.keyProducerId,
                keyProducers);

        processKeyProducers(
                finder.keyProducerJavaIncremental,
                cKeyProducerJavaIncremental ->
                        new KeyProducerJavaIncremental(cKeyProducerJavaIncremental, keyUtility, bitHelper),
                cKeyProducerJavaIncremental -> cKeyProducerJavaIncremental.keyProducerId,
                keyProducers);

        processKeyProducers(
                finder.keyProducerJavaSocket,
                cKeyProducerJavaSocket -> new KeyProducerJavaSocket(cKeyProducerJavaSocket, keyUtility, bitHelper),
                cKeyProducerJavaSocket -> cKeyProducerJavaSocket.keyProducerId,
                keyProducers);

        processKeyProducers(
                finder.keyProducerJavaWebSocket,
                cKeyProducerJavaWebSocket ->
                        new KeyProducerJavaWebSocket(cKeyProducerJavaWebSocket, keyUtility, bitHelper),
                cKeyProducerJavaWebSocket -> cKeyProducerJavaWebSocket.keyProducerId,
                keyProducers);

        processKeyProducers(
                finder.keyProducerJavaZmq,
                cKeyProducerJavaZmq -> new KeyProducerJavaZmq(cKeyProducerJavaZmq, keyUtility, bitHelper),
                cKeyProducerJavaZmq -> cKeyProducerJavaZmq.keyProducerId,
                keyProducers);
    }

    private <T, K> void processKeyProducers(
            Iterable<T> configList,
            Function<T, K> constructor,
            Function<T, @Nullable String> getId,
            Map<String, K> keyProducers) {
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
                // Producers that implement Startable (Socket, Zmq) move their background
                // reader out of the constructor to avoid the JEP 410 this-escape; this
                // single dispatch site invokes start() for any such producer, so the
                // call lives in one place rather than at every factory lambda.
                if (keyProducer instanceof Startable startable) {
                    startable.start();
                }
                keyProducers.put(keyProducerId, keyProducer);
            }
        }
    }

    /**
     * Initialises and starts the {@link ConsumerJava}.
     *
     * @throws Exception if the consumer's LMDB layer cannot be opened or the
     *     accelerator snapshot cannot be built
     */
    public void startConsumer() throws Exception {
        LOGGER.info("startConsumer");
        CConsumerJava localCConsumerJava = Objects.requireNonNull(finder.consumerJava);

        final ConsumerJava localConsumerJava =
                new ConsumerJava(localCConsumerJava, keyUtility, persistenceUtils, runtimeStatistics);
        consumerJava = localConsumerJava;
        localConsumerJava.initLMDB();
        localConsumerJava.startConsumer();
        localConsumerJava.startStatisticsTimer();
    }

    /**
     * Builds the configured producers and binds them to their key producer and consumer.
     */
    public void configureProducer() {
        LOGGER.info("configureProducer");
        var localConsumerJava = Objects.requireNonNull(consumerJava);
        processProducers(
                finder.producerJava,
                bitHelper::assertBatchSizeInBitsIsInRange,
                this::getKeyProducer,
                (config, keyProducer) -> new ProducerJava(
                        config, localConsumerJava, keyUtility, keyProducer, bitHelper, runtimeStatistics),
                javaProducers);

        processProducers(
                finder.producerJavaSecretsFiles,
                bitHelper::assertBatchSizeInBitsIsInRange,
                this::getKeyProducer,
                (config, keyProducer) -> new ProducerJavaSecretsFiles(
                        config, localConsumerJava, keyUtility, keyProducer, bitHelper, runtimeStatistics),
                javaProducersSecretsFiles);

        processProducers(
                finder.producerOpenCL,
                bitHelper::assertBatchSizeInBitsIsInRange,
                this::getKeyProducer,
                (config, keyProducer) -> new ProducerOpenCL(
                        config, localConsumerJava, keyUtility, keyProducer, bitHelper, runtimeStatistics),
                openCLProducers);
    }

    private <T extends CProducer, P> void processProducers(
            Iterable<T> configs,
            java.util.function.Consumer<Integer> batchSizeAssert,
            Function<T, KeyProducer> getKeyProducer,
            BiFunction<T, KeyProducer, P> producerConstructor,
            Collection<P> targetCollection) {
        if (configs != null) {
            for (T config : configs) {
                batchSizeAssert.accept(config.batchSizeInBits);
                KeyProducer keyProducer = getKeyProducer.apply(config);
                P producer = producerConstructor.apply(config, keyProducer);
                targetCollection.add(producer);
            }
        }
    }

    /**
     * Resolves the {@link KeyProducer} configured for the given producer.
     *
     * @param cProducer the producer configuration
     * @return the resolved {@link KeyProducer}
     * @throws KeyProducerIdUnknownException if the referenced id is null or unknown
     */
    public KeyProducer getKeyProducer(CProducer cProducer) {
        final String id = cProducer.keyProducerId;
        if (id == null) {
            throw new KeyProducerIdUnknownException(null);
        }
        KeyProducer keyProducer = keyProducers.get(id);
        if (keyProducer == null) {
            throw new KeyProducerIdUnknownException(id);
        }
        return keyProducer;
    }

    /**
     * Calls {@code initProducer()} on every configured producer.
     *
     * @throws Exception if any producer fails to initialise; the orchestrator does not
     *                   catch this here, the Main run loop is the architectural place
     *                   that catches, logs and triggers shutdown
     */
    public void initProducer() throws Exception {
        LOGGER.info("initProducer");
        for (Producer producer : getAllProducers()) {
            producer.initProducer();
        }
    }

    /**
     * Submits every configured producer to the producer executor service.
     */
    public void startProducer() {
        LOGGER.info("startProducer");
        // Late-bind the running-producer gauge now that the producers exist (the consumer's
        // statistics timer started earlier, before configureProducer()).
        runtimeStatistics.setRunningProducersGauge(() -> getAllProducers().stream()
                .filter(producer -> producer.getState() == ProducerState.RUNNING)
                .count());
        for (Producer producer : getAllProducers()) {
            @FireAndForget("lifecycle via Producer.interrupt() and Finder.interrupt() shutdown")
            @SuppressWarnings("FutureReturnValueIgnored")
            Object unused = producerExecutorService.submit(producer);
        }
    }

    /**
     * Shuts down the producer executor and interrupts the consumer once producers have stopped.
     *
     * @throws InterruptedException if the calling thread is interrupted while awaiting termination;
     *                              callers are responsible for restoring the interrupt flag or
     *                              propagating it according to their own design.
     */
    public void shutdownAndAwaitTermination() throws InterruptedException {
        LOGGER.info("shutdownAndAwaitTermination");
        producerExecutorService.shutdown();
        producerExecutorService.awaitTermination(finder.awaitTerminateSeconds, TimeUnit.SECONDS);

        // no producers are running anymore, the consumer can be interrupted
        final ConsumerJava localConsumerJava = consumerJava;
        if (localConsumerJava != null) {
            LOGGER.info("Interrupt: " + localConsumerJava);
            localConsumerJava.interrupt();
            consumerJava = null;
        }
        LOGGER.info("consumerJava released.");
    }

    @Override
    public void interrupt() {
        LOGGER.info("interrupt called: delegate interrupt to all keyProducers and producers");

        // Interrupt all Producers
        for (Producer producer : getAllProducers()) {
            LOGGER.info("Interrupt Producer: " + producer.toString());
            producer.interrupt();
            LOGGER.info("waitTillProducerNotRunning ...");
            producer.waitTillProducerNotRunning();
            producer.releaseProducer();
        }
        freeAllProducers();

        // Interrupt all KeyProducers
        for (KeyProducer keyProducer : getKeyProducers().values()) {
            LOGGER.info("Interrupt KeyProducer: " + keyProducer.toString());
            keyProducer.interrupt();
        }
        freeAllKeyProducers();

        LOGGER.info("All producers released and freed.");
    }

    /**
     * Returns a snapshot of all configured key producers.
     *
     * @return an immutable snapshot of all configured key producers keyed by id
     */
    public Map<String, KeyProducer> getKeyProducers() {
        return ImmutableMap.copyOf(keyProducers);
    }

    /**
     * Returns a list containing every configured producer.
     *
     * @return a new list containing every configured producer
     */
    public List<Producer> getAllProducers() {
        List<Producer> producers = new ArrayList<>();
        producers.addAll(javaProducers);
        producers.addAll(javaProducersSecretsFiles);
        producers.addAll(openCLProducers);
        return producers;
    }

    /**
     * Removes every registered producer instance.
     */
    public void freeAllProducers() {
        javaProducers.clear();
        javaProducersSecretsFiles.clear();
        openCLProducers.clear();
    }

    /**
     * Removes every registered key-producer instance.
     */
    public void freeAllKeyProducers() {
        keyProducers.clear();
    }

    /**
     * Returns a list containing every configured consumer.
     *
     * @return a new list containing every configured consumer (currently zero or one)
     */
    public List<Consumer> getAllConsumers() {
        List<Consumer> consumers = new ArrayList<>();
        if (consumerJava != null) {
            consumers.add(consumerJava);
        }
        return consumers;
    }
}
