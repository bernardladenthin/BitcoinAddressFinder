// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseProgram;

import com.google.common.io.Resources;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDeviceSelection;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatformSelector;
import org.jocl.CL;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
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
        cl_context_properties contextProperties = selection.contextProperties();
        cl_device_id[] cl_device_ids = new cl_device_id[] {device.device()};

        // Create a context for the selected device
        context = clCreateContext(contextProperties, 1, cl_device_ids, null, null, null);

        // Create a command-queue for the selected device
        cl_queue_properties properties = new cl_queue_properties();
        commandQueue = clCreateCommandQueueWithProperties(context, device.device(), properties, null);

        // #################### kernel specifix ####################

        String[] openCLPrograms = getOpenCLPrograms();
        // Create the program from the source code
        program = clCreateProgramWithSource(context, openCLPrograms.length, openCLPrograms, null, null);

        // Build the program
        clBuildProgram(program, 0, null, null, null, null);

        // Create the kernel
        kernel = clCreateKernel(program, KERNEL_NAME, null);

        openClTask = new OpenClTask(context, producerOpenCL, bitHelper, byteBufferUtility);
    }

    Optional<OpenClTask> getOpenClTask() {
        return Optional.ofNullable(openClTask);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            if (openClTask != null) {
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

        localOpenClTask.setSrcPrivateKeyChunk(privateKeyBase);
        ByteBuffer dstByteBuffer = localOpenClTask.executeKernel(localKernel, localCommandQueue);

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
