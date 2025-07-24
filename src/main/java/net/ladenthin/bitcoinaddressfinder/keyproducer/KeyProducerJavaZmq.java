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


import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.zeromq.SocketType;

public class KeyProducerJavaZmq extends KeyProducerJava<CKeyProducerJavaZmq> {

    private final KeyUtility keyUtility;
    private final ZContext context;
    private final ZMQ.Socket socket;

    private volatile boolean shouldStop = false;

    public KeyProducerJavaZmq(CKeyProducerJavaZmq config, KeyUtility keyUtility, BitHelper bitHelper) {
        super(config);
        this.keyUtility = keyUtility;
        this.context = new ZContext();
        this.socket = context.createSocket(SocketType.PULL);

        if (cKeyProducerJava.mode == CKeyProducerJavaZmq.Mode.BIND) {
            socket.bind(cKeyProducerJava.address);
        } else {
            socket.connect(cKeyProducerJava.address);
        }

        socket.setReceiveTimeOut(cKeyProducerJava.timeout);
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) throws NoMoreSecretsAvailableException {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);
        int count = returnStartSecretOnly ? 1 : overallWorkSize;
        BigInteger[] secrets = new BigInteger[count];

        for (int i = 0; i < count; i++) {
            if (shouldStop) {
                throw new NoMoreSecretsAvailableException("Interrupted before receiving key.");
            }

            try {
                byte[] msg = socket.recv();

                if (msg == null) {
                    throw new NoMoreSecretsAvailableException("Timeout while receiving key.");
                }

                if (msg.length != PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES) {
                    throw new NoMoreSecretsAvailableException("Received malformed key of length " + msg.length);
                }

                BigInteger key = keyUtility.bigIntegerFromUnsignedByteArray(msg);
                secrets[i] = key;

                if (cKeyProducerJava.logReceivedSecret) {
                    System.out.println("Received key: " + keyUtility.bigIntegerToFixedLengthHex(key));
                }
            } catch (org.zeromq.ZMQException e) {
                if (shouldStop || e.getErrorCode() == ZMQ.Error.ETERM.getCode()) {
                    throw new NoMoreSecretsAvailableException("Receive interrupted due to socket/context shutdown", e);
                }
                throw e; // rethrow other unexpected ZMQ errors
            }
        }

        return secrets;
    }

    @Override
    public void interrupt() {
        shouldStop = true;
        close();
    }

    public void close() {
        socket.close();
        context.close();
    }
}
