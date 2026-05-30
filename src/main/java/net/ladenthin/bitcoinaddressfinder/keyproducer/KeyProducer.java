// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.Interruptable;

/**
 * Strategy for creating batches of secret scalars used as candidate private keys.
 */
public interface KeyProducer extends Interruptable {
    /**
     * Creates the next batch of secrets.
     *
     * @param overallWorkSize        the requested number of secrets in the batch
     * @param returnStartSecretOnly  if {@code true} only the first secret of the batch is returned
     * @return an array of generated secrets (length 1 if {@code returnStartSecretOnly})
     * @throws NoMoreSecretsAvailableException if no more secrets can be produced
     */
    BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly)
            throws NoMoreSecretsAvailableException;
}
