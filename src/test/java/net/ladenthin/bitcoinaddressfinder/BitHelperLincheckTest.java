// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.kotlinx.lincheck.LinChecker;
import org.jetbrains.lincheck.datastructures.Operation;
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions;
import org.junit.jupiter.api.Test;

/**
 * Linearizability check demonstrating the Lincheck setup.
 *
 * <p>Uses {@link AtomicLong} as the system under test to verify that concurrent
 * increment and read operations are linearizable.</p>
 */
public class BitHelperLincheckTest {

    private final AtomicLong counter = new AtomicLong(0);

    @Operation
    public long increment() {
        return counter.incrementAndGet();
    }

    @Operation
    public long get() {
        return counter.get();
    }

    @Test
    public void modelCheckingTest() {
        ModelCheckingOptions options = new ModelCheckingOptions()
                .iterations(20)
                .invocationsPerIteration(500)
                .threads(2)
                .actorsPerThread(3);
        LinChecker.check(BitHelperLincheckTest.class, options);
    }
}
