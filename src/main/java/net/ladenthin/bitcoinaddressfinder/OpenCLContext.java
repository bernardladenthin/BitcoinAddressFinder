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
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;
import org.jocl.CL;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCLContext {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    
    public String[] getOpenCLPrograms() throws IOException {
        List<String> resourceNamesContent = getResourceNamesContent(getResourceNames());
        List<String> resourceNamesContentWithReplacements = new ArrayList<>();
        for (String content : resourceNamesContent) {
            String contentWithReplacements = content;
            contentWithReplacements = contentWithReplacements.replaceAll("#include.*", "");
            contentWithReplacements = contentWithReplacements.replaceAll("GLOBAL_AS const secp256k1_t \\*tmps", "const secp256k1_t \\*tmps");
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

        resourceNames.add("copyfromhashcat/inc_ecc_secp256k1.h");
        resourceNames.add("copyfromhashcat/inc_ecc_secp256k1.cl");
        resourceNames.add("inc_ecc_secp256k1custom.cl");
        return resourceNames;
    }

    private final static String NON_CHUNK_KERNEL_NAME = "generateKeysKernel_grid";
    private final static String CHUNK_KERNEL_NAME = "generateKeyChunkKernel_grid";
    private final static boolean EXCEPTIONS_ENABLED = true;
    
    private final CProducerOpenCL producerOpenCL;

    private cl_context_properties contextProperties;
    private cl_device_id device;
    private cl_context context;
    private cl_command_queue commandQueue;
    private cl_program program;
    private cl_kernel kernel;
    private OpenClTask openClTask;
    
    public OpenCLContext(CProducerOpenCL producerOpenCL) {
        this.producerOpenCL = producerOpenCL;
    }
    
    /**
     * Sets all properties and parameters to finally create the OpenCL kernel.
     *
     * @throws IOException When an error occurs while reading a resource.
     */
    public void init() throws IOException {
        
        // #################### general ####################
        
        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(EXCEPTIONS_ENABLED);
        
        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];
        
        // Obtain a platform ID
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[producerOpenCL.platformIndex];
        
        // Initialize the context properties
        contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
        
        // Obtain the number of devices for the platform
        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, producerOpenCL.deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];
        
        // Obtain a device ID 
        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, producerOpenCL.deviceType, numDevices, devices, null);
        device = devices[producerOpenCL.deviceIndex];
        cl_device_id[] cl_device_ids = new cl_device_id[]{device};
        
        // Create a context for the selected device
        context = clCreateContext(contextProperties, 1, cl_device_ids, null, null, null);
        
        // Create a command-queue for the selected device
        cl_queue_properties properties = new cl_queue_properties();
        commandQueue = clCreateCommandQueueWithProperties(context, device, properties, null);
        
        // #################### kernel specifix ####################
        
        String[] openCLPrograms = getOpenCLPrograms();
        // Create the program from the source code
        program = clCreateProgramWithSource(context, openCLPrograms.length, openCLPrograms, null, null);

        // Build the program
        clBuildProgram(program, 0, null, null, null, null);
        
        // Create the kernel
        if (producerOpenCL.chunkMode) {
            kernel = clCreateKernel(program, CHUNK_KERNEL_NAME, null);
        } else {
            kernel = clCreateKernel(program, NON_CHUNK_KERNEL_NAME, null);
        }

        openClTask = new OpenClTask(context, producerOpenCL);
    }

    protected OpenClTask getOpenClTask() {
        return openClTask;
    }

    public void release() {
        openClTask.releaseCl();
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
    }

    /**
     * This method executes the OpenCL kernel to generate publicKeys. Depending on the parameter <strong>chunkMode</strong> it
     * will automatically generate new privateKeys based on the first privateKey that was passed.
     *
     * @param privateKeys In case of <strong>chunkMode = true</strong> this method only needs
     *                    one privateKey, but in case of <strong>chunkMode = false</strong>
     *                    it needs exactly as many private keys as the work size.
     * @return publicKeys as {@link OpenCLGridResult}
     */
    public OpenCLGridResult createKeys(BigInteger[] privateKeys) {
        openClTask.setSrcPrivateKeys(privateKeys);
        ByteBuffer dstByteBuffer = openClTask.executeKernel(kernel, commandQueue);

        OpenCLGridResult openCLGridResult = null;
        try {
            openCLGridResult = new OpenCLGridResult(privateKeys, producerOpenCL.getWorkSize(), dstByteBuffer,
                    producerOpenCL.chunkMode);
        } catch (InvalidWorkSizeException e) {
            // TODO Handle a thrown InvalidWorkSizeException
            e.printStackTrace();
        }
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
