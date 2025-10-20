// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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

        // Fallback to 1000 ms if timeout not set
        int internalTimeout = cKeyProducerJava.timeout > 0 ? cKeyProducerJava.timeout : 1000;
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
        return cKeyProducerJava.timeout > 0 ? cKeyProducerJava.timeout : 1000;
    }

    @Override
    public void interrupt() {
        shouldStop = true;
        receiverThread.interrupt(); // allow thread to exit if blocked
        try {
            receiverThread.join(500);
        } catch (InterruptedException ignored) {}
        socket.close();
        context.close();
    }
}