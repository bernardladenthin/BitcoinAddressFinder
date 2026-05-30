// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

/**
 * Key producer that receives secrets through a ZeroMQ PULL socket.
 */
public class KeyProducerJavaZmq extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaZmq> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyProducerJavaZmq.class);

    private final ZContext context;
    private final ZMQ.Socket socket;
    private final Thread receiverThread;

    /**
     * Creates a new ZMQ-based key producer and starts the background receiver thread.
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

        receiverThread = new Thread(
                () -> {
                    while (!shouldStop && !Thread.currentThread().isInterrupted()) {
                        try {
                            byte[] msg = socket.recv(0); // blocking up to timeout
                            if (msg != null) {
                                if (msg.length == PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES) {
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

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    @Override
    protected int getReadTimeout() {
        return cKeyProducerJava.timeout;
    }

    @Override
    public void interrupt() {
        signalShutdown(); // wakes any caller blocked in createSecrets()
        receiverThread.interrupt(); // allow thread to exit if blocked
        try {
            receiverThread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        socket.close();
        context.close();
    }
}
