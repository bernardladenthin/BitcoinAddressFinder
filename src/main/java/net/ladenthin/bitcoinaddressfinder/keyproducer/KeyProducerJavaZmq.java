// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.Startable;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

/**
 * Key producer that receives secrets through a ZeroMQ PULL socket.
 *
 * <p>The background receiver thread is not spawned by the constructor; callers
 * must invoke {@link #start()} after construction. This avoids the JEP&nbsp;410
 * this-escape that would otherwise publish a partially-constructed instance to
 * the worker thread the moment the {@code new Thread(() -> ...)} lambda captures
 * {@code this}.</p>
 */
public class KeyProducerJavaZmq extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaZmq> implements Startable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyProducerJavaZmq.class);

    private final ZContext context;
    private final ZMQ.Socket socket;
    private @Nullable Thread receiverThread;

    /**
     * Creates a new ZMQ-based key producer and opens the underlying ZMQ socket
     * (bind or connect according to the configured mode). The background
     * receiver thread is NOT spawned here; the caller must invoke
     * {@link #start()} afterwards.
     *
     * @param config      the ZMQ configuration
     * @param keyUtility  cryptographic helper
     * @param bitHelper   bit/batch-size helper (unused but kept for symmetry)
     */
    public KeyProducerJavaZmq(CKeyProducerJavaZmq config, KeyUtility keyUtility, BitHelper bitHelper) {
        super(config, keyUtility);

        context = new ZContext();
        socket = context.createSocket(SocketType.PULL);

        if (cKeyProducerJava.mode == CKeyProducerJavaZmq.Mode.BIND) {
            socket.bind(cKeyProducerJava.address);
        } else {
            socket.connect(cKeyProducerJava.address);
        }

        socket.setReceiveTimeOut(cKeyProducerJava.timeout);
    }

    /**
     * Spawns and starts the background receiver thread that polls the ZMQ
     * socket and feeds incoming messages into the queue. Idempotency: calling
     * {@code start()} more than once replaces the {@code receiverThread} field;
     * the intended usage is a single invocation right after construction.
     */
    @Override
    public void start() {
        Thread thread = new Thread(
                () -> {
                    while (!shouldStop && !Thread.currentThread().isInterrupted()) {
                        try {
                            byte[] msg = socket.recv(0); // blocking up to timeout
                            if (msg != null) {
                                if (msg.length == OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES) {
                                    addSecret(msg);
                                } else {
                                    LOGGER.error("Received invalid secret length: " + msg.length);
                                }
                            }
                            // if msg is null: it's a timeout — just loop again
                        } catch (ZMQException e) {
                            if (shouldStop || e.getErrorCode() == ZMQ.Error.ETERM.getCode()) break;
                            LOGGER.error("ZMQ error", e); // unexpected ZMQ errors
                        }
                    }
                },
                "ZMQ-Receiver");

        thread.setDaemon(true);
        thread.start();
        receiverThread = thread;
    }

    @Override
    protected int getReadTimeout() {
        return cKeyProducerJava.timeout;
    }

    @Override
    public void interrupt() {
        signalShutdown(); // wakes any caller blocked in createSecrets()
        Thread localReceiver = receiverThread;
        if (localReceiver != null) {
            localReceiver.interrupt(); // allow thread to exit if blocked
            try {
                localReceiver.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        socket.close();
        context.close();
    }
}
