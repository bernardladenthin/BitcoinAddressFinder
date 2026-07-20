// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.GpuFilterType;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLGridResult;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Runs the real production kernel on the real device and checks its hit set against the CPU filter,
 * candidate by candidate, for every supported {@link GpuFilterType}.
 *
 * <h2>Why this exists next to the agreement tests</h2>
 * {@code BinaryFuse16GpuAgreementTest} re-implements the kernel formula in Java and compares it to
 * the Java filter. That proves the <em>transcription</em> is faithful — it proves nothing about the
 * binary the OpenCL compiler actually produced. A driver bug, a wrong kernel-argument index, an
 * endianness mistake in the upload, or a {@code ushort*} cast reading a buffer that was filled with
 * 8-bit slots would all leave the agreement test perfectly green.
 *
 * <p>All of those failures look the same from the outside: the scan keeps running and reports
 * nothing. A filter that wrongly rejects is indistinguishable from a database that genuinely holds
 * no match, which is why this path needs a test that exercises the compiled kernel end to end
 * rather than a model of it.
 *
 * <h2>Why every second candidate</h2>
 * The sibling {@code OpenCLCompactOutputIntegrationTest} inserts three indices, {@code {0, n/2,
 * n-1}}. That catches a filter that matches nothing or everything, but a dense alternating pattern
 * catches far more: an off-by-one in the slot index, a batch that is silently truncated, a
 * work-item that reads its neighbour's key, or a fingerprint width mismatch that shifts every
 * lookup by one slot. Half the batch must be flagged, and <em>which</em> half is fully determined.
 *
 * <p>The expectation is not "exactly the inserted ones" — the filter may legitimately produce false
 * positives among the other half, and at these small sizes it sometimes does. It is computed by
 * running the same Java filter over every candidate, so the assertion is exact set equality against
 * what the CPU says, false positives included. Anything else would either be flaky or would have to
 * tolerate a mismatch, and tolerating a mismatch here defeats the purpose.
 */
public class OpenCLFilterHitSetIntegrationTest {

    private final BitHelper bitHelper = new BitHelper();

    /** Aligned base secret: low 24 bits are zero, so {@code secretBase + i == secretBase | i}. */
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

    private CProducerOpenCL compactProducerConfig(int batchSizeInBits, GpuFilterType filterType) {
        CProducerOpenCL cfg = new CProducerOpenCL();
        cfg.batchSizeInBits = batchSizeInBits;
        cfg.keysPerWorkItem = 1;
        cfg.enableGpuFilter = true;
        cfg.transferAll = false;
        cfg.gpuFilterType = filterType;
        return cfg;
    }

    @ParameterizedTest
    @EnumSource(GpuFilterType.class)
    public void everySecondCandidateInserted_kernelHitSetMatchesCpuExactly(GpuFilterType filterType) throws Exception {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int bits = Math.min(8, assume.maxGridBitsForAvailableDevice());
        final int n = 1 << bits;

        // Derive every candidate once on the CPU; the kernel must derive the same ones.
        PublicKeyBytes[] candidates = new PublicKeyBytes[n];
        for (int i = 0; i < n; i++) {
            candidates[i] = PublicKeyBytes.fromPrivate(SECRET_BASE.add(BigInteger.valueOf(i)));
        }

        // Insert the uncompressed hash160 of every SECOND candidate.
        List<byte[]> filterEntries = new ArrayList<>();
        for (int i = 0; i < n; i += 2) {
            filterEntries.add(candidates[i].getUncompressedKeyHash());
        }
        AddressIterable source = iterableOf(filterEntries);

        // Build the matching filter and predict the kernel's answer with the SAME filter. The kernel
        // flags a candidate when either hash160 variant passes, so the prediction must do both.
        final Set<BigInteger> expected = new LinkedHashSet<>();
        OpenCLContext context = new OpenCLContext(compactProducerConfig(bits, filterType), bitHelper);
        try {
            context.init();

            switch (filterType) {
                case FUSE_8 -> {
                    BinaryFuse8AddressPresence filter = BinaryFuse8AddressPresence.populateFrom(source);
                    for (int i = 0; i < n; i++) {
                        if (matches(filter::containsAddress, candidates[i])) {
                            expected.add(SECRET_BASE.add(BigInteger.valueOf(i)));
                        }
                    }
                    BinaryFuse8GpuFilterData d = filter.toGpuFilterData();
                    context.uploadGpuFilter(
                            d.fingerprints(),
                            (int) d.seed(),
                            (int) (d.seed() >>> 32),
                            d.segmentLength(),
                            d.segmentLengthMask(),
                            d.segmentCountLength());
                }
                case FUSE_16 -> {
                    BinaryFuse16AddressPresence filter = BinaryFuse16AddressPresence.populateFrom(source);
                    for (int i = 0; i < n; i++) {
                        if (matches(filter::containsAddress, candidates[i])) {
                            expected.add(SECRET_BASE.add(BigInteger.valueOf(i)));
                        }
                    }
                    BinaryFuse16GpuFilterData d = filter.toGpuFilterData();
                    context.uploadGpuFilter16(
                            d.fingerprints(),
                            (int) d.seed(),
                            (int) (d.seed() >>> 32),
                            d.segmentLength(),
                            d.segmentLengthMask(),
                            d.segmentCountLength());
                }
            }

            OpenCLGridResult result = context.createKeys(SECRET_BASE);
            PublicKeyBytes[] keys = result.getPublicKeyBytes();

            Set<BigInteger> actual = new LinkedHashSet<>();
            for (PublicKeyBytes key : keys) {
                assertThat(
                        "every returned entry must be a genuine derivation",
                        key.runtimePublicKeyCalculationCheck(),
                        is(true));
                actual.add(key.getSecretKey());
            }

            // Sanity: the setup must actually discriminate, otherwise set equality proves nothing.
            assertThat("at least the inserted half must be flagged", expected.size(), is(greaterThan(n / 4)));

            // The load-bearing assertion. Not "contains all inserted" - exact equality, so a kernel
            // that over-reports (matching too much) fails just as loudly as one that under-reports.
            assertThat("kernel hit set must equal the CPU filter's answer for " + filterType, actual, is(expected));
        } finally {
            context.close();
        }
    }

    /** The kernel flags a candidate when the uncompressed OR the compressed hash160 passes. */
    private static boolean matches(java.util.function.Predicate<ByteBuffer> filter, PublicKeyBytes pkb) {
        return filter.test(ByteBuffer.wrap(pkb.getUncompressedKeyHash()))
                || filter.test(ByteBuffer.wrap(pkb.getCompressedKeyHash()));
    }
}
