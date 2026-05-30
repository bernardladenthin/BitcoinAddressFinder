// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;

/**
 * Supplies the next candidate secret as a {@link BigInteger}.
 */
@FunctionalInterface
public interface SecretSupplier {
    /**
     * Returns the next secret of up to {@code bitLength} bits.
     *
     * @param bitLength the maximum bit length of the next secret
     * @return the next secret
     * @throws NoMoreSecretsAvailableException if the supplier cannot produce more secrets
     */
    BigInteger nextSecret(int bitLength) throws NoMoreSecretsAvailableException;
}
