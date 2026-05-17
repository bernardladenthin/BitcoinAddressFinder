// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

public class KeyProducerJavaZmq extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaZmq> {

    private final ZContext context;
    private final ZMQ.Socket socket;
    private final Thread receiverThread;
    
    public KeyProducerJavaZmq(CKeyProducerJavaZmq config, KeyUtility keyUtility, BitHelper bitHelper, Logger logger) {
        super(config, keyUtility, logger);

        context = new ZContext();
        socket = context.createSocket(SocketType.PULL);

        if (cKeyProducerJava.mode == CKeyProducerJavaZmq.Mode.BIND) {
            socket.bind(cKeyProducerJava.address);
        } else {
            socket.connect(cKeyProducerJava.address);
        }

        int internalTimeout = getReadTimeout();
        socket.setReceiveTimeOut(internalTimeout);

        receiverThread = new Thread(() -> {
            while (!shouldStop && !Thread.currentThread().isInterrupted()) {
                try {
                    byte[] msg = socket.recv(0); // blocking up to timeout
                    if (msg != null) {
                        if (msg.length == PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES) {
                            addSecret(msg);
                        } else {
                            logger.error("Received invalid secret length: " + msg.length);
                        }
                    }
                    // if msg is null: it's a timeout — just loop again
                } catch (ZMQException e) {
                    if (shouldStop || e.getErrorCode() == ZMQ.Error.ETERM.getCode()) break;
                    logger.error("ZMQ error", e); // unexpected ZMQ errors
                }
            }
        }, "ZMQ-Receiver");

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    @Override
    protected int getReadTimeout() {
        return cKeyProducerJava.timeout;
    }

    @Override
    public void interrupt() {
        shouldStop = true;
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