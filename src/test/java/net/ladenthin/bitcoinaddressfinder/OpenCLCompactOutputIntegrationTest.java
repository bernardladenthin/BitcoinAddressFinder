// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLGridResult;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import org.junit.jupiter.api.Test;

/**
 * End-to-end exactness tests for the GPU Binary Fuse 8 compact output path.
 *
 * <p>Every assertion is <b>exact</b> (equalities and exact inequalities, no &#x00b1; tolerances):
 * the test derives every candidate in the batch on the CPU and controls exactly which of them are
 * inserted into the filter, so the count word the kernel reports is provably bounded rather than
 * approximate.
 *
 * <p>Runs on any available OpenCL 2.0+ device. The grid size is capped to the device's safe range
 * ({@link OpenCLPlatformAssume#maxGridBitsForAvailableDevice()}), so a CPU OpenCL runtime (e.g.
 * pocl in CI) exercises the same compact path on a small batch while a GPU runs the full 256-wide
 * batch. The tests self-skip when no OpenCL 2.0+ device is present.
 */
public class OpenCLCompactOutputIntegrationTest {

    private final BitHelper bitHelper = new BitHelper();

    /** Aligned base secret: low 24 bits are zero, so secretBase + i == secretBase | i for the grid. */
    private static final BigInteger SECRET_BASE = BigInteger.ONE.shiftLeft(24);

    private static AddressIterable iterableOf(List<byte[]> entries) {
        return new AddressIterable() {
            @Override
            public Stream<ByteBuffer> addresses() {
                return entries.stream().map(ByteBuffer::wrap);
            }

            @Override
            public long count() {
                return entries.size();
            }
        };
    }

    private static void uploadPayload(OpenCLContext context, BinaryFuse8GpuFilterData data) {
        long seed = data.seed();
        context.uploadGpuFilter(
                data.fingerprints(),
                (int) seed,
                (int) (seed >>> 32),
                data.segmentLength(),
                data.segmentLengthMask(),
                data.segmentCountLength());
    }

    /** Chooses the batch bit-size: 8 (256 work-items) on a GPU, capped lower on a CPU device. */
    private int chooseBatchSizeInBits(OpenCLPlatformAssume assume) {
        return Math.min(8, assume.maxGridBitsForAvailableDevice());
    }

    private CProducerOpenCL compactProducerConfig(int batchSizeInBits) {
        CProducerOpenCL cfg = new CProducerOpenCL();
        cfg.batchSizeInBits = batchSizeInBits;
        cfg.keysPerWorkItem = 1;
        cfg.enableGpuFilter = true;
        cfg.transferAll = false;
        return cfg;
    }

    @Test
    public void compactFullBatch_allCandidatesInFilter_countEqualsN() throws Exception {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int bits = chooseBatchSizeInBits(assume);
        final int n = 1 << bits;

        // CPU-side: insert BOTH hash160 variants of every candidate, so no candidate can miss.
        List<byte[]> filterEntries = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PublicKeyBytes pkb = PublicKeyBytes.fromPrivate(SECRET_BASE.add(BigInteger.valueOf(i)));
            filterEntries.add(pkb.getUncompressedKeyHash());
            filterEntries.add(pkb.getCompressedKeyHash());
        }
        BinaryFuse8GpuFilterData payload = BinaryFuse8AddressPresence.populateFrom(iterableOf(filterEntries))
                .toGpuFilterData();

        OpenCLContext context = new OpenCLContext(compactProducerConfig(bits), bitHelper);
        try {
            context.init();
            uploadPayload(context, payload);
            OpenCLGridResult result = context.createKeys(SECRET_BASE);
            PublicKeyBytes[] keys = result.getPublicKeyBytes();

            // every candidate is in the filter -> zero misses -> count is provably exactly N
            assertThat(keys.length, is(n));
            for (PublicKeyBytes key : keys) {
                assertThat(key.runtimePublicKeyCalculationCheck(), is(true));
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void compactPartialBatch_kInserted_countAtLeastKAndBelowN() throws Exception {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int bits = chooseBatchSizeInBits(assume);
        final int n = 1 << bits;

        // Insert only the uncompressed hash160 of K candidates, spread across the batch.
        final int[] insertedIndices = {0, n / 2, n - 1};
        final int k = insertedIndices.length;
        List<byte[]> filterEntries = new ArrayList<>();
        for (int idx : insertedIndices) {
            PublicKeyBytes pkb = PublicKeyBytes.fromPrivate(SECRET_BASE.add(BigInteger.valueOf(idx)));
            filterEntries.add(pkb.getUncompressedKeyHash());
        }
        BinaryFuse8GpuFilterData payload = BinaryFuse8AddressPresence.populateFrom(iterableOf(filterEntries))
                .toGpuFilterData();

        OpenCLContext context = new OpenCLContext(compactProducerConfig(bits), bitHelper);
        try {
            context.init();
            uploadPayload(context, payload);
            OpenCLGridResult result = context.createKeys(SECRET_BASE);
            PublicKeyBytes[] keys = result.getPublicKeyBytes();

            // no false negatives: every inserted candidate must be flagged -> count >= K.
            assertThat(keys.length, is(greaterThanOrEqualTo(k)));
            // the batch is mostly misses -> count is well below N.
            assertThat(keys.length, is(lessThan(n)));

            // every returned entry is a genuine derivation (valid pubkey), and each inserted index
            // is present among the returned work-items.
            for (PublicKeyBytes key : keys) {
                assertThat(key.runtimePublicKeyCalculationCheck(), is(true));
            }
            for (int idx : insertedIndices) {
                BigInteger expectedSecret = SECRET_BASE.add(BigInteger.valueOf(idx));
                boolean found = false;
                for (PublicKeyBytes key : keys) {
                    if (key.getSecretKey().equals(expectedSecret)) {
                        found = true;
                        break;
                    }
                }
                assertThat("inserted index " + idx + " must be flagged", found, is(true));
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void compactEmptyFilter_countIsZero() throws Exception {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int bits = chooseBatchSizeInBits(assume);

        BinaryFuse8GpuFilterData payload = BinaryFuse8AddressPresence.populateFrom(iterableOf(new ArrayList<>()))
                .toGpuFilterData();

        OpenCLContext context = new OpenCLContext(compactProducerConfig(bits), bitHelper);
        try {
            context.init();
            uploadPayload(context, payload);
            OpenCLGridResult result = context.createKeys(SECRET_BASE);
            PublicKeyBytes[] keys = result.getPublicKeyBytes();

            // an empty filter matches nothing -> count is exactly 0
            assertThat(keys.length, is(0));
        } finally {
            context.close();
        }
    }
}
