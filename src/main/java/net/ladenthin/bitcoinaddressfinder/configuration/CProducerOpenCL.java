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
package net.ladenthin.bitcoinaddressfinder.configuration;

import static org.jocl.CL.CL_DEVICE_TYPE_ALL;

public class CProducerOpenCL extends CProducer {

    public int platformIndex = 0;
    public long deviceType = CL_DEVICE_TYPE_ALL;
    public int deviceIndex = 0;
    
    public int maxResultReaderThreads = 4;
    
    /**
     * in ms.
     */
    public int delayBlockedReader = 100;
    
    /**
     * Number of inner iterations each OpenCL work-item performs on the GPU.
     * <p>
     * Instead of launching one work-item per candidate key, the kernel can loop
     * {@code loopCount} times per work-item to reduce the total number of threads
     * and avoid exceeding GPU or host memory limits.
     * </p>
     * <p>
     * This directly reduces the number of launched work-items by a factor of {@code loopCount},
     * and each work-item will compute and write {@code loopCount} results.
     * </p>
     * <p>
     * Requirements:
     * <ul>
     *   <li>{@code loopCount} must be a power of two (e.g. 1, 2, 4, 8, 16, ...)</li>
     *   <li>The total result size (workSize × loopCount × chunkSize) must not exceed {@code Integer.MAX_VALUE}</li>
     *   <li>{@code batchSizeInBits} must be divisible by {@code loopCount}</li>
     * </ul>
     * </p>
     * <p>
     * <b>Performance notes:</b>
     * <ul>
     *   <li>Lower values (e.g. 4 or 8) are often sufficient to reduce launch overhead and register pressure.</li>
     *   <li>Using too high a {@code loopCount} can underutilize the GPU, as fewer total work-items are launched,
     *       which may reduce occupancy and throughput on highly parallel architectures.</li>
     *   <li>This optimization is especially useful when using point addition in the kernel instead of full scalar multiplication.</li>
     * </ul>
     * </p>
     * <p>
     * Example:
     * If {@code batchSizeInBits} = 20 (≈1 million keys) and {@code loopCount} = 8,
     * the kernel is launched with only 131,072 work-items (1,048,576 ÷ 8),
     * each performing 8 iterations internally.
     * </p>
     */
    public int loopCount = 1;
}
