// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.io.Resources;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDeviceSelection;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatformSelector;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;
import org.jocl.CL;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseProgram;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCLContext implements ReleaseCLObject {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    
    public String[] getOpenCLPrograms() throws IOException {
        List<String> resourceNamesContent = getResourceNamesContent(getResourceNames());
        List<String> resourceNamesContentWithReplacements = new ArrayList<>();
        for (String content : resourceNamesContent) {
            String contentWithReplacements = content;
            contentWithReplacements = contentWithReplacements.replaceAll("#include.*", "");
            resourceNamesContentWithReplacements.add(contentWithReplacements);
        }
        String[] openClPrograms = resourceNamesContentWithReplacements.toArray(new String[0]);
        return openClPrograms;
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
    
    private final static String KERNEL_NAME = "generateKeysKernel_grid";
    private final static boolean EXCEPTIONS_ENABLED = true;
    
    private final CProducerOpenCL producerOpenCL;
    private final BitHelper bitHelper;

    private @Nullable OpenCLDevice device;
    private @Nullable cl_context context;
    private @Nullable cl_command_queue commandQueue;
    private @Nullable cl_program program;
    private @Nullable cl_kernel kernel;
    private @Nullable OpenClTask openClTask;
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    
    private boolean closed = false;
    
    public OpenCLContext(CProducerOpenCL producerOpenCL, BitHelper bitHelper) {
        this.producerOpenCL = producerOpenCL;
        this.bitHelper = bitHelper;
    }
    
    public void init() throws IOException {
        
        // #################### general ####################
        
        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(EXCEPTIONS_ENABLED);
        
        List<OpenCLPlatform> platforms = new OpenCLBuilder().build();

        OpenCLDeviceSelection selection = OpenCLPlatformSelector.select(
            platforms,
            producerOpenCL.platformIndex,
            producerOpenCL.deviceType,
            producerOpenCL.deviceIndex
        );
        
        device = selection.device();
        cl_context_properties contextProperties = selection.contextProperties();
        cl_device_id[] cl_device_ids = new cl_device_id[]{device.device()};
        
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

    @Nullable
    OpenClTask getOpenClTask() {
        return openClTask;
    }
    
    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        device = null;
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

    public OpenCLGridResult createKeys(BigInteger privateKeyBase) {
        OpenClTask localOpenClTask = Objects.requireNonNull(openClTask);
        cl_kernel localKernel = Objects.requireNonNull(kernel);
        cl_command_queue localCommandQueue = Objects.requireNonNull(commandQueue);

        localOpenClTask.setSrcPrivateKeyChunk(privateKeyBase);
        ByteBuffer dstByteBuffer = localOpenClTask.executeKernel(localKernel, localCommandQueue);

        OpenCLGridResult openCLGridResult = new OpenCLGridResult(privateKeyBase, producerOpenCL.getOverallWorkSize(bitHelper), dstByteBuffer);
        return openCLGridResult;
    }

    private static List<String> getResourceNamesContent(List<String> resourceNames) throws IOException {
        List<String> contents = new ArrayList<>();
        for (String resourceName : resourceNames) {
            URL url = Resources.getResource(resourceName);
            String content = Resources.toString(url, StandardCharsets.UTF_8);
            contents.add(content);
        }
        return contents;
    }

}
