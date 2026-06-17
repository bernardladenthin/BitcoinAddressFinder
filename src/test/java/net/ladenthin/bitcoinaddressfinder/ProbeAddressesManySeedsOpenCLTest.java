// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLGridResult;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Hardened end-to-end correctness gate for the scalar-walker field arithmetic, run for <b>both</b>
 * field representations ({@link CProducerOpenCL#useReducedRadixField} {@code false} = radix-2³²,
 * {@code true} = reduced-radix 2²⁶).
 *
 * <p>The existing {@code ProbeAddressesOpenCLTest} pins correctness against bitcoinj but uses a single
 * fixed seed ({@code new Random(1337)}). The reduced-radix port is sensitive to magnitude/carry
 * handling that could (in theory) only misbehave for rare limb patterns, and a wrong result here is a
 * <em>silently missed key</em>, not a crash — so this test widens the input space: it derives a full
 * batch from <b>many</b> independent random bases (different seeds and bit sizes) and verifies
 * <b>every</b> derived key against bitcoinj via {@link PublicKeyBytes#runtimePublicKeyCalculationCheck()},
 * which recomputes both the compressed and uncompressed public key and both hash160 chains from the
 * secret with {@code ECKey.fromPrivate} and compares. Thousands of keys per representation across
 * varied ranges make a representation-specific arithmetic bug overwhelmingly likely to surface.
 *
 * <p>{@code @OpenCLTest}: self-skips when no OpenCL 2.0+ device is present (runs under pocl in CI or a
 * real GPU). {@code keysPerWorkItem} is set to the {@code KEYS_BATCH_INV} sub-batch size so the walk's
 * Montgomery sub-batch boundaries (and the {@code m == 0} anchor-emit path) are exercised every batch.
 */
public class ProbeAddressesManySeedsOpenCLTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    private final BitHelper bitHelper = new BitHelper();

    /** 2^8 = 256 keys per batch. */
    private static final int BITS_FOR_BATCH = 8;

    /** Walk sub-batch size (matches the kernel's KEYS_BATCH_INV) so the batched-inversion path runs. */
    private static final int KEYS_PER_WORK_ITEM = 16;

    /** Number of independent random bases (seeds) per representation. */
    private static final int NUMBER_OF_SEEDS = 16;

    @ParameterizedTest
    @OpenCLTest
    @ValueSource(booleans = {false, true})
    public void createKeys_manySeeds_allMatchBitcoinjReference(boolean useReducedRadixField) throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        final KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);

        final CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = BITS_FOR_BATCH;
        producerOpenCL.keysPerWorkItem = KEYS_PER_WORK_ITEM;
        producerOpenCL.useReducedRadixField = useReducedRadixField;

        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
            openCLContext.init();

            for (int seed = 0; seed < NUMBER_OF_SEEDS; seed++) {
                final Random random = new Random(0x5DEECE66DL ^ (0x9E3779B97F4A7C15L * (seed + 1)));
                // Vary the bit size across seeds so both small and full-width 256-bit bases are covered.
                final int bitSize = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS - (seed % 64);
                final BigInteger secretKeyBase = keyUtility.createSecret(bitSize, random);
                final BigInteger secretBase =
                        keyUtility.alignDown(secretKeyBase, bitHelper.getLowBitMask(producerOpenCL.batchSizeInBits));

                final OpenCLGridResult createKeys = openCLContext.createKeys(secretBase);
                try {
                    final PublicKeyBytes[] publicKeys = createKeys.getPublicKeyBytes();
                    for (int i = 0; i < publicKeys.length; i++) {
                        final PublicKeyBytes publicKeyBytes = publicKeys[i];
                        assertTrue(
                                publicKeyBytes.runtimePublicKeyCalculationCheck(),
                                "useReducedRadixField=" + useReducedRadixField
                                        + " seed=" + seed
                                        + " index=" + i
                                        + " secretKey=" + publicKeyBytes.getSecretKey());
                    }
                } finally {
                    createKeys.close();
                }
            }
        }
    }
}
