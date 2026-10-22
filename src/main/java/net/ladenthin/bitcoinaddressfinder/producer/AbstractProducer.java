// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.secret.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyValidator;
import org.apache.commons.codec.binary.Hex;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link Producer} implementations providing the common state machine,
 * key consumption loop and shutdown handling.
 */
@ToString
public abstract class AbstractProducer implements Producer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProducer.class);

    /**
     * Class-name prefix shared by all built-in key producers; stripped to derive the short
     * strategy label (e.g. {@code KeyProducerJavaRandom} &rarr; {@code Random}).
     */
    private static final String KEY_PRODUCER_CLASS_PREFIX = "KeyProducerJava";

    /**
     * Grace {@link #waitTillProducerNotRunning()} adds to the shutdown deadline. A producer gives up on
     * its pending batches exactly at the deadline and reports itself stopped right after; a wait that
     * ended at the same instant saw it {@link ProducerState#RUNNING} a moment before it stopped, logged
     * that and left its resources unreleased. One second is far more than that hand-over takes and far
     * less than any sensible {@code shutdownTimeoutSeconds}.
     */
    static final Duration SHUTDOWN_WAIT_GRACE = Duration.ofSeconds(1);

    /**
     * Value of {@link #shutdownDeadlineNanos} while no interrupt was issued. A real deadline that lands
     * on it (one chance in 2^64) counts as not recorded: the waits then take the whole timeout, as they
     * do without an interrupt.
     */
    private static final long NO_SHUTDOWN_DEADLINE = Long.MIN_VALUE;

    /**
     * Counted down once when {@link #run()} transitions to
     * {@link ProducerState#NOT_RUNNING}. {@link #waitTillProducerNotRunning()}
     * awaits this latch until the shutdown deadline plus {@link #SHUTDOWN_WAIT_GRACE}
     * instead of spinning on the {@code state} field.
     */
    @ToString.Exclude
    protected final CountDownLatch notRunningLatch = new CountDownLatch(1);

    /** Flag indicating whether the producer is currently running. */
    protected final AtomicBoolean running = new AtomicBoolean(false);

    /** Configuration backing this producer. */
    protected final CProducer cProducer;

    /**
     * Downstream consumer that receives generated keys.
     *
     * <p>Excluded from {@link ToString} — stateful coordinator (executors + queue +
     * lifecycle), recursive/heavy in logs.
     */
    @ToString.Exclude
    protected final Consumer consumer;
    /** Cryptographic helper used to encode/decode private keys. */
    protected final KeyUtility keyUtility;

    /**
     * Strategy that supplies the next secret batch.
     *
     * <p>Excluded from {@link ToString} — stateful coordinator covered by its own
     * {@code toString}; including here would produce recursive output.
     */
    @ToString.Exclude
    protected final KeyProducer keyProducer;
    /** Helper for bit-level batch size arithmetic. */
    protected final BitHelper bitHelper;
    /** Validator that rejects or replaces invalid secp256k1 private keys. */
    protected final PrivateKeyValidator privateKeyValidator;

    /** Current life-cycle state. */
    protected volatile ProducerState state = ProducerState.UNINITIALIZED;

    /**
     * The most recent failure this producer hit, fatal or swallowed, or {@code null} if none.
     *
     * <p>Volatile because it is written on the producer's executor thread and read by whoever
     * started it — the orchestrator that needs to know whether a measurement window contained a
     * healthy producer or one that was failing throughout.
     *
     * <p>Excluded from {@link ToString} — a stack trace does not belong in a one-line producer
     * description.
     */
    @ToString.Exclude
    private volatile @Nullable Throwable lastFailure;

    /**
     * Flag controlling the main {@link #run()} loop; cleared via {@link #interrupt()}.
     *
     * <p>Excluded from {@link ToString} — uninformative lifecycle flag.
     */
    @ToString.Exclude
    protected final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * {@link System#nanoTime()} at which this producer's shutdown ends: recorded once, by the first
     * {@link #interrupt()}, {@code shutdownTimeoutSeconds} ahead; {@link #NO_SHUTDOWN_DEADLINE} before.
     * The wait for the pending batches and the orchestrator's wait in
     * {@link #waitTillProducerNotRunning()} both run to it, which is what keeps them from giving up on
     * each other.
     *
     * <p>Excluded from {@link ToString} -- an absolute nanosecond stamp says nothing in a log.
     */
    @ToString.Exclude
    private final AtomicLong shutdownDeadlineNanos = new AtomicLong(NO_SHUTDOWN_DEADLINE);

    /**
     * Shared runtime metrics sink. Producers increment their per-producer batch counter
     * here; the consumer's statistics line reads it. Excluded from {@link ToString} — it
     * is a shared, mutable aggregate, not part of this producer's own identity.
     */
    @ToString.Exclude
    protected final RuntimeStatistics runtimeStatistics;

    /**
     * Creates a new producer with the given collaborators.
     *
     * @param cProducer        the producer configuration
     * @param consumer         the downstream consumer
     * @param keyUtility       the cryptographic helper
     * @param keyProducer      the secret supplying strategy
     * @param bitHelper        the bit/batch-size helper
     * @param runtimeStatistics shared runtime metrics sink for per-producer batch counts
     */
    public AbstractProducer(
            CProducer cProducer,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper,
            RuntimeStatistics runtimeStatistics) {
        this.cProducer = cProducer;
        this.consumer = consumer;
        this.keyUtility = keyUtility;
        this.keyProducer = keyProducer;
        this.bitHelper = bitHelper;
        this.privateKeyValidator = new PrivateKeyValidator();
        this.runtimeStatistics = runtimeStatistics;
    }

    /**
     * Returns the compute backend of this producer, used to label its runtime statistics.
     *
     * @return {@link ProducerType#CPU} or {@link ProducerType#GPU}
     */
    protected abstract ProducerType producerType();

    /**
     * Builds the per-producer statistics label
     * ({@code "<keyProducerId> (<Strategy>, <CPU|GPU>)"}), e.g.
     * {@code "exampleRandom (Random, GPU)"}. The strategy is derived from the bound
     * {@link KeyProducer} class; the backend from {@link #producerType()}.
     *
     * @return the label distinguishing this producer in the runtime statistics
     */
    protected String producerLabel() {
        String simpleName = keyProducer.getClass().getSimpleName();
        String strategy = simpleName.startsWith(KEY_PRODUCER_CLASS_PREFIX)
                ? simpleName.substring(KEY_PRODUCER_CLASS_PREFIX.length())
                : simpleName;
        return String.valueOf(cProducer.keyProducerId) + " (" + strategy + ", " + producerType() + ")";
    }

    @Override
    public void initProducer() throws Exception {
        LOGGER.info("Init producer.");
        this.state = ProducerState.INITIALIZED;
    }

    @Override
    public void releaseProducer() {
        LOGGER.info("Release producer.");
    }

    @Override
    public void run() {
        if (!shouldRun.get()) {
            LOGGER.info("Producer was interrupted before it started running.");
            signalNotRunning();
            return;
        }
        if (state != ProducerState.INITIALIZED) {
            throw new IllegalStateException("Producer not initialized. Current state: " + state);
        }
        state = ProducerState.RUNNING;
        while (shouldRun.get()) {
            try {
                produceKeys();
            } catch (Exception e) {
                LOGGER.error("Error in produceKeys", e);
                // Recorded as well as logged: the exception dies on this thread, and the thread that
                // started this producer has no other way to learn the run ended badly rather than
                // simply ending. See ProducerStateProvider#getLastFailure().
                recordFailure(e);
                break;
            }
            if (cProducer.runOnce) {
                break;
            }
        }
        awaitPendingBatches();
        signalNotRunning();
    }

    /**
     * Waits until every batch this producer has started is in the consumer. Runs at the end of
     * {@link #run()}, while the state is still {@link ProducerState#RUNNING}, whether the loop ended
     * normally, on request or with an exception.
     *
     * <p>A producer that hands its batches to the consumer on its own thread has nothing to wait for,
     * which is this default. One that hands them over asynchronously must override it: the finder
     * releases the consumer as soon as every producer is no longer running, and a batch enqueued after
     * that is never checked.
     */
    protected void awaitPendingBatches() {}

    /**
     * Returns how long a wait at the end of this producer's run may still take, in nanoseconds: the time
     * left until the deadline {@link #interrupt()} recorded, or the whole of {@code shutdownTimeoutSeconds}
     * when no interrupt was issued (a run that ends by itself). Never negative: past the deadline it is
     * zero, and a wait bounded by it returns at once.
     *
     * @return the time a wait for the pending batches may take
     */
    protected long remainingShutdownNanos() {
        final long deadline = shutdownDeadlineNanos.get();
        if (deadline == NO_SHUTDOWN_DEADLINE) {
            return shutdownTimeoutNanos();
        }
        return Math.max(0L, deadline - System.nanoTime());
    }

    /**
     * Describes, for a shutdown log line, where a wait stands relative to the interrupt, which is where
     * {@code shutdownTimeoutSeconds} is measured from.
     *
     * @return {@code "<seconds> after the interrupt"}, or {@code "no interrupt was issued"}
     */
    protected String describeTimeSinceInterrupt() {
        final long deadline = shutdownDeadlineNanos.get();
        if (deadline == NO_SHUTDOWN_DEADLINE) {
            return "no interrupt was issued";
        }
        final long interruptNanos = deadline - shutdownTimeoutNanos();
        return formatSeconds(System.nanoTime() - interruptNanos) + " after the interrupt";
    }

    /**
     * Formats a span of nanoseconds for a shutdown log line.
     *
     * @param nanos the span in nanoseconds
     * @return the span in seconds with one decimal, e.g. {@code 2.0s}
     */
    protected static String formatSeconds(long nanos) {
        return String.format(Locale.ROOT, "%.1fs", nanos / (double) TimeUnit.SECONDS.toNanos(1));
    }

    private long shutdownTimeoutNanos() {
        return TimeUnit.SECONDS.toNanos(cProducer.shutdownTimeoutSeconds);
    }

    /**
     * Transitions the producer to {@link ProducerState#NOT_RUNNING} and releases
     * any thread parked in {@link #waitTillProducerNotRunning()}.
     *
     * <p>Visible to subclasses and tests so a fake producer can simulate the
     * terminal transition without going through {@link #run()}.
     */
    protected void signalNotRunning() {
        state = ProducerState.NOT_RUNNING;
        notRunningLatch.countDown();
    }

    @Override
    public void produceKeys() throws Exception {
        try {
            BigInteger[] secrets;
            try {
                secrets = keyProducer.createSecrets(
                        cProducer.getOverallWorkSize(), cProducer.batchUsePrivateKeyIncrement);
            } catch (NoMoreSecretsAvailableException ex) {
                logNoMoreSecretsInSecretFactory();
                interrupt();
                return;
            }

            // assert the requested secrets array fulfill its request parameter
            if (cProducer.batchUsePrivateKeyIncrement) {
                if (secrets.length != 1) {
                    throw new IllegalStateException("secrets.length=" + secrets.length
                            + " but cProducer.batchUsePrivateKeyIncrement=true requires exactly 1");
                }
            } else {
                // A key producer may return a shorter last batch (an incremental range that ends inside it).
                if (secrets.length < 1 || secrets.length > cProducer.getOverallWorkSize()) {
                    throw new IllegalStateException("secrets.length=" + secrets.length
                            + " is not in [1, cProducer.getOverallWorkSize()=" + cProducer.getOverallWorkSize() + "]");
                }
            }
            privateKeyValidator.replaceInvalidPrivateKeys(secrets);

            consumeSecrets(secrets);
        } catch (RuntimeException e) {
            logErrorInProduceKeys(e);
            throw e;
        }
    }

    void consumeSecrets(BigInteger... secrets) {
        String producerLabel = producerLabel();
        runtimeStatistics.incrementBatches(producerLabel);
        if (cProducer.batchUsePrivateKeyIncrement) {
            // Increment mode expands secrets[0] into a full grid of getOverallWorkSize() candidates,
            // so the candidate count is the grid size, not secrets.length.
            runtimeStatistics.addGeneratedKeys(producerLabel, cProducer.getOverallWorkSize());
            BigInteger secret = secrets[0];
            BigInteger secretBase = createSecretBase(secret, cProducer.logSecretBase);
            processSecretBase(secretBase);
        } else {
            // Non-increment: each supplied secret is one candidate. Counting the candidates that
            // enter the pipeline at the top (before any GPU pre-filter) is what lets the statistics
            // line show total throughput distinctly from the consumer's post-filter lookup rate.
            runtimeStatistics.addGeneratedKeys(producerLabel, secrets.length);
            processSecrets(secrets);
        }
    }

    /**
     * The method fromPrivate can throw an {@link IllegalArgumentException}.
     *
     * @param e      the throwable describing the failure
     * @param secret the secret to be able to recover the issue
     */
    protected void logErrorInProduceKeys(Throwable e, BigInteger secret) {
        LOGGER.error("Error in produceKey for secret " + secret + ".", e);
        // This is the swallowed path: the caller carries on with the next secret, so logging is the
        // only trace a failure ever leaves. Recording it as well is what lets an orchestrator tell a
        // window of real work from a window of repeated failure -- see getLastFailure().
        recordFailure(e);
    }

    /**
     * Logs an unexpected error caught while running {@link #produceKeys()}.
     *
     * @param e the exception to log
     */
    protected void logErrorInProduceKeys(Exception e) {
        LOGGER.error("Error in produceKey", e);
        recordFailure(e);
    }

    /**
     * Records a failure so callers can observe it after the fact.
     *
     * <p>Only the most recent one is kept: a producer failing repeatedly reports the same cause over
     * and over, and holding every throwable of a 20-second failure storm would retain stack traces
     * nobody reads. What callers act on is whether a failure happened at all.
     *
     * @param e the failure to record
     */
    protected void recordFailure(Throwable e) {
        lastFailure = e;
    }

    /**
     * Logs that the underlying secret source has been exhausted.
     */
    protected void logNoMoreSecretsInSecretFactory() {
        LOGGER.error("No more keys in secret factory. Shutdown producer.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Waits until the deadline {@link #interrupt()} recorded plus {@link #SHUTDOWN_WAIT_GRACE}: the
     * producer waits for its pending batches until exactly that deadline and reports itself stopped right
     * after, so a wait that ended at the deadline saw it still running and left its resources unreleased.
     * Without an interrupt the wait is {@code shutdownTimeoutSeconds} plus the grace from now.
     */
    @Override
    public void waitTillProducerNotRunning() {
        if (state != ProducerState.RUNNING) {
            return;
        }
        final long waitNanos = remainingShutdownNanos() + SHUTDOWN_WAIT_GRACE.toNanos();
        try {
            if (!notRunningLatch.await(waitNanos, TimeUnit.NANOSECONDS)) {
                LOGGER.error(
                        "waitTillProducerNotRunning timed out after {} ({}, shutdownTimeoutSeconds {} plus {}s grace);"
                                + " producer state still {}. Continuing shutdown without confirming this producer stopped.",
                        formatSeconds(waitNanos),
                        describeTimeSinceInterrupt(),
                        cProducer.shutdownTimeoutSeconds,
                        SHUTDOWN_WAIT_GRACE.toSeconds(),
                        state);
            }
        } catch (InterruptedException e) {
            LOGGER.warn("waitTillProducerNotRunning interrupted; continuing shutdown.", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Derives the per-batch secret base by masking off the lower bits of {@code secret}.
     *
     * @param secret        the candidate secret
     * @param logSecretBase whether to log the resulting secret base
     * @return the secret base used as the starting point of the next batch
     */
    public BigInteger createSecretBase(BigInteger secret, boolean logSecretBase) {
        BigInteger lowBitMask = bitHelper.getLowBitMask(cProducer.batchSizeInBits);
        BigInteger secretBase = keyUtility.alignDown(secret, lowBitMask);

        if (logSecretBase) {
            LOGGER.info("secretBase: " + keyUtility.bigIntegerToFixedLengthHex(secretBase) + "/"
                    + cProducer.batchSizeInBits);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("secret BigInteger: " + secret);
            LOGGER.trace("secret as byte array: " + keyUtility.bigIntegerToFixedLengthHex(secret));
            LOGGER.trace("lowBitMask: " + Hex.encodeHexString(lowBitMask.toByteArray()));
            LOGGER.trace("secretBase: " + secretBase);
            LOGGER.trace("secretBase as byte array: " + keyUtility.bigIntegerToFixedLengthHex(secretBase));
        }

        return secretBase;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The first call records the shutdown deadline, {@code shutdownTimeoutSeconds} from now; later
     * calls keep it. It is recorded before the loop flag is cleared, so a run loop that ends because of
     * this interrupt sees it when it waits for its pending batches.
     */
    @Override
    public void interrupt() {
        shutdownDeadlineNanos.compareAndSet(NO_SHUTDOWN_DEADLINE, System.nanoTime() + shutdownTimeoutNanos());
        shouldRun.set(false);
    }

    @Override
    public ProducerState getState() {
        return state;
    }

    @Override
    public @Nullable Throwable getLastFailure() {
        return lastFailure;
    }
}
