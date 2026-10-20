// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.ladenthin.bitcoinaddressfinder.AwaitTimeTest;
import net.ladenthin.bitcoinaddressfinder.AwaitTimeTests;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.LogLevelChange;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandomAlgorithm;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.keyproducer.*;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerIdIsNotUniqueException;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerIdNullException;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerIdUnknownException;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaSocketTest;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaTest;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.producer.Producer;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerState;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerStateProvider;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

public class FinderTest {

    /** How long the slow test listener holds the consumer per batch. */
    private static final long SLOW_LISTENER_MILLIS = 200;

    /** Grid of the GPU end-to-end test: large enough that reading it back takes noticeably long. */
    private static final int OPENCL_GRID_BITS = 18;

    @TempDir
    public Path folder;

    // <editor-fold defaultstate="collapsed" desc="interrupt">
    @Test
    public void interrupt_noProducersSet_noExceptionThrown() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        // act
        finder.interrupt();
        // assert
    }

    /** A ZeroMQ broadcaster whose first close() is held until released. */
    private static final class HeldCloseZmqBroadcaster extends ZmqResultBroadcaster {
        private final java.util.concurrent.CountDownLatch closeEntered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch releaseClose = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicBoolean held = new java.util.concurrent.atomic.AtomicBoolean();

        private HeldCloseZmqBroadcaster(String publishAddress) {
            super(publishAddress);
        }

        @Override
        public void close() {
            if (held.compareAndSet(false, true)) {
                closeEntered.countDown();
                try {
                    releaseClose.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            super.close();
        }
    }

    @Test
    public void interrupt_calledFromTwoThreadsAtOnce_neitherFails() throws Exception {
        // arrange: the shutdown hook and the main thread's shutdownAndAwaitTermination() both call
        // interrupt(); the first is held inside a broadcaster's close() while the second runs
        Finder finder = new Finder(new CFinder());
        HeldCloseZmqBroadcaster held = new HeldCloseZmqBroadcaster("inproc://finder-test-held");
        ZmqResultBroadcaster other = new ZmqResultBroadcaster("inproc://finder-test-other");
        java.lang.reflect.Field field = Finder.class.getDeclaredField("zmqBroadcasters");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ZmqResultBroadcaster> broadcasters = (List<ZmqResultBroadcaster>) field.get(finder);
        broadcasters.add(held);
        broadcasters.add(other);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Runnable interrupt = () -> {
            try {
                finder.interrupt();
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };

        // act
        Thread first = new Thread(interrupt, "first");
        first.start();
        assertThat(held.closeEntered.await(5, java.util.concurrent.TimeUnit.SECONDS), is(true));
        Thread second = new Thread(interrupt, "second");
        second.start();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (second.isAlive() && second.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            second.join(10);
        }
        held.releaseClose.countDown();
        first.join(5_000);
        second.join(5_000);

        // assert
        assertThat(failure.get(), is(nullValue()));
    }

    /** Collects everything a WebSocket server sends, in order. */
    private static final class RecordingWebSocketClient extends WebSocketClient {

        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        RecordingWebSocketClient(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            // nothing to do
        }

        @Override
        public void onMessage(String message) {
            messages.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            // nothing to do
        }

        @Override
        public void onError(Exception ex) {
            // recorded through the absence of expected messages
        }

        @Nullable
        String awaitMessage() throws InterruptedException {
            return messages.poll(15, TimeUnit.SECONDS);
        }
    }

    /**
     * A client of a WebSocket key producer steps by the grid it is told. Only the producers that key
     * producer feeds expand its bases, so only their grid counts: a producer on another key producer
     * must not change it, and among producers of different grids on the same key producer the smallest
     * is the step that leaves no gap (each base goes to one producer, and a larger grid aligns the base
     * down over the block, so the smaller step only checks blocks twice).
     */
    @ParameterizedTest
    @CsvSource({"random, 4", "ws, 4"})
    public void configureProducer_producersWithDifferentGrids_webSocketAnnouncesTheGridItsBasesAreExpandedTo(
            String keyProducerIdOfTheSecondProducer, int expectedBatchSizeInBits) throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        CKeyProducerJavaWebSocket webSocket = new CKeyProducerJavaWebSocket();
        webSocket.port = KeyProducerJavaSocketTest.findFreePort();
        webSocket.timeoutMillis = KeyProducerJavaTest.TIMEOUT_FOR_TERMINATE;
        webSocket.keyProducerId = "ws";
        webSocket.broadcastResults = true;
        cFinder.keyProducerJavaWebSocket.add(webSocket);
        configureKeyProducerJavaRandom("random", cFinder);

        CProducerJava fedByTheWebSocket = new CProducerJava();
        fedByTheWebSocket.keyProducerId = "ws";
        fedByTheWebSocket.batchSizeInBits = 4;
        cFinder.producerJava.add(fedByTheWebSocket);
        CProducerJava second = new CProducerJava();
        second.keyProducerId = keyProducerIdOfTheSecondProducer;
        second.batchSizeInBits = 8;
        cFinder.producerJava.add(second);
        configureConsumerJava(cFinder);

        Finder finder = new Finder(cFinder);
        RecordingWebSocketClient client = new RecordingWebSocketClient(URI.create("ws://127.0.0.1:" + webSocket.port));
        try {
            finder.startKeyProducer();
            finder.startConsumer();
            finder.configureProducer();
            ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", webSocket.port, 15_000);

            // act
            assertThat(client.connectBlocking(15, TimeUnit.SECONDS), is(true));
            String greeting = client.awaitMessage();

            // assert
            assertThat(greeting, is(notNullValue()));
            JsonNode config = new ObjectMapper().readTree(greeting);
            assertThat(config.get("type").asText(), is(equalTo("config")));
            assertThat(config.get("batchSizeInBits").asInt(), is(equalTo(expectedBatchSizeInBits)));
        } finally {
            client.closeBlocking();
            finder.interrupt();
            finder.shutdownAndAwaitTermination();
        }
    }

    @Test
    public void interrupt_producersSetAndNotInitialized_noExceptionThrown() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        // act
        finder.interrupt();
        finder.shutdownAndAwaitTermination();
        // assert
    }

    @Test
    public void interrupt_consumerStarted_consumerNotStopped() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);

        // consumer java configuration
        configureConsumerJava(cFinder);

        Finder finder = new Finder(cFinder);
        finder.startConsumer();
        // act
        finder.interrupt();
        finder.shutdownAndAwaitTermination();
        // assert
    }

    /**
     * A {@code Ctrl+C} while the consumer loads its database is the common case: that load is the
     * long step of the startup. The interrupt frees the key producers, and the startup that resumes
     * afterwards must not report their absence as a configuration error.
     */
    @Test
    public void configureProducer_interruptedWhileConsumerStarted_doesNotReportUnknownKeyProducer() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        String keyProducerId = "exampleId";
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId;
        cFinder.producerJava.add(cProducerJava);
        configureKeyProducerJavaRandom(keyProducerId, cFinder);
        configureConsumerJava(cFinder);

        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.interrupt();

        // act
        assertDoesNotThrow(finder::configureProducer);

        // assert
        assertThat(finder.getAllProducers(), is(empty()));
        finder.shutdownAndAwaitTermination();
    }

    /**
     * An interrupt that lands before the consumer started must keep the consumer from starting: its
     * database load is the longest step of the startup, and nothing would ever use the result.
     */
    @Test
    public void startConsumer_interruptedBefore_consumerNotStarted() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.interrupt();

        // act
        finder.startConsumer();

        // assert
        assertThat(finder.getAllConsumers(), is(empty()));
        finder.shutdownAndAwaitTermination();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="shutdownAndAwaitTermination">
    @Test
    public void shutdownAndAwaitTermination_noProducersSet_shutdownCalled() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        ExecutorService injectedExecutor = Executors.newCachedThreadPool();
        Finder finder = new Finder(cFinder, injectedExecutor);
        // pre-assert
        assertThat(injectedExecutor.isTerminated(), is(equalTo(Boolean.FALSE)));
        // act
        finder.shutdownAndAwaitTermination();
        // assert
        assertThat(injectedExecutor.isTerminated(), is(equalTo(Boolean.TRUE)));
    }

    @Test
    public void shutdownAndAwaitTermination_producersSetAndNotInitialized_shutdownCalled() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);
        configureConsumerJava(cFinder);
        ExecutorService injectedExecutor = Executors.newCachedThreadPool();
        Finder finder = new Finder(cFinder, injectedExecutor);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        // act
        finder.shutdownAndAwaitTermination();
        // assert
        assertThat(injectedExecutor.isTerminated(), is(equalTo(Boolean.TRUE)));
    }

    @AwaitTimeTest
    @Test
    public void shutdownAndAwaitTermination_producersSetAndInitialized_shutdownCalledAndAwaitTermination()
            throws Exception {
        // Attention: During the long duration, this test produce a lot of debug and warn output, prevent it by set the
        // log details
        new LogLevelChange().turnOff();

        // arrange
        CFinder cFinder = new CFinder();
        // Shorten the await-terminate timeout from the production default (100k years) so the
        // test's wait-then-time-out branch fires in seconds; injected via config, no static
        // mutation, no test-order coupling.
        cFinder.awaitTerminateSeconds = AwaitTimeTests.AWAIT_DURATION.toSeconds();
        String keyProducerId = "exampleId";
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId;
        cProducerJava.runOnce = false;
        cFinder.producerJava.add(cProducerJava);
        configureKeyProducerJavaRandom(keyProducerId, cFinder);

        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        finder.initProducer();
        finder.startProducer();

        // act
        long beforeAct = System.currentTimeMillis();
        finder.shutdownAndAwaitTermination();

        // assert
        long afterAct = System.currentTimeMillis();
        Duration waitTime = Duration.ofMillis(afterAct - beforeAct);

        // assert the waiting time is over, substract imprecision
        assertThat(waitTime, is(greaterThan(AwaitTimeTests.AWAIT_DURATION.minus(AwaitTimeTests.IMPRECISION))));
    }
    /**
     * A completed run must leave nothing of the finder's own running.
     *
     * <p>This is the unit-level guard for a reproduced defect: after a producer exhausted its key
     * range the process logged {@code Main#run end.} and then never exited. The threads holding it
     * open were ones the finder had started and never released - a producer's result-reader pool and
     * the key producers' reader threads, all non-daemon. Releasing them happened only in
     * {@link Finder#interrupt()}, which on the ordinary completion path ran solely from the JVM
     * shutdown hook; and a shutdown hook cannot run until every non-daemon thread has already ended,
     * so the release that would have ended them was unreachable.
     *
     * <p>The two collections asserted here are emptied by exactly that release step, which makes them
     * the observable proxy for "the JVM can now exit" in a test that must not depend on a GPU or on
     * counting live threads.
     */
    /**
     * The result of every checked batch must reach the broadcasters before they close: the last
     * batches of a finite run are the completion signal a client waits for. The consumer is slowed
     * down by a listener, so it is still draining its queue when the producers are done.
     */
    @Test
    public void shutdownAndAwaitTermination_consumerStillDraining_everyBatchIsReportedBeforeTheBroadcastersClose()
            throws Exception {
        // arrange
        final int producers = 4;
        CFinder cFinder = new CFinder();
        String keyProducerId = "exampleId";
        for (int i = 0; i < producers; i++) {
            final CProducerJava cProducerJava = new CProducerJava();
            cProducerJava.keyProducerId = keyProducerId;
            // One batch per producer, then the producer ends by itself.
            cProducerJava.runOnce = true;
            cFinder.producerJava.add(cProducerJava);
        }
        configureKeyProducerJavaRandom(keyProducerId, cFinder);
        configureConsumerJava(cFinder);
        Objects.requireNonNull(cFinder.consumerJava).threads = 1;

        Finder finder = new Finder(cFinder);
        final AtomicInteger reported = new AtomicInteger();
        final AtomicInteger reportedWhenClosing = new AtomicInteger(-1);
        finder.addResultListener(batchResult -> {
            try {
                Thread.sleep(SLOW_LISTENER_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reported.incrementAndGet();
        });
        finder.setBeforeResultBroadcastersClosed(() -> reportedWhenClosing.compareAndSet(-1, reported.get()));
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        finder.initProducer();
        finder.startProducer();

        // act
        finder.shutdownAndAwaitTermination();

        // assert
        assertThat(reported.get(), is(equalTo(producers)));
        assertThat(reportedWhenClosing.get(), is(equalTo(producers)));
    }

    /**
     * The same guarantee for the GPU producer, whose batches reach the consumer asynchronously: a
     * result reader thread reads the grid back and enqueues it after {@code processSecretBase}
     * returned. Before the producer waited for its readers, the finder released the consumer while a
     * reader was still busy and the last batch of a finite run was never checked or reported — lost in
     * 4 of 5 runs on pocl. Probabilistic in the red direction only: with the wait in place every batch
     * is reported, every time.
     */
    @Test
    @OpenCLTest
    public void shutdownAndAwaitTermination_openClProducerFinished_everyBatchIsReported() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        final int producers = 2;
        CFinder cFinder = new CFinder();
        String keyProducerId = "exampleId";
        for (int i = 0; i < producers; i++) {
            final CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
            cProducerOpenCL.keyProducerId = keyProducerId;
            cProducerOpenCL.runOnce = true;
            cProducerOpenCL.batchSizeInBits = OPENCL_GRID_BITS;
            cProducerOpenCL.transferAll = true;
            cFinder.producerOpenCL.add(cProducerOpenCL);
        }
        configureKeyProducerJavaRandom(keyProducerId, cFinder);
        configureConsumerJava(cFinder);
        Objects.requireNonNull(cFinder.consumerJava).threads = 1;

        Finder finder = new Finder(cFinder);
        final AtomicInteger reported = new AtomicInteger();
        finder.addResultListener(batchResult -> reported.incrementAndGet());
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        finder.initProducer();
        finder.startProducer();

        // act
        finder.shutdownAndAwaitTermination();

        // assert
        assertThat(reported.get(), is(equalTo(producers)));
    }

    /** A start that fails after the consumer started must not leave its non-daemon threads running. */
    @Test
    public void abortStart_consumerStarted_consumerIsReleased() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startConsumer();

        // pre-assert
        assertThat(finder.isConsumerRunning(), is(true));

        // act
        finder.abortStart();

        // assert
        assertThat(finder.isConsumerRunning(), is(false));
    }

    /**
     * The teardown of the producers can fail (the producer releaser rethrows what a release threw).
     * The consumer must be released all the same, or its non-daemon threads keep the JVM alive after
     * the start error has been reported.
     */
    @Test
    public void abortStart_interruptThrows_consumerIsReleasedAnyway() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        configureConsumerJava(cFinder);
        final IllegalStateException teardownFailure = new IllegalStateException("teardown failed");
        Finder finder = new Finder(cFinder) {
            @Override
            public void interrupt() {
                super.interrupt();
                throw teardownFailure;
            }
        };
        finder.startConsumer();

        // act
        IllegalStateException thrown = assertThrows(IllegalStateException.class, finder::abortStart);

        // assert
        assertThat(thrown, is(sameInstance(teardownFailure)));
        assertThat(finder.isConsumerRunning(), is(false));
    }

    @Test
    public void shutdownAndAwaitTermination_producerFinished_producersAndKeyProducersReleased() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        String keyProducerId = "exampleId";
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId;
        // One batch, then the producer ends by itself - the completion path under test.
        cProducerJava.runOnce = true;
        cFinder.producerJava.add(cProducerJava);
        configureKeyProducerJavaRandom(keyProducerId, cFinder);
        configureConsumerJava(cFinder);

        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        finder.initProducer();
        finder.startProducer();

        // pre-assert
        assertThat(finder.getAllProducers(), is(not(empty())));
        assertThat(finder.getKeyProducers().values(), is(not(empty())));

        // act
        finder.shutdownAndAwaitTermination();

        // assert
        assertThat(finder.getAllProducers(), is(empty()));
        assertThat(finder.getKeyProducers().values(), is(empty()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getAllProducers">
    @Test
    public void getAllProducers_noProducersSet_returnEmptyList() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, is(empty()));
    }

    @Test
    public void getAllProducers_producersSetAndNotInitialized_returnList() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, hasSize(3));
        finder.interrupt();
        finder.shutdownAndAwaitTermination();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="startKeyProducer">
    @Test
    public void startKeyProducer_keyProducerIdIsNull_ExceptionThrown() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        configureKeyProducerJavaRandom(null, cFinder);

        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        // act
        assertThrows(KeyProducerIdNullException.class, () -> finder.startKeyProducer());
    }

    @Test
    public void startKeyProducer_keyProducerIdIsNotUnique_ExceptionThrown() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        String sameIdTwice = "123";
        configureKeyProducerJavaRandom(sameIdTwice, cFinder);
        configureKeyProducerJavaRandom(sameIdTwice, cFinder);

        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        // act
        assertThrows(KeyProducerIdIsNotUniqueException.class, () -> finder.startKeyProducer());
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="configureProducer">
    /**
     * The consumer's database load is the long step of the startup, so a producer that names no
     * configured key producer must fail before it, not after: {@code Main} starts the key producers,
     * then the consumer, then the producers.
     */
    @Test
    public void startKeyProducer_producerKeyProducerIdIsNull_ExceptionThrown() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.runOnce = false;
        // null is not valid or will find any other id
        cProducerJava.keyProducerId = null;
        cFinder.producerJava.add(cProducerJava);
        configureKeyProducerJavaRandom("unknownId", cFinder);

        Finder finder = new Finder(cFinder);

        // act
        assertThrows(KeyProducerIdUnknownException.class, finder::startKeyProducer);
        finder.shutdownAndAwaitTermination();
    }

    @Test
    public void startKeyProducer_producerKeyProducerIdHasATypo_ExceptionThrown() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = "exampleld";
        cFinder.producerJava.add(cProducerJava);
        configureKeyProducerJavaRandom("exampleId", cFinder);

        Finder finder = new Finder(cFinder);

        // act
        assertThrows(KeyProducerIdUnknownException.class, finder::startKeyProducer);
        finder.shutdownAndAwaitTermination();
    }

    @Test
    public void startKeyProducer_producerBatchSizeOutOfRange_ExceptionThrown() throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = "exampleId";
        cProducerJava.batchSizeInBits = -1;
        cFinder.producerJava.add(cProducerJava);
        configureKeyProducerJavaRandom("exampleId", cFinder);

        Finder finder = new Finder(cFinder);

        // act
        assertThrows(IllegalArgumentException.class, finder::startKeyProducer);
        finder.shutdownAndAwaitTermination();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="testFullCycle">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_KEY_PRODUCER_TYPES)
    public void testFullCycle_keyProducerJavaSetAndInitialized_statesCorrect(
            CommonDataProvider.KeyProducerTypesLocal keyProducerType) throws Exception {
        // arrange
        CFinder cFinder = new CFinder();
        String keyProducerId = "exampleId";
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId;
        cProducerJava.runOnce = false;
        cFinder.producerJava.add(cProducerJava);
        final Class keyProducerClass;
        switch (keyProducerType) {
            case KeyProducerJavaRandom:
                keyProducerClass = KeyProducerJavaRandom.class;
                configureKeyProducerJavaRandom(keyProducerId, cFinder);
                break;
            case KeyProducerJavaIncremental:
                keyProducerClass = KeyProducerJavaIncremental.class;
                configureKeyProducerJavaIncremental(keyProducerId, cFinder);
                break;
            case KeyProducerJavaBip39:
                keyProducerClass = KeyProducerJavaBip39.class;
                configureKeyProducerJavaBip39(keyProducerId, cFinder);
                break;
            case KeyProducerJavaSocket:
                keyProducerClass = KeyProducerJavaSocket.class;
                configureKeyProducerJavaSocket(keyProducerId, cFinder);
                break;
            case KeyProducerJavaWebSocket:
                keyProducerClass = KeyProducerJavaWebSocket.class;
                configureKeyProducerJavaWebSocket(keyProducerId, cFinder);
                break;
            case KeyProducerJavaZmq:
                keyProducerClass = KeyProducerJavaZmq.class;
                configureKeyProducerJavaZmq(keyProducerId, cFinder);
                break;
            default:
                throw new IllegalArgumentException("Unknown KeyProducerType: " + keyProducerType);
        }

        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        // act and assert the full cycle
        {
            // pre-assert
            assertThat(finder.getKeyProducers().keySet(), hasSize(0));
            // act
            finder.startKeyProducer();
            // assert
            assertThat(finder.getKeyProducers().keySet(), hasSize(1));
        }
        // assert the constructed producer is of the expected concrete class
        {
            KeyProducer keyProducer =
                    Objects.requireNonNull(finder.getKeyProducers().get(keyProducerId));
            assertThat(keyProducer.getClass().getCanonicalName(), is(keyProducerClass.getCanonicalName()));
        }
        {
            // pre-assert
            assertThat(finder.getAllConsumers(), hasSize(0));
            // act
            finder.startConsumer();
            // assert
            assertThat(finder.getAllConsumers(), hasSize(1));
        }

        {
            // pre-assert
            assertThat(finder.getAllProducers(), hasSize(0));
            // act
            finder.configureProducer();
            // assert
            assertThat(finder.getAllProducers(), hasSize(1));
            assertProducerState(finder.getAllProducers(), ProducerState.UNINITIALIZED);
        }

        {
            // act
            finder.initProducer();
            // assert
            assertProducerState(finder.getAllProducers(), ProducerState.INITIALIZED);
        }
        // catch the reference, it is not possible to get the reference afterwards the interrupt
        final List<Producer> allProducers = finder.getAllProducers();
        {
            // act
            finder.startProducer();
            // wait
            Thread.sleep(Duration.ofSeconds(1L));

            // assert
            assertProducerState(allProducers, ProducerState.RUNNING);
        }
        {
            // act
            finder.interrupt();
            // assert
            assertThat(finder.getAllProducers(), hasSize(0));
            assertProducerState(allProducers, ProducerState.NOT_RUNNING);
        }

        {
            // pre-assert
            assertThat(finder.getAllConsumers(), hasSize(1));
            // act
            finder.shutdownAndAwaitTermination();
            // assert
            assertThat(finder.getAllConsumers(), hasSize(0));
        }
    }
    // </editor-fold>

    @Test
    public void startKeyProducer_allConfiguredKeyProducerTypes_haveInstances() throws IOException {
        // Arrange
        CFinder cFinder = new CFinder();

        // Configure one instance for each KeyProducer type
        // 1. JavaRandom
        CKeyProducerJavaRandom javaRandom = new CKeyProducerJavaRandom();
        javaRandom.keyProducerId = "randomId";
        javaRandom.randomAlgorithm = CKeyProducerJavaRandomAlgorithm.RANDOM_CUSTOM_SEED;
        javaRandom.customSeed = 123L;
        cFinder.keyProducerJavaRandom.add(javaRandom);

        // 2. JavaBip39
        CKeyProducerJavaBip39 bip39 = new CKeyProducerJavaBip39();
        bip39.keyProducerId = "bip39Id";
        bip39.mnemonic = CKeyProducerJavaBip39.DEFAULT_MNEMONIC;
        cFinder.keyProducerJavaBip39.add(bip39);

        // 3. JavaIncremental
        CKeyProducerJavaIncremental incremental = new CKeyProducerJavaIncremental();
        incremental.keyProducerId = "incrementalId";
        cFinder.keyProducerJavaIncremental.add(incremental);

        // 4. JavaSocket
        CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.keyProducerId = "socketId";
        socket.port = KeyProducerJavaSocketTest.findFreePort();
        cFinder.keyProducerJavaSocket.add(socket);

        // 5. JavaWebSocket
        CKeyProducerJavaWebSocket webSocket = new CKeyProducerJavaWebSocket();
        webSocket.keyProducerId = "webSocketId";
        webSocket.port = KeyProducerJavaSocketTest.findFreePort();
        cFinder.keyProducerJavaWebSocket.add(webSocket);

        // 6. JavaZmq — bind to a ZMQ ephemeral port ("*"), not a pre-picked one. The producer binds
        // in BIND mode (default); pre-picking a port via findFreePort() (open ServerSocket(0), close,
        // reuse the number) has a TOCTOU race — the port can be taken between the close and the ZMQ
        // bind, which intermittently failed CI with "ZMQ Errno 48" (EADDRINUSE). ZMQ resolves "*" to a
        // free port atomically at bind time. This test only asserts the producer instance exists, so it
        // never needs the concrete port.
        CKeyProducerJavaZmq zmq = new CKeyProducerJavaZmq();
        zmq.keyProducerId = "zmqId";
        zmq.address = "tcp://127.0.0.1:*";
        cFinder.keyProducerJavaZmq.add(zmq);

        Finder finder = new Finder(cFinder);

        // Act
        finder.startKeyProducer();

        // Assert
        assertThat(
                finder.getKeyProducers().keySet(),
                hasItems("randomId", "bip39Id", "incrementalId", "socketId", "webSocketId", "zmqId"));

        // Additionally assert each instance is of expected class type
        assertThat(finder.getKeyProducers().get("randomId"), instanceOf(KeyProducerJavaRandom.class));
        assertThat(finder.getKeyProducers().get("bip39Id"), instanceOf(KeyProducerJavaBip39.class));
        assertThat(finder.getKeyProducers().get("incrementalId"), instanceOf(KeyProducerJavaIncremental.class));
        assertThat(finder.getKeyProducers().get("socketId"), instanceOf(KeyProducerJavaSocket.class));
        assertThat(finder.getKeyProducers().get("webSocketId"), instanceOf(KeyProducerJavaWebSocket.class));
        assertThat(finder.getKeyProducers().get("zmqId"), instanceOf(KeyProducerJavaZmq.class));

        // Interrupt and free producers
        finder.interrupt();

        // Assert keyProducers map is empty after interrupt
        assertThat(finder.getKeyProducers().keySet(), is(empty()));
    }

    private void configureProducerWithExamples(CFinder cFinder) {
        String keyProducerId_producerJava = "exampleId_producerJava";
        String keyProducerId_producerJavaSecretsFiles = "exampleId_producerJavaSecretsFiles";
        String keyProducerId_producerOpenCL = "exampleId_producerOpenCL";
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId_producerJava;
        cFinder.producerJava.add(cProducerJava);
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        cProducerJavaSecretsFiles.keyProducerId = keyProducerId_producerJavaSecretsFiles;
        cFinder.producerJavaSecretsFiles.add(cProducerJavaSecretsFiles);
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.keyProducerId = keyProducerId_producerOpenCL;
        cFinder.producerOpenCL.add(cProducerOpenCL);
        configureKeyProducerJavaRandom(keyProducerId_producerJava, cFinder);
        configureKeyProducerJavaRandom(keyProducerId_producerJavaSecretsFiles, cFinder);
        configureKeyProducerJavaRandom(keyProducerId_producerOpenCL, cFinder);
    }

    private void configureKeyProducerJavaRandom(@Nullable String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.randomAlgorithm = CKeyProducerJavaRandomAlgorithm.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cFinder.keyProducerJavaRandom.add(cKeyProducerJavaRandom);
    }

    private void configureKeyProducerJavaIncremental(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaIncremental incremental = new CKeyProducerJavaIncremental();
        incremental.keyProducerId = keyProducerId;
        cFinder.keyProducerJavaIncremental.add(incremental);
    }

    private void configureKeyProducerJavaBip39(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaBip39 bip39 = new CKeyProducerJavaBip39();
        bip39.keyProducerId = keyProducerId;
        bip39.mnemonic = CKeyProducerJavaBip39.DEFAULT_MNEMONIC;
        cFinder.keyProducerJavaBip39.add(bip39);
    }

    private void configureKeyProducerJavaSocket(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.port = KeyProducerJavaSocketTest.findFreePort();
        socket.timeoutMillis = KeyProducerJavaTest.TIMEOUT_FOR_TERMINATE;
        socket.keyProducerId = keyProducerId;
        cFinder.keyProducerJavaSocket.add(socket);
    }

    private void configureKeyProducerJavaWebSocket(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaWebSocket socket = new CKeyProducerJavaWebSocket();
        socket.port = KeyProducerJavaSocketTest.findFreePort();
        socket.timeoutMillis = KeyProducerJavaTest.TIMEOUT_FOR_TERMINATE;
        socket.keyProducerId = keyProducerId;
        cFinder.keyProducerJavaWebSocket.add(socket);
    }

    private void configureKeyProducerJavaZmq(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaZmq zmq = new CKeyProducerJavaZmq();
        // Bind to a ZMQ ephemeral port ("*") rather than a pre-picked findFreePort() number, which has
        // a TOCTOU race (the port can be taken between the probe's ServerSocket close and the ZMQ bind
        // -> "ZMQ Errno 48" EADDRINUSE). ZMQ resolves "*" atomically at bind time; these tests only
        // exercise the producer lifecycle and never connect a client, so the concrete port is unneeded.
        zmq.address = "tcp://127.0.0.1:*";
        zmq.timeoutMillis = KeyProducerJavaTest.TIMEOUT_FOR_TERMINATE;
        zmq.keyProducerId = keyProducerId;
        cFinder.keyProducerJavaZmq.add(zmq);
    }

    private void configureConsumerJava(CFinder cFinder) throws IOException {
        boolean compressed = false;
        boolean useStaticAmount = true;
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();

        cFinder.consumerJava = cConsumerJava;
    }

    private static void assertProducerState(
            List<Producer> producerStateProviders, ProducerState expectedProducerState) {
        for (ProducerStateProvider producerStateProvider : producerStateProviders) {
            assertThat(producerStateProvider.getState(), is(equalTo(expectedProducerState)));
        }
    }

    // <editor-fold defaultstate="collapsed" desc="vanity forces transferAll (Step H)">
    @Test
    public void applyVanityFullTransferOverride_vanityEnabled_forcesTransferAllTrue() {
        // arrange
        CFinder cFinder = new CFinder();
        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.enableVanity = true;
        cFinder.consumerJava = cConsumerJava;
        CProducerOpenCL alreadyFull = new CProducerOpenCL();
        alreadyFull.transferAll = true;
        CProducerOpenCL compactByDefault = new CProducerOpenCL();
        cFinder.producerOpenCL = List.of(alreadyFull, compactByDefault);
        Finder finder = new Finder(cFinder);

        // act
        finder.applyVanityFullTransferOverride();

        // assert — both producers run full transfer; compact mode is disabled under vanity
        assertThat(alreadyFull.transferAll, is(true));
        assertThat(compactByDefault.transferAll, is(true));
    }

    @Test
    public void applyVanityFullTransferOverride_vanityDisabled_leavesTransferAllFalse() {
        // arrange
        CFinder cFinder = new CFinder();
        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.enableVanity = false;
        cFinder.consumerJava = cConsumerJava;
        CProducerOpenCL producer = new CProducerOpenCL();
        cFinder.producerOpenCL = List.of(producer);
        Finder finder = new Finder(cFinder);

        // act
        finder.applyVanityFullTransferOverride();

        // assert — without vanity the producer keeps its default (compact-capable) transferAll
        assertThat(producer.transferAll, is(false));
    }
    // </editor-fold>
}
