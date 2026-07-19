// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.GpuFilterType;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link Finder#resolveGpuFilterType()}, which picks the <em>single</em> fingerprint width
 * the GPU pre-filter payload is built for.
 *
 * <p><b>Why this needs pinning.</b> The payload is a full LMDB scan and a multi-GB allocation, so
 * it is built exactly once and shared by every compact-mode producer — one width has to win. The
 * resolution therefore also <b>rewrites</b> the losing producers' {@code gpuFilterType} so their
 * kernels probe the width that was actually uploaded. If that rewrite were dropped, nothing would
 * fail: the kernel would reinterpret the uploaded slots at the wrong width and report misses for
 * addresses that are present — silent false negatives, indistinguishable from an honest empty
 * scan. Equally, if the resolution ignored a producer's configured width in the wrong direction,
 * an operator's explicit {@code FUSE_16} setting would be silently discarded.
 */
public class FinderGpuFilterTypeTest {

    /** Builds a producer configuration in compact (GPU pre-filter) mode at the given width. */
    private static CProducerOpenCL compact(GpuFilterType type) {
        CProducerOpenCL cfg = new CProducerOpenCL();
        cfg.enableGpuFilter = true;
        cfg.transferAll = false;
        cfg.gpuFilterType = type;
        return cfg;
    }

    private static CFinder finderWith(List<CProducerOpenCL> producers) {
        CFinder cFinder = new CFinder();
        cFinder.producerOpenCL = new ArrayList<>(producers);
        return cFinder;
    }

    @Test
    public void resolveGpuFilterType_noProducers_returnsFuse8Default() {
        // Nothing is built in this case; the method must still return a usable value rather than
        // null, because the consumer stores it unconditionally before initLMDB().
        Finder finder = new Finder(finderWith(List.of()));

        assertThat(finder.resolveGpuFilterType(), is(equalTo(GpuFilterType.FUSE_8)));
    }

    @Test
    public void resolveGpuFilterType_singleCompactProducer_returnsItsConfiguredWidth() {
        // The ordinary case: one producer, and its explicit setting must reach the payload build.
        // Falling back to the FUSE_8 default here would silently ignore a configured FUSE_16 and
        // hand the kernel half the fingerprint width it expects.
        CProducerOpenCL producer = compact(GpuFilterType.FUSE_16);
        Finder finder = new Finder(finderWith(List.of(producer)));

        assertThat(finder.resolveGpuFilterType(), is(equalTo(GpuFilterType.FUSE_16)));
        assertThat(producer.gpuFilterType, is(equalTo(GpuFilterType.FUSE_16)));
    }

    @Test
    public void resolveGpuFilterType_severalProducersAgreeing_returnsThatWidthAndDoesNotWarn() {
        // Agreement is not a conflict: no producer may be rewritten and no warning emitted, so a
        // correctly configured multi-GPU setup stays quiet and an actual conflict stays visible.
        CProducerOpenCL first = compact(GpuFilterType.FUSE_16);
        CProducerOpenCL second = compact(GpuFilterType.FUSE_16);
        CProducerOpenCL third = compact(GpuFilterType.FUSE_16);
        Finder finder = new Finder(finderWith(List.of(first, second, third)));

        try (LogCaptor logCaptor = LogCaptor.forClass(Finder.class)) {
            assertThat(finder.resolveGpuFilterType(), is(equalTo(GpuFilterType.FUSE_16)));
            assertThat(logCaptor.getWarnLogs().isEmpty(), is(true));
        }
    }

    @Test
    public void resolveGpuFilterType_producersDisagree_firstCompactProducerWinsAndOthersAreRewritten() {
        // The decisive case. Only one payload exists, so the losers' configuration must be
        // corrected to match it; leaving them pointing at their own width would make their kernels
        // probe the uploaded slots at the wrong width and silently drop funded addresses.
        CProducerOpenCL first = compact(GpuFilterType.FUSE_16);
        CProducerOpenCL second = compact(GpuFilterType.FUSE_8);
        CProducerOpenCL third = compact(GpuFilterType.FUSE_8);
        Finder finder = new Finder(finderWith(List.of(first, second, third)));

        try (LogCaptor logCaptor = LogCaptor.forClass(Finder.class)) {
            GpuFilterType resolved = finder.resolveGpuFilterType();

            assertThat(resolved, is(equalTo(GpuFilterType.FUSE_16)));
            assertThat(first.gpuFilterType, is(equalTo(GpuFilterType.FUSE_16)));
            assertThat(second.gpuFilterType, is(equalTo(GpuFilterType.FUSE_16)));
            assertThat(third.gpuFilterType, is(equalTo(GpuFilterType.FUSE_16)));
            // The override is a silent configuration change unless it is logged; an operator who
            // asked for FUSE_8 on two devices must be able to see why they are running FUSE_16.
            assertThat(logCaptor.getWarnLogs().stream().anyMatch(m -> m.contains("gpuFilterType")), is(true));
        }
    }

    @Test
    public void resolveGpuFilterType_firstProducerIsFuse8_wins() {
        // Mirror of the case above, so the test cannot pass merely because FUSE_16 outranks FUSE_8.
        // The rule is positional ("the first compact-mode producer"), not a precedence between
        // widths.
        CProducerOpenCL first = compact(GpuFilterType.FUSE_8);
        CProducerOpenCL second = compact(GpuFilterType.FUSE_16);
        Finder finder = new Finder(finderWith(List.of(first, second)));

        assertThat(finder.resolveGpuFilterType(), is(equalTo(GpuFilterType.FUSE_8)));
        assertThat(second.gpuFilterType, is(equalTo(GpuFilterType.FUSE_8)));
    }

    @Test
    public void resolveGpuFilterType_gpuFilterDisabledProducersAreIgnoredWhenChoosing() {
        // A producer with enableGpuFilter = false uploads no filter and probes nothing, so its
        // gpuFilterType is meaningless. Letting it vote would let a dormant setting decide the
        // width for the producers that actually probe.
        CProducerOpenCL disabled = compact(GpuFilterType.FUSE_8);
        disabled.enableGpuFilter = false;
        CProducerOpenCL compactProducer = compact(GpuFilterType.FUSE_16);
        Finder finder = new Finder(finderWith(List.of(disabled, compactProducer)));

        assertThat(finder.resolveGpuFilterType(), is(equalTo(GpuFilterType.FUSE_16)));
        // ...and it is not rewritten either: it is simply not part of this decision.
        assertThat(disabled.gpuFilterType, is(equalTo(GpuFilterType.FUSE_8)));
    }

    @Test
    public void resolveGpuFilterType_transferAllProducersAreIgnoredWhenChoosing() {
        // transferAll = true means the kernel emits every candidate and never probes the filter
        // (this is also how vanity mode forces producers), so such a producer must not decide the
        // width the payload is built for.
        CProducerOpenCL fullTransfer = compact(GpuFilterType.FUSE_8);
        fullTransfer.transferAll = true;
        CProducerOpenCL compactProducer = compact(GpuFilterType.FUSE_16);
        Finder finder = new Finder(finderWith(List.of(fullTransfer, compactProducer)));

        assertThat(finder.resolveGpuFilterType(), is(equalTo(GpuFilterType.FUSE_16)));
        assertThat(fullTransfer.gpuFilterType, is(equalTo(GpuFilterType.FUSE_8)));
    }

    @Test
    public void resolveGpuFilterType_onlyNonCompactProducers_returnsFuse8Default() {
        // No compact-mode producer -> nothing is built, so the returned value is only a
        // placeholder; it must still be non-null and must not be taken from the ignored producers.
        CProducerOpenCL disabled = compact(GpuFilterType.FUSE_16);
        disabled.enableGpuFilter = false;
        CProducerOpenCL fullTransfer = compact(GpuFilterType.FUSE_16);
        fullTransfer.transferAll = true;
        Finder finder = new Finder(finderWith(List.of(disabled, fullTransfer)));

        assertThat(finder.resolveGpuFilterType(), is(equalTo(GpuFilterType.FUSE_8)));
    }
}
