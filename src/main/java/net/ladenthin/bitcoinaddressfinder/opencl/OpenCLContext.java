// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_MEM_WRITE_ONLY;
import static org.jocl.CL.CL_QUEUE_PROFILING_ENABLE;
import static org.jocl.CL.CL_QUEUE_PROPERTIES;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clFinish;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clReleaseProgram;
import static org.jocl.CL.clSetKernelArg;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenCL context and kernel lifecycle wrapper used by the GPU producer.
 */
// The JOCL upstream API is not annotated for nullness; every clXxx(...) call here
// passes the null values that the OpenCL C ABI accepts (e.g. pfn_notify, user_data,
// errcode_ret). Checker Framework reads those parameters as @NonNull by default and
// flags every site. Suppress at class scope rather than per call — same justification.
@SuppressWarnings({"nullness:argument", "nullness:dereference.of.nullable"})
@ToString
public class OpenCLContext implements ReleaseCLObject {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenCLContext.class);

    /**
     * Loads the OpenCL kernel sources from the classpath and strips {@code #include} directives.
     *
     * @return one source string per kernel source resource
     * @throws IOException if a resource cannot be read
     */
    public String[] getOpenCLPrograms() throws IOException {
        List<String> resourceNamesContent = getResourceNamesContent(getResourceNames());
        List<String> resourceNamesContentWithReplacements = new ArrayList<>(resourceNamesContent.size());
        for (String content : resourceNamesContent) {
            String contentWithReplacements = content;
            contentWithReplacements = contentWithReplacements.replaceAll("#include.*", "");
            resourceNamesContentWithReplacements.add(contentWithReplacements);
        }
        return resourceNamesContentWithReplacements.toArray(new String[0]);
    }

    private List<String> getResourceNames() {
        List<String> resourceNames = new ArrayList<>();
        resourceNames.add("inc_defines.h");
        resourceNames.add("copyfromhashcat/inc_vendor.h");
        resourceNames.add("copyfromhashcat/inc_types.h");
        resourceNames.add("copyfromhashcat/inc_platform.h");
        resourceNames.add("copyfromhashcat/inc_platform.cl");
        resourceNames.add("copyfromhashcat/inc_common.h");
        resourceNames.add("copyfromhashcat/inc_common.cl");
        resourceNames.add("copyfromhashcat/inc_hash_sha256.h");
        resourceNames.add("copyfromhashcat/inc_hash_sha256.cl");
        resourceNames.add("copyfromhashcat/inc_hash_ripemd160.h");
        resourceNames.add("copyfromhashcat/inc_hash_ripemd160.cl");

        resourceNames.add("copyfromhashcat/inc_ecc_secp256k1.h");
        resourceNames.add("copyfromhashcat/inc_ecc_secp256k1.cl");
        // Reduced-radix 2^26 field module (our own file; does not touch copyfromhashcat). Provides the
        // 10x26 multiply/square plus the u32[8] <-> 2^26 compatibility layer used by test_fe10x26 and
        // FieldMulBenchmark. Must come after inc_ecc_secp256k1.* (it reuses u32/u64/PRIVATE_AS) and
        // before the custom kernel file that references its symbols.
        resourceNames.add("inc_ecc_secp256k1_fe10x26.cl");
        resourceNames.add("inc_ecc_secp256k1custom.cl");
        return resourceNames;
    }

    private static final String KERNEL_NAME = "generateKeysKernel_grid";
    private static final boolean EXCEPTIONS_ENABLED = true;

    /**
     * {@code clBuildProgram} options string (Stage-0 quick win). Kept as a single constant so it is
     * trivial to A/B and revert.
     *
     * <ul>
     *   <li>{@code -cl-std=CL2.0} — the kernel's compact mode already relies on OpenCL 2.0
     *       {@code atomic_add} on global memory; pinning the language standard makes that explicit
     *       rather than depending on the driver's default (CL1.2).</li>
     *   <li>{@code -cl-mad-enable} — permits fused multiply-add contraction. This kernel is
     *       integer-only so the effect is expected to be marginal, but it is harmless and part of
     *       the documented quick-win set (see docs/performance.md, "Stage 0").</li>
     * </ul>
     *
     * <p>Deliberately omits {@code -cl-fast-relaxed-math}: it only affects floating-point math, of
     * which this kernel has none.
     */
    private static final String CL_BUILD_OPTIONS = "-cl-std=CL2.0 -cl-mad-enable";

    /**
     * Build define that makes {@code inv_mod} fall back to the legacy binary-GCD modular inverse.
     * Appended to the build options only when {@link CProducerOpenCL#useSafeGcdInverse} is
     * {@code false}; the kernel uses safegcd by default (see {@code inc_ecc_secp256k1.cl}).
     */
    @VisibleForTesting
    static final String LEGACY_BINARY_GCD_INV_MOD_BUILD_OPTION = "-D USE_LEGACY_BINARY_GCD_INV_MOD";

    /** Kernel define that skips the compressed hash160 chain (profiling: isolate one hash chain). */
    @VisibleForTesting
    static final String PROFILE_SKIP_SECOND_HASH160_BUILD_OPTION = "-D PROFILE_SKIP_SECOND_HASH160";

    /** Kernel define that skips both hash160 chains (profiling: isolate EC arithmetic). */
    @VisibleForTesting
    static final String PROFILE_SKIP_HASH160_BUILD_OPTION = "-D PROFILE_SKIP_HASH160";

    /** NVIDIA verbose-build flag; appended when {@link CProducerOpenCL#logGpuDiagnostics} is set. */
    @VisibleForTesting
    static final String NV_VERBOSE_BUILD_OPTION = "-cl-nv-verbose";

    /**
     * Build define that switches the scalar-walker hot loop to the reduced-radix 2²⁶ field
     * ({@code inc_ecc_secp256k1_fe10x26.cl}). Appended only when
     * {@link CProducerOpenCL#useReducedRadixField} is {@code true}; the default walk uses the vendored
     * radix-2³² field.
     */
    @VisibleForTesting
    static final String REDUCED_RADIX_FIELD_BUILD_OPTION = "-D USE_REDUCED_RADIX_FIELD";

    /**
     * Assembles the {@code clBuildProgram} options string: the constant {@link #CL_BUILD_OPTIONS},
     * plus {@link #LEGACY_BINARY_GCD_INV_MOD_BUILD_OPTION} when {@link CProducerOpenCL#useSafeGcdInverse}
     * is {@code false}, the profiling define for a non-{@code FULL}
     * {@link CProducerOpenCL#kernelProfileStage}, {@link #NV_VERBOSE_BUILD_OPTION} when
     * {@link CProducerOpenCL#logGpuDiagnostics} is set, and {@link #REDUCED_RADIX_FIELD_BUILD_OPTION}
     * when {@link CProducerOpenCL#useReducedRadixField} is set.
     *
     * @return the build options string for this context's producer configuration
     */
    @VisibleForTesting
    String buildOptions() {
        final StringBuilder options = new StringBuilder(CL_BUILD_OPTIONS);
        if (!producerOpenCL.useSafeGcdInverse) {
            options.append(' ').append(LEGACY_BINARY_GCD_INV_MOD_BUILD_OPTION);
        }
        switch (producerOpenCL.kernelProfileStage) {
            case ONE_HASH160:
                options.append(' ').append(PROFILE_SKIP_SECOND_HASH160_BUILD_OPTION);
                break;
            case NO_HASH160:
                options.append(' ').append(PROFILE_SKIP_HASH160_BUILD_OPTION);
                break;
            case FULL:
            default:
                break;
        }
        if (producerOpenCL.logGpuDiagnostics) {
            options.append(' ').append(NV_VERBOSE_BUILD_OPTION);
        }
        if (producerOpenCL.useReducedRadixField) {
            options.append(' ').append(REDUCED_RADIX_FIELD_BUILD_OPTION);
        }
        return options.toString();
    }

    private static final ComparableVersion REQUIRED_COMPACT_MODE_VERSION = new ComparableVersion("2.0");

    private final CProducerOpenCL producerOpenCL;
    private final BitHelper bitHelper;

    // JOCL native-pointer wrappers (cl_context / cl_command_queue / cl_program / cl_kernel) —
    // toString is uninformative for any of them. openClTask is also excluded to avoid
    // recursive aggregation into this context's snapshot.
    @ToString.Exclude
    private @Nullable cl_context context;

    @ToString.Exclude
    private @Nullable cl_command_queue commandQueue;

    @ToString.Exclude
    private @Nullable cl_program program;

    @ToString.Exclude
    private @Nullable cl_kernel kernel;

    @ToString.Exclude
    private @Nullable OpenClTask openClTask;

    /**
     * GPU VRAM buffer holding the Binary Fuse 8 fingerprint slot array.
     *
     * <p>Always non-{@code null} between {@link #init()} and {@link #close()}: {@link #init()}
     * allocates a one-byte dummy placeholder so the kernel arguments are always bindable;
     * {@link #uploadGpuFilter} replaces it with the real filter. {@code null} before
     * {@code init()} and after {@code close()}.
     */
    @ToString.Exclude
    private @Nullable cl_mem fuse8FingerprintsMem;

    /**
     * GPU VRAM buffer holding the 5-int Binary Fuse 8 metadata
     * {@code [seedLo, seedHi, segLen, segLenMask, segCountLen]}.
     *
     * <p>Always non-{@code null} between {@link #init()} and {@link #close()}: {@link #init()}
     * allocates a dummy zero-valued metadata block alongside the placeholder fingerprint buffer
     * so the kernel arguments are always bindable; {@link #uploadGpuFilter} replaces it with
     * the real metadata. {@code null} before {@code init()} and after {@code close()}.
     */
    @ToString.Exclude
    private @Nullable cl_mem fuse8MetadataMem;

    /**
     * Whether a real GPU filter has been uploaded via {@link #uploadGpuFilter}. {@code false}
     * means the buffers hold a dummy empty filter (allocated in {@link #init()} so the kernel
     * arguments are always bindable) and the kernel must run in full-transfer mode.
     */
    private boolean gpuFilterUploaded = false;

    /**
     * GPU VRAM buffer holding the precomputed {@code i·G} table used by the single-anchor affine
     * scalar walk (Stage 1). Entry {@code m-1} ({@code m = 1 .. keysPerWorkItem-1}) holds the
     * affine coordinates of {@code m·G} as {@code [x(8 words)][y(8 words)]} in device word order.
     *
     * <p>Built once and uploaded in {@link #init()} (the table is independent of the private key),
     * and released in {@link #close()}. When {@code keysPerWorkItem == 1} the walk never reads it,
     * so a one-byte placeholder is uploaded (zero-size device buffers are invalid) &mdash; mirroring
     * the empty-filter placeholder in {@link #allocateFilterBuffers}. {@code null} before
     * {@code init()} and after {@code close()}.
     */
    @ToString.Exclude
    private @Nullable cl_mem igTableMem;

    /**
     * GPU VRAM buffer holding the fixed-base comb table used to compute the one-time anchor
     * {@code P0 = k0·G} (Stage 2). For {@code pos = 0 .. 63} and {@code digit = 0 .. 15} it holds the
     * affine point {@code (digit · 2^(4·pos))·G} as {@code [x(8 words)][y(8 words)]} in device word
     * order; entry {@code (pos, digit)} is at word offset {@code (pos*16 + digit)*16}. The
     * {@code digit == 0} slot is the point at infinity and is never read (left zero).
     *
     * <p>Built once and uploaded in {@link #init()} (independent of the private key), released in
     * {@link #close()}. ~64 KB. {@code null} before {@code init()} and after {@code close()}.
     */
    @ToString.Exclude
    private @Nullable cl_mem combTableMem;

    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

    private boolean closed = false;

    /**
     * Creates a new OpenCL context.
     *
     * @param producerOpenCL the OpenCL producer configuration
     * @param bitHelper      the bit/batch-size helper
     */
    public OpenCLContext(CProducerOpenCL producerOpenCL, BitHelper bitHelper) {
        this.producerOpenCL = producerOpenCL;
        this.bitHelper = bitHelper;
    }

    /**
     * Initialises the OpenCL context, command queue, program and kernel.
     *
     * @throws IOException if loading the kernel sources fails
     */
    public void init() throws IOException {

        // #################### general ####################

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(EXCEPTIONS_ENABLED);

        List<OpenCLPlatform> platforms = new OpenCLBuilder().build();

        OpenCLPlatformSelector platformSelector = new OpenCLPlatformSelector();
        OpenCLDeviceSelection selection = platformSelector.select(
                platforms, producerOpenCL.platformIndex, producerOpenCL.deviceType, producerOpenCL.deviceIndex);

        OpenCLDevice device = selection.device();
        LOGGER.info("Selected OpenCL device:\n{}", device.toStringPretty());
        // Fail fast on a big-endian device: the kernel canonicalises its output assuming a
        // little-endian device (see OpenClKernelConstants.GPU_NATIVE_WORD_ORDER), so running on
        // a big-endian device would produce silently corrupt results. Reject it cleanly instead.
        assertDeviceByteOrderSupported(device.getByteOrder(), device.deviceName());
        // Compact mode uses atomic_add on global memory, which requires OpenCL 2.0+.
        assertCompactModeDeviceVersionSupported(
                producerOpenCL.enableGpuFilter && !producerOpenCL.transferAll,
                device.getDeviceVersionAsComparableVersion(),
                device.deviceName());
        cl_context_properties contextProperties = selection.contextProperties();
        cl_device_id[] cl_device_ids = new cl_device_id[] {device.device()};

        // Create a context for the selected device
        context = clCreateContext(contextProperties, 1, cl_device_ids, null, null, null);

        // Create a command-queue for the selected device. Opt-in device-side profiling
        // (CL_QUEUE_PROFILING_ENABLE) is enabled only when the diagnostic flag is set, so the
        // production pipeline pays no profiling overhead (see CProducerOpenCL.enableProfiling).
        cl_queue_properties properties = new cl_queue_properties();
        if (producerOpenCL.enableProfiling) {
            properties.addProperty(CL_QUEUE_PROPERTIES, CL_QUEUE_PROFILING_ENABLE);
        }
        commandQueue = clCreateCommandQueueWithProperties(context, device.device(), properties, null);

        // #################### kernel specifix ####################

        String[] openCLPrograms = getOpenCLPrograms();
        // Create the program from the source code
        program = clCreateProgramWithSource(context, openCLPrograms.length, openCLPrograms, null, null);

        // Build the program with the Stage-0 quick-win options (see CL_BUILD_OPTIONS), plus the
        // per-config modular-inverse selector (see CProducerOpenCL.useSafeGcdInverse).
        clBuildProgram(program, 0, null, buildOptions(), null, null);

        if (producerOpenCL.logGpuDiagnostics) {
            logProgramBuildLog(device);
        }

        // Create the kernel
        kernel = clCreateKernel(program, KERNEL_NAME, null);

        logKernelResourceUsage(device);

        openClTask = new OpenClTask(context, producerOpenCL, bitHelper, byteBufferUtility);

        // Allocate a dummy empty filter so the kernel's fuse8 arguments are always bindable.
        // uploadGpuFilter() replaces it with the real filter; until then the kernel runs in
        // full-transfer mode (see createKeys()).
        allocateFilterBuffers(new byte[0], 0, 0, 2, 1, 0);
        gpuFilterUploaded = false;

        // Build and upload the i·G table for the single-anchor affine scalar walk. It depends only
        // on the curve and keysPerWorkItem (not on the private key), so it is built once here.
        uploadIGTable(producerOpenCL.keysPerWorkItem);

        // Build and upload the fixed-base comb table used to compute the one-time anchor P0 = k0·G.
        // Curve-only (key-independent), so built once here.
        uploadCombTable();
    }

    /**
     * Logs the built kernel's per-work-item resource usage (one INFO line at init). These standard
     * {@code clGetKernelWorkGroupInfo} values are the occupancy diagnostic used in {@code
     * docs/performance.md} ("Occupancy / register pressure"): {@code kernelMaxWorkGroupSize} below the
     * device's {@code CL_DEVICE_MAX_WORK_GROUP_SIZE} indicates the kernel is resource- (typically
     * register-) limited, and a non-zero {@code privateMemBytes} indicates register spilling to
     * device-local memory. {@code workGroupSizeMultiple} is the warp/wavefront size.
     *
     * @param device the selected OpenCL device the kernel was built for
     */
    private void logKernelResourceUsage(OpenCLDevice device) {
        final cl_kernel localKernel = Objects.requireNonNull(kernel);
        final long[] value = new long[1];
        CL.clGetKernelWorkGroupInfo(
                localKernel, device.device(), CL.CL_KERNEL_WORK_GROUP_SIZE, Sizeof.size_t, Pointer.to(value), null);
        final long kernelMaxWorkGroupSize = value[0];
        CL.clGetKernelWorkGroupInfo(
                localKernel,
                device.device(),
                CL.CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE,
                Sizeof.size_t,
                Pointer.to(value),
                null);
        final long workGroupSizeMultiple = value[0];
        CL.clGetKernelWorkGroupInfo(
                localKernel, device.device(), CL.CL_KERNEL_PRIVATE_MEM_SIZE, Sizeof.cl_ulong, Pointer.to(value), null);
        final long privateMemBytes = value[0];
        CL.clGetKernelWorkGroupInfo(
                localKernel, device.device(), CL.CL_KERNEL_LOCAL_MEM_SIZE, Sizeof.cl_ulong, Pointer.to(value), null);
        final long localMemBytes = value[0];
        LOGGER.info(
                "Kernel resource usage: kernelMaxWorkGroupSize={} workGroupSizeMultiple={} privateMemBytes={} localMemBytes={}",
                kernelMaxWorkGroupSize,
                workGroupSizeMultiple,
                privateMemBytes,
                localMemBytes);
        // The heuristic starting-config suggestion lives in the device-info block
        // (OpenCLDevice.toStringPretty, logged above and shown by the OpenCLInfo command).
    }

    /**
     * Logs the full {@code clGetProgramBuildInfo} build log (enabled by {@link
     * CProducerOpenCL#logGpuDiagnostics}). On NVIDIA, building with {@link #NV_VERBOSE_BUILD_OPTION}
     * can surface ptxas register/spill stats here; the content is driver-dependent and may be empty.
     *
     * @param device the device the program was built for
     */
    private void logProgramBuildLog(OpenCLDevice device) {
        final cl_program localProgram = Objects.requireNonNull(program);
        final long[] size = new long[1];
        CL.clGetProgramBuildInfo(localProgram, device.device(), CL.CL_PROGRAM_BUILD_LOG, 0, null, size);
        final byte[] logBytes = new byte[(int) size[0]];
        CL.clGetProgramBuildInfo(
                localProgram, device.device(), CL.CL_PROGRAM_BUILD_LOG, logBytes.length, Pointer.to(logBytes), null);
        LOGGER.info("GPU program build log:\n{}", new String(logBytes, StandardCharsets.UTF_8));
    }

    /**
     * Fails fast when the selected OpenCL device does not match the byte order the kernel and
     * the host read path assume ({@link OpenClKernelConstants#GPU_NATIVE_WORD_ORDER}).
     *
     * <p>The kernel canonicalises its {@code u32} output (counts, indices, and the on-device
     * X / Y / hash160 byte-swaps) assuming a little-endian device. Running on a big-endian
     * device would corrupt those words silently, so this rejects it with a clear error rather
     * than producing wrong &mdash; and security-relevant &mdash; results. Extracted as a static
     * method so it can be unit-tested without a real device.
     *
     * @param deviceByteOrder the selected device's byte order (from {@code OpenCLDevice.getByteOrder()})
     * @param deviceName      the device name, for the error message
     * @throws UnsupportedOperationException if {@code deviceByteOrder} is not the supported order
     */
    @VisibleForTesting
    static void assertDeviceByteOrderSupported(ByteOrder deviceByteOrder, String deviceName) {
        if (!OpenClKernelConstants.GPU_NATIVE_WORD_ORDER.equals(deviceByteOrder)) {
            throw new UnsupportedOperationException("OpenCL device '" + deviceName + "' is " + deviceByteOrder
                    + "; only " + OpenClKernelConstants.GPU_NATIVE_WORD_ORDER
                    + " devices are supported (the kernel output is little-endian-canonicalised).");
        }
    }

    /**
     * Fails fast when the selected OpenCL device does not meet the minimum version required
     * for compact GPU filter mode.
     *
     * <p>Compact mode uses {@code atomic_add} on {@code __global uint*} memory, which requires
     * OpenCL 2.0 or later. When compact mode is not requested ({@code compactModeRequested ==
     * false}), this method is a no-op and does not inspect the device version.
     *
     * @param compactModeRequested {@code true} when {@code enableGpuFilter=true} and
     *     {@code transferAll=false}; {@code false} otherwise
     * @param deviceVersion        the selected device's version (from
     *     {@link OpenCLDevice#getDeviceVersionAsComparableVersion()})
     * @param deviceName           the device name, used in the error message
     * @throws UnsupportedOperationException if compact mode is requested but the device version
     *     is below 2.0
     */
    @VisibleForTesting
    static void assertCompactModeDeviceVersionSupported(
            boolean compactModeRequested, ComparableVersion deviceVersion, String deviceName) {
        if (!compactModeRequested) {
            return;
        }
        if (deviceVersion.compareTo(REQUIRED_COMPACT_MODE_VERSION) < 0) {
            throw new UnsupportedOperationException(
                    "Compact GPU filter mode (enableGpuFilter=true, transferAll=false) requires OpenCL 2.0+, "
                            + "but device '" + deviceName + "' reports version " + deviceVersion
                            + ". Set transferAll=true or upgrade to an OpenCL 2.0+ device.");
        }
    }

    /**
     * Returns the current OpenCL task, if one has been initialised.
     *
     * @return the active {@link OpenClTask}, or {@link Optional#empty()} before {@code init()}
     *     has run or after {@code close()}
     */
    public Optional<OpenClTask> getOpenClTask() {
        return Optional.ofNullable(openClTask);
    }

    /**
     * Returns whether this context has been initialised and not yet released.
     *
     * <p>{@code true} after {@link #init()} has created the OpenCL context and before
     * {@link #close()} releases it; {@code false} before {@code init()} and after
     * {@code close()}.
     *
     * @return {@code true} when the OpenCL context is live, {@code false} otherwise
     */
    public boolean isInitialized() {
        return context != null && !closed;
    }

    /**
     * Uploads a Binary Fuse 8 filter to GPU VRAM as two read-only device buffers.
     *
     * <p>The OpenCL layer accepts the filter as primitive arguments only &mdash; it never
     * depends on the persistence type that produced them. The caller (the
     * {@code ProducerOpenCL}, fed by the engine) decomposes the filter's public payload into
     * the fingerprint byte array and the five metadata integers before invoking this method.
     *
     * <p>Two {@code CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR} buffers are allocated and retained
     * for later kernel-argument binding: the fingerprint slot array and a 5-int metadata buffer
     * {@code [seedLo, seedHi, segLen, segLenMask, segCountLen]}. An empty filter
     * ({@code fingerprints.length == 0}, i.e. {@code segCountLen == 0}) still allocates a
     * one-byte placeholder fingerprint buffer (zero-size device buffers are invalid); the kernel
     * detects the empty filter via {@code segCountLen == 0} and never reports a hit.
     *
     * <p>Re-uploading releases any previously uploaded buffers first.
     *
     * @param fingerprints the fingerprint slot array
     * @param seedLo       low 32 bits of the construction seed
     * @param seedHi       high 32 bits of the construction seed
     * @param segLen       per-segment {@code reduce} length
     * @param segLenMask   {@code segLen - 1}
     * @param segCountLen  total fingerprint slot count ({@code fingerprints.length})
     * @throws IllegalStateException if the context has not been initialised
     */
    public void uploadGpuFilter(
            byte[] fingerprints, int seedLo, int seedHi, int segLen, int segLenMask, int segCountLen) {
        allocateFilterBuffers(fingerprints, seedLo, seedHi, segLen, segLenMask, segCountLen);
        gpuFilterUploaded = true;
    }

    private void allocateFilterBuffers(
            byte[] fingerprints, int seedLo, int seedHi, int segLen, int segLenMask, int segCountLen) {
        final cl_context localContext = context;
        if (localContext == null || closed) {
            throw new IllegalStateException("uploadGpuFilter called before init() or after close()");
        }

        // Release any previously uploaded filter so a re-upload does not leak device memory.
        releaseGpuFilter();

        // Zero-size device buffers are invalid; pad an empty filter to a single zero byte. The
        // kernel relies on segCountLen == 0 (not the buffer length) to detect the empty filter.
        final byte[] fingerprintBytes = fingerprints.length == 0 ? new byte[1] : fingerprints;
        final cl_mem localFpMem = clCreateBuffer(
                localContext,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) fingerprintBytes.length,
                Pointer.to(fingerprintBytes),
                null);

        final int[] metadata = new int[] {seedLo, seedHi, segLen, segLenMask, segCountLen};
        final cl_mem localMetaMem;
        try {
            localMetaMem = clCreateBuffer(
                    localContext,
                    CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) metadata.length * Sizeof.cl_int,
                    Pointer.to(metadata),
                    null);
        } catch (RuntimeException e) {
            // First buffer was allocated; free it before propagating so no VRAM is leaked.
            clReleaseMemObject(localFpMem);
            throw e;
        }
        fuse8FingerprintsMem = localFpMem;
        fuse8MetadataMem = localMetaMem;
    }

    private void releaseGpuFilter() {
        if (fuse8FingerprintsMem != null) {
            clReleaseMemObject(fuse8FingerprintsMem);
            fuse8FingerprintsMem = null;
        }
        if (fuse8MetadataMem != null) {
            clReleaseMemObject(fuse8MetadataMem);
            fuse8MetadataMem = null;
        }
    }

    private void uploadIGTable(int keysPerWorkItem) {
        final cl_context localContext = Objects.requireNonNull(context);
        // One entry per walked key (m = 1 .. keysPerWorkItem-1); at least one so the device buffer is
        // never zero-size. When keysPerWorkItem == 1 the walk reads no i*G entry, so the placeholder
        // (left unwritten by the kernel's count=0 run) is never consumed.
        final int entries = Math.max(keysPerWorkItem - 1, 1);
        final long bytes = (long) entries * OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES;
        igTableMem = clCreateBuffer(localContext, CL_MEM_READ_WRITE, bytes, null, null);
        enqueuePrecomputeKernel(igTableMem, "precompute_ig_table", keysPerWorkItem - 1);
    }

    /**
     * Runs a single-work-item fixed-base precompute kernel (see
     * {@code copyfromhashcat/inc_ecc_secp256k1.cl}) from the already-built program to populate
     * {@code out} on the device. Argument 0 is the output buffer; {@code countArg}, when non-{@code
     * null}, binds a trailing {@code const u32 count}.
     *
     * @param out        the device buffer to populate (kernel argument 0)
     * @param kernelName the precompute kernel name
     * @param countArg   value for a trailing {@code const u32 count} argument, or {@code null}
     */
    private void enqueuePrecomputeKernel(cl_mem out, String kernelName, @Nullable Integer countArg) {
        final cl_program localProgram = Objects.requireNonNull(program);
        final cl_command_queue localQueue = Objects.requireNonNull(commandQueue);
        final cl_kernel precomputeKernel = clCreateKernel(localProgram, kernelName, null);
        try {
            clSetKernelArg(precomputeKernel, 0, Sizeof.cl_mem, Pointer.to(out));
            if (countArg != null) {
                clSetKernelArg(precomputeKernel, 1, Sizeof.cl_uint, Pointer.to(new int[] {countArg}));
            }
            clEnqueueNDRangeKernel(localQueue, precomputeKernel, 1, null, new long[] {1L}, null, 0, null, null);
            clFinish(localQueue);
        } finally {
            clReleaseKernel(precomputeKernel);
        }
    }

    private void releaseIGTable() {
        if (igTableMem != null) {
            clReleaseMemObject(igTableMem);
            igTableMem = null;
        }
    }

    private void uploadCombTable() {
        final cl_context localContext = Objects.requireNonNull(context);
        final long bytes = (long) COMB_POSITIONS * COMB_MAGNITUDES * OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES;
        combTableMem = clCreateBuffer(localContext, CL_MEM_READ_WRITE, bytes, null, null);
        enqueuePrecomputeKernel(combTableMem, "precompute_comb_table", null);
    }

    /**
     * Number of 4-bit windows ("positions") in the signed-digit comb: 64 windows covering the
     * 256-bit scalar, plus one extra position for the carry-out of the top window's signed recode
     * (it only ever holds magnitude 1 = {@code 2^256 * G}).
     */
    @VisibleForTesting
    static final int COMB_POSITIONS = 65;

    /**
     * Magnitude slots per position: {@code 1..8}, stored at slot index {@code mag-1}. The comb uses
     * signed digits {@code b in {-8..+7}}; negative digits reuse the magnitude-{@code |b|} entry
     * negated ({@code -P = (x, p - y)}), so only 8 points per position are stored (half the unsigned
     * {@code 0..15} layout).
     */
    @VisibleForTesting
    static final int COMB_MAGNITUDES = 8;

    private void releaseCombTable() {
        if (combTableMem != null) {
            clReleaseMemObject(combTableMem);
            combTableMem = null;
        }
    }

    /**
     * Test hook: runs a single-work-item fixed-base precompute kernel from the already-built program
     * (e.g. {@code precompute_ig_table}, {@code precompute_comb_table} in
     * {@code copyfromhashcat/inc_ecc_secp256k1.cl}) into a fresh {@code CL_MEM_READ_WRITE} buffer and
     * returns the result bytes. This validates the on-device table generation against the bitcoinj
     * reference without changing the production (host-built) table path.
     *
     * @param kernelName  the precompute kernel to run (argument 0 is the output buffer)
     * @param outputBytes size of the output buffer in bytes
     * @param countArg    value for a trailing {@code const u32 count} argument, or {@code null} when
     *     the kernel takes only the output buffer
     * @return the kernel's output buffer contents
     */
    @VisibleForTesting
    byte[] runPrecomputeKernelForTesting(String kernelName, int outputBytes, @Nullable Integer countArg) {
        final cl_context localContext = Objects.requireNonNull(context);
        final cl_command_queue localQueue = Objects.requireNonNull(commandQueue);

        final cl_mem out = clCreateBuffer(localContext, CL_MEM_READ_WRITE, outputBytes, null, null);
        try {
            enqueuePrecomputeKernel(out, kernelName, countArg);

            final ByteBuffer buf = ByteBuffer.allocateDirect(outputBytes);
            clEnqueueReadBuffer(localQueue, out, CL_TRUE, 0, outputBytes, Pointer.to(buf), 0, null, null);
            clFinish(localQueue);

            final byte[] bytes = new byte[outputBytes];
            buf.get(bytes);
            return bytes;
        } finally {
            clReleaseMemObject(out);
        }
    }

    /**
     * Benchmark hook: launches the {@code bench_inv_mod} microbenchmark kernel (see
     * {@code inc_ecc_secp256k1custom.cl}) over {@code globalWorkSize} work-items, each performing
     * {@code iterations} modular inverses with the build-selected {@code inv_mod}, and blocks until it
     * finishes. Used by {@code InvModBenchmark} to isolate the inverse cost; the work is fully on the
     * device (no result is read back). The output buffer and kernel are created and released per call.
     *
     * @param globalWorkSize     number of work-items (e.g. {@code 1 << gridSizeInBits})
     * @param iterations         inverses performed per work-item
     * @param inputHighLimbsZero {@code true} for ~160-bit operands (top three limbs zeroed),
     *     {@code false} for full ~256-bit operands
     */
    @VisibleForTesting
    public void runBenchInvMod(int globalWorkSize, int iterations, boolean inputHighLimbsZero) {
        final cl_context localContext = Objects.requireNonNull(context);
        final cl_program localProgram = Objects.requireNonNull(program);
        final cl_command_queue localQueue = Objects.requireNonNull(commandQueue);

        final cl_kernel benchKernel = clCreateKernel(localProgram, "bench_inv_mod", null);
        final cl_mem out =
                clCreateBuffer(localContext, CL_MEM_WRITE_ONLY, (long) globalWorkSize * Sizeof.cl_uint, null, null);
        try {
            clSetKernelArg(benchKernel, 0, Sizeof.cl_mem, Pointer.to(out));
            clSetKernelArg(benchKernel, 1, Sizeof.cl_uint, Pointer.to(new int[] {iterations}));
            clSetKernelArg(benchKernel, 2, Sizeof.cl_uint, Pointer.to(new int[] {inputHighLimbsZero ? 1 : 0}));
            clEnqueueNDRangeKernel(localQueue, benchKernel, 1, null, new long[] {globalWorkSize}, null, 0, null, null);
            clFinish(localQueue);
        } finally {
            clReleaseMemObject(out);
            clReleaseKernel(benchKernel);
        }
    }

    /**
     * Benchmark hook: launches the {@code bench_fe_mul} microbenchmark kernel (see
     * {@code inc_ecc_secp256k1custom.cl}) over {@code globalWorkSize} work-items, each performing
     * {@code iterations} chained field multiplies, and blocks until it finishes. Used by
     * {@code FieldMulBenchmark} to compare the reduced-radix 2^26 field multiply against the vendored
     * radix-2^32 {@code mul_mod}. The work is fully on the device (no result is read back). The output
     * buffer and kernel are created and released per call.
     *
     * @param globalWorkSize  number of work-items (e.g. {@code 1 << gridSizeInBits})
     * @param iterations      chained multiplies performed per work-item
     * @param useReducedRadix {@code true} for the 2^26 field path, {@code false} for radix-2^32
     *     {@code mul_mod}
     */
    @VisibleForTesting
    public void runBenchFeMul(int globalWorkSize, int iterations, boolean useReducedRadix) {
        final cl_context localContext = Objects.requireNonNull(context);
        final cl_program localProgram = Objects.requireNonNull(program);
        final cl_command_queue localQueue = Objects.requireNonNull(commandQueue);

        final cl_kernel benchKernel = clCreateKernel(localProgram, "bench_fe_mul", null);
        final cl_mem out =
                clCreateBuffer(localContext, CL_MEM_WRITE_ONLY, (long) globalWorkSize * Sizeof.cl_uint, null, null);
        try {
            clSetKernelArg(benchKernel, 0, Sizeof.cl_mem, Pointer.to(out));
            clSetKernelArg(benchKernel, 1, Sizeof.cl_uint, Pointer.to(new int[] {iterations}));
            clSetKernelArg(benchKernel, 2, Sizeof.cl_uint, Pointer.to(new int[] {useReducedRadix ? 1 : 0}));
            clEnqueueNDRangeKernel(localQueue, benchKernel, 1, null, new long[] {globalWorkSize}, null, 0, null, null);
            clFinish(localQueue);
        } finally {
            clReleaseMemObject(out);
            clReleaseKernel(benchKernel);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            releaseGpuFilter();
            releaseIGTable();
            releaseCombTable();
            if (openClTask != null) {
                openClTask.close();
                openClTask = null;
            }
            if (kernel != null) {
                clReleaseKernel(kernel);
                kernel = null;
            }
            if (program != null) {
                clReleaseProgram(program);
                program = null;
            }
            if (commandQueue != null) {
                clReleaseCommandQueue(commandQueue);
                commandQueue = null;
            }
            if (context != null) {
                clReleaseContext(context);
                context = null;
            }
            closed = true;
        }
    }

    /**
     * Runs the kernel for the given private-key base and returns the result grid.
     *
     * @param privateKeyBase the base private key for the batch
     * @return the grid result containing the derived public-key bytes
     */
    public OpenCLGridResult createKeys(BigInteger privateKeyBase) {
        OpenClTask localOpenClTask = Objects.requireNonNull(openClTask);
        cl_kernel localKernel = Objects.requireNonNull(kernel);
        cl_command_queue localCommandQueue = Objects.requireNonNull(commandQueue);
        cl_mem localFuse8FingerprintsMem = Objects.requireNonNull(fuse8FingerprintsMem);
        cl_mem localFuse8MetadataMem = Objects.requireNonNull(fuse8MetadataMem);
        cl_mem localIgTableMem = Objects.requireNonNull(igTableMem);
        cl_mem localCombTableMem = Objects.requireNonNull(combTableMem);

        // Compact (filter) mode only when a real filter is uploaded AND the caller did not force
        // full transfer; otherwise run full transfer (no filter, or vanity forced transferAll).
        final int transferAll = (producerOpenCL.transferAll || !gpuFilterUploaded) ? 1 : 0;

        localOpenClTask.setSrcPrivateKeyChunk(privateKeyBase);
        ByteBuffer dstByteBuffer = localOpenClTask.executeKernel(
                localKernel,
                localCommandQueue,
                localFuse8FingerprintsMem,
                localFuse8MetadataMem,
                transferAll,
                localIgTableMem,
                localCombTableMem);

        // The result owns the readback buffer until closed; close() returns it to the task's reuse
        // pool. The producer's result reader closes it after consuming (see ProducerOpenCL).
        return new OpenCLGridResult(
                privateKeyBase,
                producerOpenCL.getOverallWorkSize(),
                dstByteBuffer,
                () -> localOpenClTask.releaseHostBuffer(dstByteBuffer));
    }

    private static List<String> getResourceNamesContent(Collection<String> resourceNames) throws IOException {
        List<String> contents = new ArrayList<>(resourceNames.size());
        for (String resourceName : resourceNames) {
            URL url = Resources.getResource(resourceName);
            String content = Resources.toString(url, StandardCharsets.UTF_8);
            contents.add(content);
        }
        return contents;
    }
}
