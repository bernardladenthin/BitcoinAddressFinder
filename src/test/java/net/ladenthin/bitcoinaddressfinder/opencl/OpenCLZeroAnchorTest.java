// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regression guard for the grid whose base is 0: the first work-item's anchor is then the scalar 0,
 * i.e. the point at infinity, which has no affine representation.
 *
 * <p>Every work-item derives its {@code keysPerWorkItem} keys as {@code P0 + m·G} from one affine
 * anchor {@code P0}. An incremental scan that starts at the smallest valid key is aligned down to base
 * 0, so the first work-item of its first grid used to compute every one of its keys from a meaningless
 * anchor: keys 2 &#x2026; {@code keysPerWorkItem - 1} were silently missed, while the CPU producer found
 * them. Key 0 is replaced on the host by {@link PublicKeyBytes#INVALID_KEY_ONE}, which carries the
 * public key of the secret 1.
 *
 * <p>Both field implementations are covered, because each has its own walk.
 */
public class OpenCLZeroAnchorTest {

    private static final int KEYS_PER_WORK_ITEM = 16;

    private final BitHelper bitHelper = new BitHelper();

    @ParameterizedTest(name = "[{index}] reducedRadix={0}")
    @OpenCLTest
    @ValueSource(booleans = {false, true})
    public void createKeys_gridBaseZero_everyKeyOfTheFirstWorkItemIsCorrect(boolean useReducedRadixField)
            throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        final CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = 8;
        producerOpenCL.keysPerWorkItem = KEYS_PER_WORK_ITEM;
        producerOpenCL.useReducedRadixField = useReducedRadixField;

        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
            openCLContext.init();

            try (OpenCLGridResult result = openCLContext.createKeys(BigInteger.ZERO)) {
                final PublicKeyBytes[] publicKeys = result.getPublicKeyBytes();

                // The secret 1 is unsupported by bitcoinj; the kernel's point must still be 1·G.
                assertThat(publicKeys[1].getSecretKey(), is(equalTo(BigInteger.ONE)));
                assertThat(
                        publicKeys[1].getUncompressed(), is(equalTo(PublicKeyBytes.INVALID_KEY_ONE.getUncompressed())));

                for (int i = 2; i < publicKeys.length; i++) {
                    assertThat(publicKeys[i].getSecretKey(), is(equalTo(BigInteger.valueOf(i))));
                    assertTrue(
                            publicKeys[i].runtimePublicKeyCalculationCheck(),
                            "reducedRadix=" + useReducedRadixField + " index=" + i);
                }
            }
        }
    }
}
