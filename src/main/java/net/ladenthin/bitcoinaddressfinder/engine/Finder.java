// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.engine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.GpuFilterType;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.consumer.ConsumerJava;
import net.ladenthin.bitcoinaddressfinder.core.FireAndForget;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.core.ResultListener;
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
import net.ladenthin.bitcoinaddressfinder.keyproducer.SocketResultBroadcaster;
import net.ladenthin.bitcoinaddressfinder.keyproducer.WebSocketResultBroadcaster;
import net.ladenthin.bitcoinaddressfinder.keyproducer.ZmqResultBroadcaster;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.producer.Producer;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerJava;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerReleaser;
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

    /**
     * Result broadcasters built from the configured key producers, kept so the grid configuration
     * can be announced to them once the producers are known and so they can be closed on shutdown.
     */
    @ToString.Exclude
    private final List<ResultBroadcaster> resultBroadcasters = new ArrayList<>();

    /** ZeroMQ publishers, kept so their context can be closed on shutdown. */
    @ToString.Exclude
    private final List<ZmqResultBroadcaster> zmqBroadcasters = new ArrayList<>();

    /** Result servers, kept so their listening socket can be closed on shutdown. */
    @ToString.Exclude
    private final List<SocketResultBroadcaster> socketBroadcasters = new ArrayList<>();

    /** Guarantees each producer is released exactly once, even when two teardowns overlap. */
    private final ProducerReleaser producerReleaser = new ProducerReleaser();

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

        final ConsumerJava localConsumerJava = new ConsumerJava(
                localCConsumerJava, keyUtility, persistenceUtils, runtimeStatistics, createResultListeners());
        consumerJava = localConsumerJava;
        // Decide up-front whether the GPU pre-filter is needed so initLMDB() can build it while
        // LMDB is still open — a self-contained backend closes the env at the end of initLMDB().
        localConsumerJava.setGpuFilterRequested(isGpuFilterRequested());
        localConsumerJava.setGpuFilterType(resolveGpuFilterType());
        localConsumerJava.initLMDB();
        localConsumerJava.startConsumer();
        localConsumerJava.startStatisticsTimer();
    }

    /**
     * Builds the result listeners for every key producer configured to broadcast.
     *
     * <p>Wired here rather than inside the consumer because only this class knows both sides: the
     * consumer must not depend on the key-producer layer, and the transport lives there. The
     * consumer sees nothing but the {@code ResultListener} contract.
     *
     * @return the listeners to notify for every checked batch, possibly empty
     */
    private List<ResultListener> createResultListeners() {
        final List<ResultListener> listeners = new ArrayList<>();
        addWebSocketBroadcasters(listeners);
        addZmqBroadcasters(listeners);
        addSocketBroadcasters(listeners);
        return listeners;
    }

    /**
     * Adds a broadcaster for every WebSocket key producer configured to report results.
     *
     * <p>Shares the key producer's server, so ranges in and results out travel one port.
     *
     * @param listeners the list to add to
     */
    private void addWebSocketBroadcasters(List<ResultListener> listeners) {
        for (CKeyProducerJavaWebSocket config : finder.keyProducerJavaWebSocket) {
            if (!config.broadcastResults) {
                continue;
            }
            final String keyProducerId = config.keyProducerId;
            if (keyProducerId == null) {
                // An id-less entry never started a key producer; startKeyProducer already rejected it.
                continue;
            }
            final KeyProducer keyProducer = keyProducers.get(keyProducerId);
            if (!(keyProducer instanceof KeyProducerJavaWebSocket webSocketKeyProducer)) {
                LOGGER.warn(
                        "broadcastResults is set for keyProducerId {} but no WebSocket key producer was started;"
                                + " results will not be broadcast.",
                        keyProducerId);
                continue;
            }
            final WebSocketResultBroadcaster broadcaster =
                    new WebSocketResultBroadcaster(webSocketKeyProducer.getEndpoint());
            resultBroadcasters.add(broadcaster::announceConfiguration);
            listeners.add(broadcaster);
            LOGGER.info("Broadcasting results on the WebSocket of keyProducerId {}", keyProducerId);
        }
    }

    /**
     * Adds a publisher for every ZeroMQ key producer configured to report results.
     *
     * <p>Needs an address of its own: secrets arrive on a {@code PULL} socket, which cannot send.
     *
     * @param listeners the list to add to
     */
    private void addZmqBroadcasters(List<ResultListener> listeners) {
        for (CKeyProducerJavaZmq config : finder.keyProducerJavaZmq) {
            if (!config.broadcastResults) {
                continue;
            }
            final ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster(config.publishAddress);
            resultBroadcasters.add(broadcaster::announceConfiguration);
            zmqBroadcasters.add(broadcaster);
            listeners.add(broadcaster);
            LOGGER.info("Publishing results on {}", config.publishAddress);
        }
    }

    /**
     * Adds a result server for every plain-socket key producer configured to report results.
     *
     * <p>Serves on a port of its own, because the inbound side handles a single peer with a
     * different framing.
     *
     * @param listeners the list to add to
     */
    private void addSocketBroadcasters(List<ResultListener> listeners) {
        for (CKeyProducerJavaSocket config : finder.keyProducerJavaSocket) {
            if (!config.broadcastResults) {
                continue;
            }
            final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(config.resultPort);
            try {
                broadcaster.start();
            } catch (IOException e) {
                LOGGER.error("Could not serve results on port {}; continuing without it.", config.resultPort, e);
                continue;
            }
            resultBroadcasters.add(broadcaster::announceConfiguration);
            socketBroadcasters.add(broadcaster);
            listeners.add(broadcaster);
        }
    }

    /**
     * Tells the connected clients which grid the running producers expand a base into.
     *
     * <p>Called once the producers are configured, which is necessarily later than the moment the
     * WebSocket started accepting connections. A client cannot derive its step without it: every
     * incoming base is aligned down to a multiple of {@code 2^batchSizeInBits}, so a client stepping
     * by less would resubmit the same block and never cover the rest of its range.
     *
     * <p>When producers disagree on the grid size the largest is announced, which is the only choice
     * that cannot make ranges overlap.
     */
    private void announceGridConfigurationToBroadcasters() {
        if (resultBroadcasters.isEmpty()) {
            return;
        }
        final List<Producer> allProducers = getAllProducers();
        if (allProducers.isEmpty()) {
            return;
        }
        int batchSizeInBits = 0;
        boolean batchUsePrivateKeyIncrement = true;
        for (CProducer cProducer : configuredProducerConfigs()) {
            batchSizeInBits = Math.max(batchSizeInBits, cProducer.batchSizeInBits);
            batchUsePrivateKeyIncrement &= cProducer.batchUsePrivateKeyIncrement;
        }
        for (ResultBroadcaster broadcaster : resultBroadcasters) {
            broadcaster.announceConfiguration(batchSizeInBits, batchUsePrivateKeyIncrement);
        }
    }

    /**
     * Returns the configuration of every producer that was actually created.
     *
     * @return the producer configurations backing the running producers
     */
    private List<CProducer> configuredProducerConfigs() {
        final List<CProducer> configs = new ArrayList<>();
        configs.addAll(finder.producerJava);
        configs.addAll(finder.producerJavaSecretsFiles);
        configs.addAll(finder.producerOpenCL);
        return configs;
    }

    /**
     * Returns whether any OpenCL producer will run in compact (GPU pre-filter) mode, so the
     * consumer should build the Binary Fuse 8 GPU payload during {@code initLMDB()}.
     *
     * <p>Mirrors the vanity rule applied later by {@link #applyVanityFullTransferOverride()}:
     * vanity scanning forces every producer to full transfer, so no GPU filter is needed then.
     *
     * @return {@code true} if at least one OpenCL producer requests compact mode and vanity is off
     */
    private boolean isGpuFilterRequested() {
        CConsumerJava localCConsumerJava = finder.consumerJava;
        if (localCConsumerJava != null && localCConsumerJava.enableVanity) {
            return false;
        }
        List<CProducerOpenCL> configs = finder.producerOpenCL;
        return configs != null && configs.stream().anyMatch(c -> c.enableGpuFilter && !c.transferAll);
    }

    /**
     * Resolves the single fingerprint width the GPU pre-filter payload is built for.
     *
     * <p>The payload is built <b>once</b> and shared by every compact-mode producer (it is a full
     * LMDB scan and a multi-GB allocation), so one width has to win. The first compact-mode
     * producer's {@code gpuFilterType} decides; any other compact-mode producer configured for a
     * different width is overridden to match, with a warning — the same "config is corrected and
     * logged" pattern as {@link #applyVanityFullTransferOverride()}. Silently leaving a producer's
     * {@code gpuFilterType} pointing at a width it was not given would not fail: the kernel would
     * reinterpret the uploaded slots and report misses for addresses that are present.
     *
     * @return the width to build, defaulting to {@link GpuFilterType#FUSE_8} when no compact-mode
     *     producer is configured (in which case nothing is built anyway)
     */
    @VisibleForTesting
    GpuFilterType resolveGpuFilterType() {
        List<CProducerOpenCL> configs = finder.producerOpenCL;
        if (configs == null) {
            return GpuFilterType.FUSE_8;
        }
        GpuFilterType resolved = null;
        for (CProducerOpenCL config : configs) {
            if (!config.enableGpuFilter || config.transferAll) {
                continue;
            }
            if (resolved == null) {
                resolved = config.gpuFilterType;
            } else if (config.gpuFilterType != resolved) {
                LOGGER.warn(
                        "producerOpenCL.gpuFilterType differs between compact-mode producers ({} vs {}); "
                                + "the GPU filter is built once and shared, so {} is forced on all of them.",
                        config.gpuFilterType,
                        resolved,
                        resolved);
                config.gpuFilterType = resolved;
            }
        }
        return resolved == null ? GpuFilterType.FUSE_8 : resolved;
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

        // Vanity scanning needs every derived address on the CPU, so it is mutually exclusive
        // with the GPU compact filter mode; force full transfer first, then upload the filter
        // only to the producers that remain in compact mode.
        applyVanityFullTransferOverride();
        uploadGpuFilterToProducers();
        // Only now is the grid known; clients that connected earlier are caught up here.
        announceGridConfigurationToBroadcasters();
    }

    /**
     * Forces {@code transferAll = true} on every OpenCL producer when vanity scanning is enabled,
     * logging a warning that GPU compact filter mode is disabled.
     *
     * <p>Operates purely on configuration objects (it does not need the constructed producers),
     * so it is unit-testable in isolation.
     */
    @VisibleForTesting
    void applyVanityFullTransferOverride() {
        CConsumerJava cConsumer = finder.consumerJava;
        if (cConsumer == null || !cConsumer.enableVanity) {
            return;
        }
        for (CProducerOpenCL cProducerOpenCL : finder.producerOpenCL) {
            if (!cProducerOpenCL.transferAll) {
                LOGGER.warn("consumerJava.enableVanity is true; forcing producerOpenCL.transferAll=true. "
                        + "GPU compact filter mode is disabled because vanity scanning needs every derived "
                        + "address on the CPU.");
                cProducerOpenCL.transferAll = true;
            }
        }
    }

    /**
     * Builds the Binary Fuse 8 GPU pre-filter <em>once</em> and uploads it to every compact-mode
     * OpenCL producer.
     *
     * <p>The GPU pre-filter is decoupled from the CPU lookup backend: it is built purely as a
     * transient VRAM-upload artifact (independent of whether the CPU lookup is {@code LMDB_ONLY},
     * {@code BINARY_FUSE_8}, etc.) so the survivors that come back to the CPU are verified
     * directly against LMDB and never "double filtered". Routed through the producer (not the
     * consumer) to respect the layered architecture: the engine reads the filter payload from the
     * consumer, decomposes it into primitives, and hands them to the producer
     * ({@code engine -> producer -> opencl}). The host-side fingerprint array is released after
     * each producer copies it into device memory (see {@code ProducerOpenCL#initProducer}); the
     * upload happens only once per session.
     *
     * <p>Producers with {@code enableGpuFilter = false}, or forced to full transfer (e.g. by
     * vanity), are skipped. If no producer requests the filter, nothing is built.
     */
    private void uploadGpuFilterToProducers() {
        ConsumerJava localConsumer = consumerJava;
        if (localConsumer == null) {
            return;
        }
        List<CProducerOpenCL> configs = finder.producerOpenCL;
        boolean anyCompactMode = configs.stream().anyMatch(c -> c.enableGpuFilter && !c.transferAll);
        if (!anyCompactMode) {
            return;
        }

        // The payload was built once during the consumer's initLMDB() (while LMDB was open), at the
        // single width resolved by resolveGpuFilterType(); exactly one of the two is present.
        Optional<BinaryFuse8GpuFilterData> payload = localConsumer.getGpuFilterData();
        Optional<BinaryFuse16GpuFilterData> payload16 = localConsumer.getGpuFilterData16();
        if (payload16.isPresent()) {
            BinaryFuse16GpuFilterData data16 = payload16.get();
            for (int i = 0; i < openCLProducers.size(); i++) {
                stageGpuFilter16OnProducer(configs.get(i), openCLProducers.get(i), data16);
            }
        } else if (payload.isPresent()) {
            BinaryFuse8GpuFilterData data = payload.get();
            for (int i = 0; i < openCLProducers.size(); i++) {
                stageGpuFilterOnProducer(configs.get(i), openCLProducers.get(i), data);
            }
        } else {
            LOGGER.warn("producerOpenCL.enableGpuFilter is true but the GPU filter payload is absent; "
                    + "running full transfer.");
            return;
        }
        // Release the host-side copy now it is staged on every producer; each producer frees its
        // own copy after the one-time VRAM upload in initProducer().
        localConsumer.discardGpuFilterData();
    }

    /**
     * Stages the already-built Binary Fuse 8 filter payload on a single OpenCL producer, unless
     * the producer has {@code enableGpuFilter = false} or is forced to full transfer.
     *
     * @param config   the producer configuration
     * @param producer the producer to stage the filter on
     * @param data     the GPU-upload payload built once by {@link #uploadGpuFilterToProducers()}
     */
    private void stageGpuFilterOnProducer(
            CProducerOpenCL config, ProducerOpenCL producer, BinaryFuse8GpuFilterData data) {
        if (!config.enableGpuFilter || config.transferAll) {
            return;
        }
        long seed = data.seed();
        producer.setGpuFilter(
                data.fingerprints(),
                (int) seed,
                (int) (seed >>> 32),
                data.segmentLength(),
                data.segmentLengthMask(),
                data.segmentCountLength());
    }

    /**
     * Binary Fuse 16 counterpart of
     * {@link #stageGpuFilterOnProducer(CProducerOpenCL, ProducerOpenCL, BinaryFuse8GpuFilterData)}.
     *
     * <p>Routed to the producer's dedicated 16-bit staging method rather than through a shared,
     * width-agnostic one: the fingerprint width defines the filter, and staging a payload at the
     * wrong width produces silent false negatives instead of an error, so the width is kept
     * explicit and type-checked all the way down to the VRAM upload.
     *
     * @param config   the producer configuration
     * @param producer the producer to stage the filter on
     * @param data     the 16-bit GPU-upload payload built once by {@link #uploadGpuFilterToProducers()}
     */
    private void stageGpuFilter16OnProducer(
            CProducerOpenCL config, ProducerOpenCL producer, BinaryFuse16GpuFilterData data) {
        if (!config.enableGpuFilter || config.transferAll) {
            return;
        }
        long seed = data.seed();
        producer.setGpuFilter16(
                data.fingerprints(),
                (int) seed,
                (int) (seed >>> 32),
                data.segmentLength(),
                data.segmentLengthMask(),
                data.segmentCountLength());
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

        // The producers have stopped, but stopping them does not release what they own: a
        // ProducerOpenCL still holds its result-reader pool, and the key producers still hold the
        // reader threads behind their socket / websocket / ZeroMQ sources. Those threads are
        // non-daemon, so leaving them running keeps the JVM alive after Main#run has returned — the
        // process then logs its completion and never exits.
        //
        // Releasing them from the JVM shutdown hook, which is where interrupt() used to be reached
        // on this path, cannot work: a shutdown hook only starts once every non-daemon thread has
        // already ended, so the release that would end them would never be reached. The teardown
        // therefore belongs here, on the completion path itself. interrupt() is idempotent (it frees
        // the collections it walks), so the hook calling it again later is harmless.
        interrupt();

        // no producers are running anymore, the consumer can be interrupted
        final ConsumerJava localConsumerJava = consumerJava;
        if (localConsumerJava != null) {
            LOGGER.info("Interrupt: " + localConsumerJava);
            localConsumerJava.interrupt();
            consumerJava = null;
        }
        LOGGER.info("consumerJava released.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Two callers can arrive here at once: the teardown runs both on the ordinary completion path
     * ({@link #shutdownAndAwaitTermination()}) and from the JVM shutdown hook, and a {@code Ctrl+C}
     * landing while a finished run releases its producers is enough to overlap them. Releasing a
     * producer twice is not harmless — an OpenCL producer's second release is rejected by the driver
     * with {@code CL_INVALID_MEM_OBJECT}. {@link ProducerReleaser} owns that guarantee; clearing the
     * collections below is bookkeeping, not the safeguard.
     */
    @Override
    public void interrupt() {
        LOGGER.info("interrupt called: delegate interrupt to all keyProducers and producers");

        // Key producers first. A producer waiting for its next secret is parked inside the key
        // producer, and a key producer configured to block indefinitely (timeoutMillis < 0, which is
        // what an interactively driven source wants) only releases that wait when it is interrupted.
        // Waiting for the producers first would therefore wait for something that cannot happen
        // until after the wait — the producer would sit there until its shutdownTimeoutSeconds ran
        // out, turning every shutdown into a multi-minute stall.
        for (KeyProducer keyProducer : getKeyProducers().values()) {
            LOGGER.info("Interrupt KeyProducer: " + keyProducer.toString());
            keyProducer.interrupt();
        }
        freeAllKeyProducers();

        // Broadcasters own sockets of their own; releasing them here keeps the process able to exit.
        for (ZmqResultBroadcaster broadcaster : zmqBroadcasters) {
            broadcaster.close();
        }
        zmqBroadcasters.clear();
        for (SocketResultBroadcaster broadcaster : socketBroadcasters) {
            broadcaster.close();
        }
        socketBroadcasters.clear();
        resultBroadcasters.clear();

        // Interrupt and release all Producers, each exactly once even under a concurrent teardown.
        producerReleaser.release(getAllProducers());
        freeAllProducers();

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
