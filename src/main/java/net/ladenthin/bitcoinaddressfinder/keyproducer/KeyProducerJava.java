// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJava;
import org.slf4j.Logger;

public abstract class KeyProducerJava<T extends CKeyProducerJava> extends AbstractKeyProducer {

    protected final T cKeyProducerJava;
    
    protected final Logger logger;
    
    public KeyProducerJava(T cKeyProducerJava, Logger logger) {
        this.cKeyProducerJava = cKeyProducerJava;
        this.logger = logger;
    }

    public void verifyWorkSize(int overallWorkSize, int maxWorkSize) throws NoMoreSecretsAvailableException {
        if (overallWorkSize < 0 || overallWorkSize > maxWorkSize) {
            throw new IllegalArgumentException("Unreasonable work size: " + overallWorkSize);
        }
    }
    
    @Override
    public Logger getLogger() {
        return logger;
    }
}
