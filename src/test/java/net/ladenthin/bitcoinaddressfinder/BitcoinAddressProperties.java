// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;

public class BitcoinAddressProperties {

    private final BitHelper bitHelper = new BitHelper();

    @Property
    boolean getLowBitMaskHasExactlyNBitsSet(@ForAll @IntRange(min = 0, max = 20) int bits) {
        BigInteger result = bitHelper.getLowBitMask(bits);
        return result.bitCount() == bits;
    }

    @Property
    boolean convertBitsSizeIsPowerOfTwo(@ForAll @IntRange(min = 0, max = 20) int bits) {
        int size = bitHelper.convertBitsToSize(bits);
        return size == (1 << bits);
    }
}
