// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.jcstress;

import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Description;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.J_Result;

@JCStressTest
@Description("Two concurrent incrementers on an AtomicLong must yield a sum of exactly 2.")
@Outcome(id = "2", expect = Expect.ACCEPTABLE, desc = "Both increments applied atomically")
@Outcome(id = {"0", "1"}, expect = Expect.FORBIDDEN, desc = "BUG: lost update on AtomicLong")
@State
public class BitHelperRace {

    private final AtomicLong counter = new AtomicLong(0);

    @Actor
    public void increment1() {
        counter.incrementAndGet();
    }

    @Actor
    public void increment2() {
        counter.incrementAndGet();
    }

    @Arbiter
    public void check(J_Result r) {
        r.r1 = counter.get();
    }
}
