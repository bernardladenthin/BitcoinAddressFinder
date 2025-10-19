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
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.jocl.CL.*;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses42;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.everyItem;
import static org.mockito.Mockito.mock;

import org.jocl.*;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

@RunWith(DataProviderRunner.class)
public class ProbeAddressesOpenCLTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    
    private static final TestAddresses42 testAddresses = new TestAddresses42(1024, false);
    
    /**
     * 22:  256Mb: executed in: 1253ms, read in:  74ms
     * 23:  512Mb: executed in: 2346ms, read in: 148ms
     * 24: 1024Mb: executed in: 4622ms, read in: 302ms
    */
    private final static int BITS_FOR_BATCH = 8;
    private final static int LOOP_COUNT = BITS_FOR_BATCH >> 1;
    
    private final BitHelper bitHelper = new BitHelper();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    private File tempAddressesFile;

    @Before
    public void init() throws IOException {
        createTemporaryAddressesFile();
    }

    public void fillAddressesFiles(File file) throws IOException {
        FileUtils.writeStringToFile(file, testAddresses.getAsBase58Strings(), StandardCharsets.UTF_8.name());
    }

    public void createTemporaryAddressesFile() throws IOException {
        tempAddressesFile = tempFolder.newFile("addresses.csv");
        fillAddressesFiles(tempAddressesFile);
    }

    @Test
    @OpenCLTest
    public void joclTest() {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        /**
         * The source code of the OpenCL program to execute
         */
        String programSource
                = "__kernel void "
                + "sampleKernel(__global const float *a,"
                + "             __global const float *b,"
                + "             __global float *c)"
                + "{"
                + "    int gid = get_global_id(0);"
                + "    c[gid] = a[gid] * b[gid];"
                + "}";

        // Create input- and output data 
        int n = 10;
        float[] srcArrayA = new float[n];
        float[] srcArrayB = new float[n];
        float[] dstArray = new float[n];
        for (int i = 0; i < n; i++) {
            srcArrayA[i] = i;
            srcArrayB[i] = i;
        }
        Pointer srcA = Pointer.to(srcArrayA);
        Pointer srcB = Pointer.to(srcArrayB);
        Pointer dst = Pointer.to(dstArray);

        // The platform, device type and device number
        // that will be used
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = 0;

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain a platform ID
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Obtain the number of devices for the platform
        int[] numDevicesArray = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        // Obtain a device ID 
        cl_device_id[] devices = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        // Create a context for the selected device
        cl_context context = clCreateContext(
                contextProperties, 1, new cl_device_id[]{device},
                null, null, null);

        // Create a command-queue for the selected device
        cl_queue_properties properties = new cl_queue_properties();
        cl_command_queue commandQueue = clCreateCommandQueueWithProperties(
                context, device, properties, null);

        // Allocate the memory objects for the input- and output data
        cl_mem srcMemA = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long)Sizeof.cl_float * n, srcA, null);
        cl_mem srcMemB = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long)Sizeof.cl_float * n, srcB, null);
        cl_mem dstMem = clCreateBuffer(context,
                CL_MEM_READ_WRITE,
                (long)Sizeof.cl_float * n, null, null);

        // Create the program from the source code
        cl_program program = clCreateProgramWithSource(context,
                1, new String[]{programSource}, null, null);

        // Build the program
        clBuildProgram(program, 0, null, null, null, null);

        // Create the kernel
        cl_kernel kernel = clCreateKernel(program, "sampleKernel", null);

        // Set the arguments for the kernel
        int a = 0;
        clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(srcMemA));
        clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(srcMemB));
        clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(dstMem));

        // Set the work-item dimensions
        long[] global_work_size = new long[]{n};

        // Execute the kernel
        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
                global_work_size, null, 0, null, null);

        // Read the output data
        long cb = (long) n * Sizeof.cl_float;
        clEnqueueReadBuffer(commandQueue, dstMem, CL_TRUE, 0,
                cb, dst, 0, null, null);

        // Release kernel, program, and memory objects
        clReleaseMemObject(srcMemA);
        clReleaseMemObject(srcMemB);
        clReleaseMemObject(dstMem);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);

        // Verify the result
        boolean passed = true;
        final float epsilon = 1e-7f;
        for (int i = 0; i < n; i++) {
            float x = dstArray[i];
            float y = srcArrayA[i] * srcArrayB[i];
            boolean epsilonEqual = Math.abs(x - y) <= epsilon * Math.abs(x);
            if (!epsilonEqual) {
                passed = false;
                break;
            }
        }

        assertThat(passed, is(equalTo(Boolean.TRUE)));
    }

    
    public static int BN_NBITS = 256;
    public static int BN_WSHIFT = 5;
    public static int BN_WBITS = (1 << BN_WSHIFT);
    public static int BN_NWORDS = ((BN_NBITS/8) / 4); // 4 == sizeof(bn_word)

    public static int ACCESS_BUNDLE = 1024;
    public static int ACCESS_STRIDE = (ACCESS_BUNDLE/BN_NWORDS);
    
    @Test
    @Ignore
    public void reverseEngineering_startPoints() {
        int GLOBAL_SIZE = 1024;
        for (int j = 0; j < 1024; j++) {
            for (int k = 0; k < 64; k++) {
                        int i, cell, start;
                        System.out.println("========================================");
                        /* Load the row increment point */
                        i = 2 * j;
                        System.out.println("i: " + i);

                        cell = i;
                        System.out.println("cell: " + cell);
                        start = ((((2 * cell) / ACCESS_STRIDE) * ACCESS_BUNDLE) +
                                 (cell % (ACCESS_STRIDE/2)));
                        System.out.println("start: " + start);

                        int row_in_access_1 = start + (i*ACCESS_STRIDE);
                        System.out.println("row_in_access_1: " + row_in_access_1);

                        start += (ACCESS_STRIDE/2);
                        System.out.println("start: " + start);

                        int row_in_access_2 = start + (i*ACCESS_STRIDE);
                        System.out.println("row_in_access_2: " + row_in_access_2);

                        cell += (k * GLOBAL_SIZE);
                        System.out.println("cell: " + cell);
                        start = (((cell / ACCESS_STRIDE) * ACCESS_BUNDLE) +
                                 (cell % ACCESS_STRIDE));
                        System.out.println("start: " + start);
                        System.out.println("========================================");
            }
        }
    }

    @Test
    @Ignore
    public void calcAddrsFixZeroCl_loadWithoutErrors() throws IOException {
        // ATTENTION: BLDEBUG
        
        
        
        int CELLS = 64;
        int ROW_SIZE = 2; // x1, y1
        int COL_SIZE = 2; // rx, ry
        
        
        String calcAddrsFixZeroClFileName = "calc_addrs.cl";
        URL url = Resources.getResource(calcAddrsFixZeroClFileName);
        String calcAddrsFixZeroCl = Resources.toString(url, StandardCharsets.UTF_8);

        
        // Create input- and output data
        // out:
        int[] src_points_out = new int[ACCESS_BUNDLE];
        int[] src_z_heap = new int[ACCESS_BUNDLE];
        // in:
        int[] src_row_in = new int[ACCESS_BUNDLE * ACCESS_STRIDE * ROW_SIZE];
        int[] src_col_in = new int[ACCESS_BUNDLE * COL_SIZE];

        Pointer pointsOut = Pointer.to(src_points_out);
        Pointer zHeap = Pointer.to(src_z_heap);
        Pointer rowIn = Pointer.to(src_row_in);
        Pointer colIn = Pointer.to(src_col_in);
        
        // The platform, device type and device number
        // that will be used
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = 0;

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain a platform ID
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Obtain the number of devices for the platform
        int[] numDevicesArray = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        // Obtain a device ID 
        cl_device_id[] devices = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        // Create a context for the selected device
        cl_context context = clCreateContext(
                contextProperties, 1, new cl_device_id[]{device},
                null, null, null);

        // Create a command-queue for the selected device
        cl_queue_properties properties = new cl_queue_properties();
        cl_command_queue commandQueue = clCreateCommandQueueWithProperties(
                context, device, properties, null);

        // Allocate the memory objects for the input- and output data
        cl_mem pointsOutMem = clCreateBuffer(context,
                CL_MEM_READ_WRITE,
                (long)Sizeof.cl_int * src_points_out.length,
                pointsOut, null);
        cl_mem zHeapMem = clCreateBuffer(context,
                CL_MEM_READ_WRITE,
                (long)Sizeof.cl_int * src_z_heap.length,
                zHeap, null);
        cl_mem rowInMem = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long)Sizeof.cl_int * src_row_in.length,
                null, null);
        cl_mem colInMem = clCreateBuffer(context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long)Sizeof.cl_int * src_col_in.length,
                null, null);
        
        // Create the program from the source code
        cl_program program = clCreateProgramWithSource(context,
                1, new String[]{calcAddrsFixZeroCl}, null, null);

        // Build the program
        clBuildProgram(program, 0, null, null, null, null);

        // Create the kernel
        cl_kernel kernel_ec_add_grid = clCreateKernel(program, "ec_add_grid", null);
        cl_kernel kernel_heap_invert = clCreateKernel(program, "heap_invert", null);
        cl_kernel kernel_hash_ec_point_get = clCreateKernel(program, "hash_ec_point_get", null);

        // Set the arguments for the kernel
        int a = 0;
        clSetKernelArg(kernel_ec_add_grid, a++, Sizeof.cl_mem, Pointer.to(pointsOutMem));
        clSetKernelArg(kernel_ec_add_grid, a++, Sizeof.cl_mem, Pointer.to(zHeapMem));
        clSetKernelArg(kernel_ec_add_grid, a++, Sizeof.cl_mem, Pointer.to(rowInMem));
        clSetKernelArg(kernel_ec_add_grid, a++, Sizeof.cl_mem, Pointer.to(colInMem));

        
        
        // Set the work-item dimensions
        long[] global_work_size = new long[]{ACCESS_BUNDLE, };

        // Execute the kernel
        clEnqueueNDRangeKernel(commandQueue, kernel_ec_add_grid, 1, null,
                global_work_size, null, 0, null, null);
    }

    @Test
    @OpenCLTest
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BIT_SIZES_AT_MOST_24, location = CommonDataProvider.class)
    public void createKeys_bitsLowerThan25_use32BitNevertheless(int bitSize) throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);

        // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = bitSize;
        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
        openCLContext.init();
            Random sr = new Random(1337);
            BigInteger secret = keyUtility.createSecret(bitSize, sr);
            BigInteger secretBase = keyUtility.killBits(secret, bitHelper.getKillBits(producerOpenCL.batchSizeInBits));

            openCLContext.createKeys(secretBase);
        }
    }
    
    @Test
    @OpenCLTest
    public void createKeys_bitsLowerThanGridSize_useMoreNevertheless() throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);
        
        // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = BITS_FOR_BATCH;
        producerOpenCL.loopCount = LOOP_COUNT;
        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
        openCLContext.init();
            Random sr = new Random(1337);
            BigInteger secret = keyUtility.createSecret(BITS_FOR_BATCH-1, sr);
            BigInteger secretBase = keyUtility.killBits(secret, bitHelper.getKillBits(producerOpenCL.batchSizeInBits));

            openCLContext.createKeys(secretBase);
       }
    }
    
    /**
    * Verifies that at least one test input triggers BigInteger encoding with a leading zero byte (sign bit = 1).
    * This is a prerequisite for testing OpenCL buffer serialization of such keys.
    */
    @Test
    public void dataProvider_largePrivateKeys_containsAtLeastOneEncodingWithLeadingZeroByte() {
        List<Integer> lengths = Arrays.stream(CommonDataProvider.largePrivateKeys())
            .map(data -> ((BigInteger) data[0]).toByteArray().length)
            .collect(Collectors.toList());

        assertThat(
            "Expected at least one BigInteger with 33-byte encoding (sign-preserving leading zero)",
            lengths,
            hasItem(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES + 1)
        );
    }
    
    /**
    * Verifies that all test inputs in largePrivate32ByteKeys() produce a 33-byte encoding
    * due to the high bit being set (i.e., sign-preserving leading zero is required).
    */
   @Test
   public void dataProvider_largePrivateKeys_allHaveLeadingZeroEncoding() {
       List<Integer> lengths = Arrays.stream(CommonDataProvider.privateKeys32ByteRequiringStrip())
           .map(data -> ((BigInteger) data[0]).toByteArray().length)
           .collect(Collectors.toList());

       assertThat(
           "Expected all BigIntegers to be encoded with 33 bytes (sign bit set → leading zero required)",
           lengths,
           everyItem(is(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES + 1))
       );
   }
   
    @OpenCLTest
    @Test(expected = PrivateKeyTooLargeException.class)
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_PRIVATE_KEYS_TOO_LARGE_WITH_CHUNK_SIZE, location = CommonDataProvider.class)
    public void setSrcPrivateKeyChunk_privateKeyTooLarge_throwsException(BigInteger privateKey, int chunkSize) throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        
        CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = chunkSize;
        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
            openCLContext.init();
            OpenClTask openClTask = openCLContext.getOpenClTask();

            // Force a key that exceeds the limit
            openClTask.setSrcPrivateKeyChunk(privateKey);
       }
    }

    @Test
    @OpenCLTest
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_PRIVATE_KEYS_32_BYTE_REQUIRING_STRIP, location = CommonDataProvider.class)
    public void setSrcPrivateKeyChunk_handlesLeadingZero_correctlySerializesTo32Bytes(BigInteger privateKey) throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = BITS_FOR_BATCH;
        producerOpenCL.loopCount = LOOP_COUNT;
        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
            openCLContext.init();
            byte[] encoded = privateKey.toByteArray();
            assertThat("Encoded must hold exactly 33 bytes", encoded.length, is(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES + 1));
            byte[] expectedStripped = Arrays.copyOfRange(encoded, 1, encoded.length);
            assertThat("ExpectedStripped must hold exactly 32 bytes", expectedStripped.length, is(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES));

            // Perform the actual OpenCL buffer population
            OpenClTask openClTask = openCLContext.getOpenClTask();
            openClTask.setSrcPrivateKeyChunk(privateKey);

            ByteBuffer buffer = openClTask.getPrivateKeySourceArgument().getByteBuffer();
            assertThat("Buffer must hold exactly 32 bytes", buffer.capacity(), is(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES));

            byte[] openClEndianBytes = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
            buffer.rewind();
            buffer.get(openClEndianBytes);

            // Reconstruct BigInteger from OpenCL buffer for validation
            byte[] bigEndianBytes = openClEndianBytes.clone();

            // OpenCL provides the bytes in device-specific endianness (could be little-endian or big-endian).
            // BigInteger(byte[]) always expects a Big-Endian (MSB-first) format.
            // Therefore, we convert the device-endian buffer to Big-Endian before creating the BigInteger.
            EndiannessConverter endiannessConverter = new EndiannessConverter(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN, byteBufferUtility);
            endiannessConverter.convertEndian(bigEndianBytes);

            BigInteger result = new BigInteger(1, bigEndianBytes);

            // Validate that the OpenCL buffer correctly represents the original private key
            assertThat("bigEndianBytes content must match stripped BigInteger encoding", bigEndianBytes, is(equalTo(expectedStripped)));
            assertThat("Reconstructed BigInteger must match original private key", result, is(equalTo(privateKey)));

       }
    }
    
    @Test
    @OpenCLTest
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_LARGE_PRIVATE_KEYS, location = CommonDataProvider.class)
    public void createKeys_fromLargePrivateKey_generatesValidPublicKeys(BigInteger privateKey) throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        
        KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);
        
        CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = BITS_FOR_BATCH;
        producerOpenCL.loopCount = LOOP_COUNT;
        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
            openCLContext.init();
            // Perform the actual OpenCL buffer population
            OpenClTask openClTask = openCLContext.getOpenClTask();

            openClTask.setSrcPrivateKeyChunk(privateKey);

            BigInteger secretBase = keyUtility.killBits(privateKey, bitHelper.getKillBits(producerOpenCL.batchSizeInBits));

            OpenCLGridResult createKeys = openCLContext.createKeys(secretBase);
            PublicKeyBytes[] publicKeys = createKeys.getPublicKeyBytes();
            createKeys.freeResult();

            final boolean souts = false;
            assertPublicKeyBytesCalculatedCorrect(publicKeys, secretBase, souts, keyUtility);
       }
    }
    
    @Test
    @OpenCLTest
    public void createKeys_fromRandomPrivateKey_correctlyHashesAndVerifiesResults() throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        
        KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);
        
        // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = BITS_FOR_BATCH;
        producerOpenCL.loopCount = LOOP_COUNT;
        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
            openCLContext.init();
            Random random = new Random(1337);
            BigInteger secretKeyBase = keyUtility.createSecret(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS, random);

            BigInteger secretBase = keyUtility.killBits(secretKeyBase, bitHelper.getKillBits(producerOpenCL.batchSizeInBits));

            OpenCLGridResult createKeys = openCLContext.createKeys(secretBase);
            PublicKeyBytes[] publicKeys = createKeys.getPublicKeyBytes();
            createKeys.freeResult();

            final boolean souts = false;
            hashPublicKeys(publicKeys, souts); // just for performance tests
            hashPublicKeysFast(publicKeys, souts); // just for performance tests

            assertPublicKeyBytesCalculatedCorrect(publicKeys, secretBase, souts, keyUtility);
       }
    }

    private static void assertAllRuntimePublicKeyCalculationsValid(PublicKeyBytes[] publicKeys, Logger logger) {
        for (int i = 0; i < publicKeys.length; i++) {
            assertRuntimePublicKeyCalculationValid(publicKeys[i], logger);
        }
    }

    private static void assertRuntimePublicKeyCalculationValid(PublicKeyBytes publicKeyBytes, Logger logger) {
        boolean valid = publicKeyBytes.runtimePublicKeyCalculationCheck(logger);
        assertThat("runtimePublicKeyCalculationCheck failed for secretKey: " + publicKeyBytes.getSecretKey(), valid, is(true));
    }

    private static void assertPublicKeyBytesCalculatedCorrect(PublicKeyBytes[] publicKeys, BigInteger secretBase, final boolean souts, KeyUtility keyUtility) {
        Logger logger = mock(Logger.class);
        assertAllRuntimePublicKeyCalculationsValid(publicKeys, logger);
        
        for (int i = 0; i < publicKeys.length; i++) {
            if (i%10_000 == 0) {
                if(souts) System.out.println("progress: " + i);
            }
            BigInteger privateKey = AbstractProducer.calculateSecretKey(secretBase, i);
            byte[] privateKeyAsByteArray = privateKey.toByteArray();
            
            if(souts) System.out.println("privateKey: " + Arrays.toString(privateKeyAsByteArray));
            
            PublicKeyBytes publicKeyBytes = publicKeys[i];
            
            ECKey resultOpenCLKeyCompressed = ECKey.fromPrivateAndPrecalculatedPublic(privateKeyAsByteArray, publicKeyBytes.getCompressed());
            ECKey resultOpenCLKeyUncompressed = ECKey.fromPrivateAndPrecalculatedPublic(privateKeyAsByteArray, publicKeyBytes.getUncompressed());
            byte[] resultOpenCLKeyCompressedPubKey = resultOpenCLKeyCompressed.getPubKey();
            byte[] resultOpenCLKeyCompressedPubKeyHash = resultOpenCLKeyCompressed.getPubKeyHash();
            byte[] resultOpenCLKeyUncompressedPubKey = resultOpenCLKeyUncompressed.getPubKey();
            byte[] resultOpenCLKeyUncompressedPubKeyHash = resultOpenCLKeyUncompressed.getPubKeyHash();
            
            if (souts) {
                System.out.println("publicKeyBytes.compressed: " + Arrays.toString(publicKeyBytes.getCompressed()));
                System.out.println("publicKeyBytes.uncompressed: " + Arrays.toString(publicKeyBytes.getUncompressed()));
                System.out.println("resultOpenCLKeyCompressedPubKey: " + Arrays.toString(resultOpenCLKeyCompressedPubKey));
                System.out.println("resultOpenCLKeyCompressedPubKeyHash: " + Arrays.toString(resultOpenCLKeyCompressedPubKeyHash));
                System.out.println("resultOpenCLKeyUncompressedPubKey: " + Arrays.toString(resultOpenCLKeyUncompressedPubKey));
                System.out.println("resultOpenCLKeyUncompressedPubKeyHash: " + Arrays.toString(resultOpenCLKeyUncompressedPubKeyHash));
            }
            
            String resultOpenCLKeyCompressedPubKeyHashBase58 = keyUtility.toBase58(resultOpenCLKeyCompressed.getPubKeyHash());
            String resultOpenCLKeyUncompressedPubKeyHashBase58 = keyUtility.toBase58(resultOpenCLKeyUncompressed.getPubKeyHash());
            
            if (souts) {
                System.out.println("resultOpenCLKeyCompressedPubKeyHashBase58: " + resultOpenCLKeyCompressedPubKeyHashBase58);
                System.out.println("resultOpenCLKeyUncompressedPubKeyHashBase58: " + resultOpenCLKeyUncompressedPubKeyHashBase58);
                
                System.out.println("publicKeyBytes.getCompressedKeyHash(): " + Arrays.toString(publicKeyBytes.getCompressedKeyHash()));
                System.out.println("publicKeyBytes.getUncompressedKeyHash(): " + Arrays.toString(publicKeyBytes.getUncompressedKeyHash()));
                
                System.out.println("publicKeyBytes.getCompressedKeyHashAsBase58(keyUtility): " + publicKeyBytes.getCompressedKeyHashAsBase58(keyUtility));
                System.out.println("publicKeyBytes.getUncompressedKeyHashAsBase58(keyUtility): " + publicKeyBytes.getUncompressedKeyHashAsBase58(keyUtility));
            }
            ECKey expectedUncompressedKey = ECKey.fromPrivate(privateKey, false);
            BigInteger expectedPrivateKeyBigInteger = expectedUncompressedKey.getPrivKey();
            ECKey expectedCompressedKey = ECKey.fromPrivate(privateKey, true);
            
            byte[] expectedCompressedPublicKeyBytes = expectedCompressedKey.getPubKey();
            byte[] expectedUncompressedPublicKeyBytes = expectedUncompressedKey.getPubKey();

            if (souts) {
                System.out.println("expectedPrivateKeyBigInteger: " + expectedPrivateKeyBigInteger);
                System.out.println("expectedCompressedPublicKeyBytes: " + Arrays.toString(expectedCompressedPublicKeyBytes));
                System.out.println("expectedUncompressedPublicKeyBytes: " + Arrays.toString(expectedUncompressedPublicKeyBytes));
            }
            
            String expectedCompressedPublicKeyHashBase58 = keyUtility.toBase58(expectedCompressedKey.getPubKeyHash());
            String expectedUncompressedPublicKeyHashBase58 = keyUtility.toBase58(expectedUncompressedKey.getPubKeyHash());
            
            assertThat(resultOpenCLKeyCompressedPubKey, is(equalTo(expectedCompressedPublicKeyBytes)));
            assertThat(resultOpenCLKeyUncompressedPubKey, is(equalTo(expectedUncompressedPublicKeyBytes)));

            assertThat(publicKeyBytes.getCompressedKeyHashAsBase58(keyUtility), is(equalTo(expectedCompressedPublicKeyHashBase58)));
            assertThat(publicKeyBytes.getUncompressedKeyHashAsBase58(keyUtility), is(equalTo(expectedUncompressedPublicKeyHashBase58)));
            
        }
    }

    private static void hashPublicKeysFast(PublicKeyBytes[] publicKeys, final boolean souts) {
        if (souts) System.out.println("execute hash fast ...");
        long beforeHash = System.currentTimeMillis();
        for (int i = 0; i < publicKeys.length; i++) {
            byte[] compressedKeyHashFast = publicKeys[i].getCompressedKeyHash();
            byte[] uncompressedKeyHashFast = publicKeys[i].getUncompressedKeyHash();
            
            //assertThat(compressedKeyHash, is(equalTo(compressedKeyHashFast)));
            //assertThat(uncompressedKeyHash, is(equalTo(uncompressedKeyHashFast)));
            
            if (souts) System.out.println("publicKeys["+i+"].compressedKeyHashFast: " + Arrays.toString(compressedKeyHashFast));
            if (souts) System.out.println("publicKeys["+i+"].uncompressedKeyHashFast: " + Arrays.toString(uncompressedKeyHashFast));
        }
        long afterHash = System.currentTimeMillis();
        if (souts) System.out.println("... hashed fast in: " + (afterHash - beforeHash) + "ms");
    }

    private static void hashPublicKeys(PublicKeyBytes[] publicKeys, final boolean souts) {
        if (souts) System.out.println("execute hash ...");
        long beforeHash = System.currentTimeMillis();
        for (int i = 0; i < publicKeys.length; i++) {
            byte[] compressedKeyHash = publicKeys[i].getCompressedKeyHash();
            byte[] uncompressedKeyHash = publicKeys[i].getUncompressedKeyHash();
            
            //assertThat(compressedKeyHash, is(equalTo(compressedKeyHashFast)));
            //assertThat(uncompressedKeyHash, is(equalTo(uncompressedKeyHashFast)));
            
            if (souts) System.out.println("publicKeys["+i+"].compressedKeyHash: " + Arrays.toString(compressedKeyHash));
            if (souts) System.out.println("publicKeys["+i+"].uncompressedKeyHash: " + Arrays.toString(uncompressedKeyHash));
        }
        long afterHash = System.currentTimeMillis();
        if (souts) System.out.println("... hashed in: " + (afterHash - beforeHash) + "ms");
    }
    
    /**
     * Read the inner bytes in reverse order. Remove padding bytes to return a clean byte array. Only for x with padding
     */
    @Deprecated
    private static final byte[] getPublicKeyFromByteBuffer(ByteBuffer b, int keyOffset) {
        int paddingBytes = 3;
        int publicKeyByteLength = PublicKeyBytes.SEC_PUBLIC_KEY_COMPRESSED_WORDS * PublicKeyBytes.U32_NUM_BYTES;
        byte[] publicKey = new byte[publicKeyByteLength - paddingBytes];
        // its not inverted because the memory was written in OpenCL
        int offset = publicKeyByteLength * keyOffset;
        outer:
        for (int i=0; i<PublicKeyBytes.SEC_PUBLIC_KEY_COMPRESSED_WORDS; i++) {
            int x = i*PublicKeyBytes.U32_NUM_BYTES;
            for (int j = 0; j < PublicKeyBytes.U32_NUM_BYTES; j++) {
                int publicKeyOffset = x+j;
                if (publicKeyOffset == publicKey.length) {
                    // return the public key, read of all bytes finish
                    break outer;
                }
                int y = PublicKeyBytes.U32_NUM_BYTES-j-1;
                int byteBufferOffset = offset+x+y;
                publicKey[publicKeyOffset] = b.get(byteBufferOffset);
            }
        }
        return publicKey;
    }
    
    @Deprecated
    private static void dumpIntArray(String name, int[] intArray) {
        for (int i = 0; i < intArray.length; i++) {
            System.out.println(name + "["+i+"]: " + Integer.toHexString(intArray[i]));
        }
    }
    
    /**
     * from https://java-browser.yawk.at/org.bouncycastle/bcprov-jdk15/1.46/org/bouncycastle/math/ec/WNafMultiplier.java
     */
    @Deprecated
    private static byte[] windowNaf(byte width, BigInteger k)
    {
        // The window NAF is at most 1 element longer than the binary
        // representation of the integer k. byte can be used instead of short or
        // int unless the window width is larger than 8. For larger width use
        // short or int. However, a width of more than 8 is not efficient for
        // m = log2(q) smaller than 2305 Bits. Note: Values for m larger than
        // 1000 Bits are currently not used in practice.
        byte[] wnaf = new byte[k.bitLength() + 1];

        // 2^width as short and BigInteger
        short pow2wB = (short)(1 << width);
        BigInteger pow2wBI = BigInteger.valueOf(pow2wB);

        int i = 0;

        // The actual length of the WNAF
        int length = 0;

        // while k >= 1
        while (k.signum() > 0)
        {
            // if k is odd
            if (k.testBit(0))
            {
                // k mod 2^width
                BigInteger remainder = k.mod(pow2wBI);

                // if remainder > 2^(width - 1) - 1
                if (remainder.testBit(width - 1))
                {
                    wnaf[i] = (byte)(remainder.intValue() - pow2wB);
                }
                else
                {
                    wnaf[i] = (byte)remainder.intValue();
                }
                // wnaf[i] is now in [-2^(width-1), 2^(width-1)-1]

                k = k.subtract(BigInteger.valueOf(wnaf[i]));
                length = i;
            }
            else
            {
                wnaf[i] = 0;
            }

            // k = k/2
            k = k.shiftRight(1);
            i++;
        }

        length++;

        // Reduce the WNAF array to its actual length
        byte[] wnafShort = new byte[length];
        System.arraycopy(wnaf, 0, wnafShort, 0, length);
        return wnafShort;
    }

}
