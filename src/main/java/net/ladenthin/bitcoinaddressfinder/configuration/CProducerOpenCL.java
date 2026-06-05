// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import static org.jocl.CL.CL_DEVICE_TYPE_ALL;

import lombok.EqualsAndHashCode;
import lombok.ToString;

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
     * Delay in milliseconds before retrying when the reader is blocked.
     */
    public int delayBlockedReader = 100;

    /**
     * Number of inner iterations each OpenCL work-item performs on the GPU.
     * <p>
     * Instead of launching one work-item per candidate key, the kernel can loop
     * {@code loopCount} times per work-item to reduce the total number of threads
     * and avoid exceeding GPU or host memory limits.
     * <p>
     * This directly reduces the number of launched work-items by a factor of {@code loopCount},
     * and each work-item will compute and write {@code loopCount} results.
     * <p>
     * Requirements:
     * <ul>
     *   <li>{@code loopCount} must be a power of two (e.g. 1, 2, 4, 8, 16, ...)</li>
     *   <li>The total result size (workSize &#x00D7; loopCount &#x00D7; chunkSize) must not exceed {@code Integer.MAX_VALUE}</li>
     *   <li>{@code batchSizeInBits} must be divisible by {@code loopCount}</li>
     * </ul>
     * <p>
     * <b>Performance notes:</b>
     * <ul>
     *   <li>Lower values (e.g. 4 or 8) are often sufficient to reduce launch overhead and register pressure.</li>
     *   <li>Using too high a {@code loopCount} can underutilize the GPU, as fewer total work-items are launched,
     *       which may reduce occupancy and throughput on highly parallel architectures.</li>
     *   <li>This optimization is especially useful when using point addition in the kernel instead of full scalar multiplication.</li>
     * </ul>
     * <p>
     * Example:
     * If {@code batchSizeInBits} = 20 (&#x2248;1 million keys) and {@code loopCount} = 8,
     * the kernel is launched with only 131,072 work-items (1,048,576 &#x00F7; 8),
     * each performing 8 iterations internally.
     */
    public int loopCount = 1;
}
