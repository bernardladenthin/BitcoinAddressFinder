// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import static org.jocl.CL.CL_DEVICE_TYPE_ALL;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the OpenCL (GPU) producer.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CProducerOpenCL extends CProducer {

    /** Creates a new {@link CProducerOpenCL}. */
    public CProducerOpenCL() {}

    /** Index of the OpenCL platform to use. */
    public int platformIndex = 0;
    /** OpenCL device type bit mask (e.g. {@code CL_DEVICE_TYPE_ALL}). */
    public long deviceType = CL_DEVICE_TYPE_ALL;
    /** Index of the OpenCL device within the selected platform. */
    public int deviceIndex = 0;

    /** Maximum number of threads that read OpenCL kernel results concurrently. */
    public int maxResultReaderThreads = 4;

    /**
     * Number of inner iterations each OpenCL work-item performs on the GPU.
     * <p>
     * Instead of launching one work-item per candidate key, the kernel can loop
     * {@code keysPerWorkItem} times per work-item to reduce the total number of threads
     * and avoid exceeding GPU or host memory limits.
     * <p>
     * This directly reduces the number of launched work-items by a factor of {@code keysPerWorkItem},
     * and each work-item will compute and write {@code keysPerWorkItem} results.
     * <p>
     * Requirements:
     * <ul>
     *   <li>{@code keysPerWorkItem} must be a power of two (e.g. 1, 2, 4, 8, 16, ...)</li>
     *   <li>The total result size (workSize &#x00D7; keysPerWorkItem &#x00D7; chunkSize) must not exceed {@code Integer.MAX_VALUE}</li>
     *   <li>{@code batchSizeInBits} must be divisible by {@code keysPerWorkItem}</li>
     * </ul>
     * <p>
     * <b>Performance notes:</b>
     * <ul>
     *   <li>Lower values (e.g. 4 or 8) are often sufficient to reduce launch overhead and register pressure.</li>
     *   <li>Using too high a {@code keysPerWorkItem} can underutilize the GPU, as fewer total work-items are launched,
     *       which may reduce occupancy and throughput on highly parallel architectures.</li>
     *   <li>This optimization is especially useful when using point addition in the kernel instead of full scalar multiplication.</li>
     * </ul>
     * <p>
     * Example:
     * If {@code batchSizeInBits} = 20 (&#x2248;1 million keys) and {@code keysPerWorkItem} = 8,
     * the kernel is launched with only 131,072 work-items (1,048,576 &#x00F7; 8),
     * each performing 8 iterations internally.
     */
    public int keysPerWorkItem = 1;

    /**
     * Enables the GPU-side Binary Fuse 8 address-presence filter.
     * <p>
     * When {@code true} and the consumer's address-lookup backend is
     * {@code BINARY_FUSE_8}, the filter is uploaded to GPU VRAM and the kernel checks each
     * derived hash160 inline, transmitting only the (tiny) subset of candidates that the
     * filter marks as possibly present. This collapses the PCIe transfer volume from the
     * full {@code N}-work-item grid to the handful of hits per batch.
     * <p>
     * Default {@code false}: the kernel transfers every work-item result (legacy behaviour).
     */
    public boolean enableGpuFilter = false;

    /**
     * Forces the kernel to transfer every work-item result instead of using compact mode.
     * <p>
     * Compact mode (the GPU filter writing only hits) is mutually exclusive with use cases
     * that need every result on the CPU. In particular vanity scanning must see all derived
     * addresses; {@code Finder} therefore sets this flag to {@code true} automatically (with
     * a warning) whenever {@code consumerJava.enableVanity = true}.
     * <p>
     * Default {@code false}: when {@link #enableGpuFilter} is active the kernel emits the
     * compact output buffer; set to {@code true} to keep the full-transfer output layout.
     */
    public boolean transferAll = false;

    /**
     * Selects which Binary Fuse width the GPU pre-filter uses (see {@link GpuFilterType}).
     * <p>
     * <b>Only takes effect in compact mode</b>, i.e. when {@link #enableGpuFilter} is {@code true}
     * <em>and</em> {@link #transferAll} is {@code false}. With {@code enableGpuFilter = false} no
     * filter is uploaded at all, and with {@code transferAll = true} the kernel emits every
     * work-item result without probing — in both cases this setting is inert.
     * <p>
     * Default {@link GpuFilterType#FUSE_8}: it is the width that fits under every device
     * allocation limit measured so far, so the default never fails to upload.
     * {@link GpuFilterType#FUSE_16} costs twice the VRAM and hands ~240&times; fewer false
     * positives to the single-threaded consumer; prefer it whenever
     * {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE} has room for it.
     */
    public GpuFilterType gpuFilterType = GpuFilterType.FUSE_8;

    /**
     * Selects the modular-inverse implementation compiled into the OpenCL kernel.
     * <p>
     * When {@code true} (default), {@code inv_mod} uses the <b>safegcd</b> path (a port of
     * libsecp256k1's constant-time {@code modinv32}): a fixed 600 divsteps per input, so all lanes
     * of a SIMT warp run in lock-step. When {@code false}, the kernel is built with
     * {@code -D USE_LEGACY_BINARY_GCD_INV_MOD} and falls back to the input-dependent binary extended
     * GCD.
     * <p>
     * safegcd was measured ≈ +45% kernel throughput at {@code keysPerWorkItem = 128} on an RTX 3070
     * (it removes the binary GCD's warp divergence). It is the default; the legacy path is retained
     * for A/B comparison and as a fallback for any device whose signed right-shift is not arithmetic
     * (safegcd, like the reference, assumes sign-extending {@code >>}).
     */
    public boolean useSafeGcdInverse = true;

    /**
     * Compile-time profiling stage for the kernel (see {@link KernelProfileStage}). {@link
     * KernelProfileStage#FULL} (default) is the normal, correct kernel. The other modes short-circuit
     * the hash160 stages to attribute kernel time (EC arithmetic vs. hashing) and produce
     * <b>incorrect</b> output — they are for benchmarking only, never production. {@code OpenCLContext}
     * maps the mode to a {@code clBuildProgram} define; see {@code docs/performance.md} ("Stage
     * attribution") for how to diff the modes.
     */
    public KernelProfileStage kernelProfileStage = KernelProfileStage.FULL;

    /**
     * Enables OpenCL device-side profiling of the kernel launch and result read-back.
     * <p>
     * When {@code true} the command queue is created with {@code CL_QUEUE_PROFILING_ENABLE} and
     * each kernel launch / read-back is timestamped on the device; the per-launch nanosecond
     * times are exposed via {@code OpenClTask.getLastKernelExecutionNanos()} and
     * {@code getLastResultReadbackNanos()}. This isolates GPU compute time from PCIe transfer
     * time and host-side parsing, which is what the {@code GpuFuse8FilterBenchmark} uses to
     * attribute cost between the kernel and the transfer.
     * <p>
     * This is a <b>diagnostic / benchmarking</b> switch, not a production tuning knob: profiling
     * adds a small amount of driver overhead, so it is <b>off by default</b> and the runtime
     * pipeline never enables it. Default {@code false}: the queue is created without profiling
     * and the enqueue calls pass no events, exactly as the non-profiling path.
     */
    public boolean enableProfiling = false;

    /**
     * Enables verbose GPU build diagnostics in the log.
     * <p>
     * When {@code true}, {@code OpenCLContext} builds the kernel with {@code -cl-nv-verbose} and logs
     * the full {@code clGetProgramBuildInfo} build log (on NVIDIA this can include ptxas register /
     * spill stats; the content is driver-dependent and may be empty). The concise per-build "Kernel
     * resource usage" line (work-group-size ceiling, private/local memory) and the device-info dump
     * are always logged regardless of this flag.
     * <p>
     * Diagnostic only; off by default. See {@code docs/performance.md} ("Occupancy / register
     * pressure").
     */
    public boolean logGpuDiagnostics = false;

    /**
     * Selects the field-arithmetic representation used in the scalar-walker hot loop.
     * <p>
     * When {@code true} (default), the kernel is built with {@code -D USE_REDUCED_RADIX_FIELD} and the
     * affine batched-addition walk holds coordinates in the reduced-radix 2²⁶ field
     * ({@code inc_ecc_secp256k1_fe10x26.cl}), converting to radix-2³² only at the increment-table
     * reads, the single per-sub-batch inverse, and the coordinate outputs. When {@code false}, the
     * walk uses the vendored radix-2³² field ({@code copyfromhashcat/inc_ecc_secp256k1.cl}).
     * <p>
     * Motivation: the radix-2³² field multiply is carry-bound; the isolated {@code FieldMulBenchmark}
     * measured the 2²⁶ multiply ≈ 1.56× faster on an RTX 3070. The comb anchor and the
     * {@code copyfromhashcat} files are unchanged either way.
     * <p>
     * <b>On by default</b> since the end-to-end gain was confirmed <b>cross-device</b>: ≈ +22% on an
     * RTX 3070 (Ampere) and ≈ +8% on an AMD RX 7900 XTX (RDNA3), never a regression on either (see
     * {@code docs/performance.md} §8 Stage 5 + §4 "Cross-device"). Set {@code false} to force the
     * legacy radix-2³² walk for A/B comparison. Correctness is identical regardless of the setting —
     * gated byte-for-byte against bitcoinj by {@code ProbeAddressesOpenCLTest} and
     * {@code ProbeAddressesManySeedsOpenCLTest} (which builds the kernel with the flag both on and off).
     */
    public boolean useReducedRadixField = true;

    /**
     * Forces the kernel's helper functions out-of-line to fix pathological OpenCL compile times on
     * AMD GPUs. <b>Tri-state</b> — {@code null}, {@link Boolean#TRUE}, {@link Boolean#FALSE}:
     * <ul>
     *   <li>{@code null} (<b>default</b>) — <b>auto / vendor-detect</b>: the build define is enabled
     *       <em>only when the selected device is AMD</em> and disabled on every other vendor. The
     *       decision and reason are logged at {@code INFO} by
     *       {@code OpenCLContext.resolveEffectiveNoInlineHelpers(...)}. This is the recommended
     *       setting: AMD gets the compile-time fix automatically, NVIDIA keeps full throughput.</li>
     *   <li>{@link Boolean#TRUE} — <b>force on</b> regardless of vendor: the kernel is always built
     *       with {@code -D AMD_NOINLINE_HELPERS}.</li>
     *   <li>{@link Boolean#FALSE} — <b>force off</b> regardless of vendor: the define is never added,
     *       even on AMD (a slow-compile warning is logged). Needed to A/B inlined vs. out-of-line on
     *       an AMD device.</li>
     * </ul>
     * <p>
     * <b>Background.</b> With the kernel inlined, AMD's LLVM-based "LC"/comgr back-end (greedy register
     * allocation + SelectionDAG scheduling) scales ~super-linearly per function, so the single giant
     * {@code generateKeysKernel_grid} takes <b>8–16+ minutes</b> to build on AMD RDNA3 (the full kernel
     * did not finish within 16&nbsp;min on an RX&nbsp;7900&nbsp;XTX / gfx1100). The {@code -D
     * AMD_NOINLINE_HELPERS} define makes the vendored {@code DECLSPEC} helpers
     * {@code __attribute__((noinline))}, partitioning the back-end work into many small functions and
     * cutting the cold compile to <b>≈3&nbsp;s</b>. (Removing the {@code inline} hint alone does nothing
     * — LLVM still inlines at {@code -O3}; only a hard {@code noinline} stops it.)
     * <p>
     * <b>Why AMD-only.</b> Out-of-line calls cost runtime throughput: a matched NVIDIA A/B measured the
     * out-of-line kernel <b>≈4.5× slower</b> on an RTX 3070 (≈ 45 vs ≈ 200 ops/s; see
     * {@code docs/performance.md} §9–§10). On NVIDIA the inlined kernel compiles in seconds anyway, so
     * there is no reason to pay that cost — hence the {@code null}-default auto-detect applies the fix
     * to AMD alone. See {@code docs/performance.md} ("slow AMD compile").
     */
    public @Nullable Boolean noInlineHelpers;
}
