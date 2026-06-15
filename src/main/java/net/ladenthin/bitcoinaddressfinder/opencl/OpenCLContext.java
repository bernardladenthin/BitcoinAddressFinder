// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_QUEUE_PROFILING_ENABLE;
import static org.jocl.CL.CL_QUEUE_PROPERTIES;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clReleaseProgram;

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
     *       the documented quick-win set (see docs/ecc-gpu-performance-optimization.md §6).</li>
     * </ul>
     *
     * <p>Deliberately omits {@code -cl-fast-relaxed-math}: it only affects floating-point math, of
     * which this kernel has none.
     */
    private static final String CL_BUILD_OPTIONS = "-cl-std=CL2.0 -cl-mad-enable";

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

        // Build the program with the Stage-0 quick-win options (see CL_BUILD_OPTIONS).
        clBuildProgram(program, 0, null, CL_BUILD_OPTIONS, null, null);

        // Create the kernel
        kernel = clCreateKernel(program, KERNEL_NAME, null);

        openClTask = new OpenClTask(context, producerOpenCL, bitHelper, byteBufferUtility);

        // Allocate a dummy empty filter so the kernel's fuse8 arguments are always bindable.
        // uploadGpuFilter() replaces it with the real filter; until then the kernel runs in
        // full-transfer mode (see createKeys()).
        allocateFilterBuffers(new byte[0], 0, 0, 2, 1, 0);
        gpuFilterUploaded = false;
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

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            releaseGpuFilter();
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

        // Compact (filter) mode only when a real filter is uploaded AND the caller did not force
        // full transfer; otherwise run full transfer (no filter, or vanity forced transferAll).
        final int transferAll = (producerOpenCL.transferAll || !gpuFilterUploaded) ? 1 : 0;

        localOpenClTask.setSrcPrivateKeyChunk(privateKeyBase);
        ByteBuffer dstByteBuffer = localOpenClTask.executeKernel(
                localKernel, localCommandQueue, localFuse8FingerprintsMem, localFuse8MetadataMem, transferAll);

        return new OpenCLGridResult(privateKeyBase, producerOpenCL.getOverallWorkSize(), dstByteBuffer);
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
