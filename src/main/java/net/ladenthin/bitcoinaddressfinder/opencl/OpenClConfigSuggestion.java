// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;

/**
 * Heuristic starting-point suggestion for the two main OpenCL tuning knobs ({@code batchSizeInBits}
 * and {@code keysPerWorkItem}), derived from the device's reported capabilities plus the calibration
 * measured in {@code docs/performance.md}.
 *
 * <p><b>This is a coarse starting point, not an optimum.</b> The real sweet spot is device-specific
 * and thermally noisy (see {@code docs/performance.md} §4/§6); always sweep {@code keysPerWorkItem}
 * on the actual hardware. The defaults are deliberately conservative and clamped to the validated
 * range.
 *
 * <p>Heuristics:
 * <ul>
 *   <li><b>{@code batchSizeInBits}</b> — the largest batch whose full-transfer result buffer
 *       ({@code 2^bits × }{@link OpenClKernelConstants#OUTPUT_ENTRY_SIZE_BYTES}) fits within a
 *       quarter of {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE} (headroom for the readback-buffer pool and
 *       the other device buffers), clamped to {@code [MIN_BATCH_SIZE_IN_BITS, MAX_BATCH_SIZE_IN_BITS]}
 *       (kept below the hard {@link OpenClKernelConstants#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} cap).</li>
 *   <li><b>{@code keysPerWorkItem}</b> — chosen so the launch keeps roughly
 *       {@link #TARGET_WORK_ITEMS_PER_COMPUTE_UNIT} work-items per compute unit (calibrated from the
 *       RTX 3070: its ~8192-work-item peak at {@code batchSizeInBits=20} over 40 compute units ≈ 205
 *       work-items/CU). Rounded down to a power of two and clamped to {@code [1,
 *       MAX_KEYS_PER_WORK_ITEM]}.</li>
 * </ul>
 *
 * @param batchSizeInBits suggested {@code producerOpenCL.batchSizeInBits}
 * @param keysPerWorkItem suggested {@code producerOpenCL.keysPerWorkItem}
 */
public record OpenClConfigSuggestion(int batchSizeInBits, int keysPerWorkItem) {

    /** Lowest batch size suggested (a per-launch grid below this underuses any GPU). */
    static final int MIN_BATCH_SIZE_IN_BITS = 14;

    /**
     * Highest batch size suggested. Below the hard {@link OpenClKernelConstants#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY}
     * cap and within the validated high-end range (see {@code docs/performance.md} §3).
     */
    static final int MAX_BATCH_SIZE_IN_BITS = 21;

    /** Largest {@code keysPerWorkItem} suggested (the upper end of the validated sweep in §4). */
    static final int MAX_KEYS_PER_WORK_ITEM = 256;

    /**
     * Target resident work-items per compute unit, calibrated from the RTX 3070 measurement
     * (~8192 work-items over 40 compute units at the peak). Used to pick {@code keysPerWorkItem} so
     * the grid stays large enough to saturate the device while still amortizing the one-time anchor.
     */
    static final int TARGET_WORK_ITEMS_PER_COMPUTE_UNIT = 200;

    /** Fraction (1/N) of {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE} the result buffer may use. */
    static final long RESULT_BUFFER_MEM_DIVISOR = 4L;

    /**
     * Computes a suggested starting config for a device.
     *
     * @param maxComputeUnits  the device's {@code CL_DEVICE_MAX_COMPUTE_UNITS} (≥ 1)
     * @param maxMemAllocBytes the device's {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE} in bytes
     * @return the suggested {@link OpenClConfigSuggestion}
     */
    public static OpenClConfigSuggestion suggest(int maxComputeUnits, long maxMemAllocBytes) {
        final int computeUnits = Math.max(1, maxComputeUnits);

        // batchSizeInBits: largest batch whose full-transfer buffer fits the memory budget.
        final long memBudgetBytes = Math.max(1L, maxMemAllocBytes / RESULT_BUFFER_MEM_DIVISOR);
        final long maxEntries = Math.max(1L, memBudgetBytes / OpenClKernelConstants.OUTPUT_ENTRY_SIZE_BYTES);
        final int bitsByMemory = 63 - Long.numberOfLeadingZeros(maxEntries); // floor(log2(maxEntries))
        final int batchSizeInBits = clamp(bitsByMemory, MIN_BATCH_SIZE_IN_BITS, MAX_BATCH_SIZE_IN_BITS);

        // keysPerWorkItem: keep ~TARGET work-items per compute unit; round down to a power of two.
        final long targetWorkItems = (long) TARGET_WORK_ITEMS_PER_COMPUTE_UNIT * computeUnits;
        final long workItemsAtBatch = 1L << batchSizeInBits;
        final long rawKeysPerWorkItem = workItemsAtBatch / Math.max(1L, targetWorkItems);
        final int keysPerWorkItem = clampToPowerOfTwo(rawKeysPerWorkItem, 1, MAX_KEYS_PER_WORK_ITEM);

        return new OpenClConfigSuggestion(batchSizeInBits, keysPerWorkItem);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Rounds {@code value} down to a power of two, then clamps to {@code [min, max]}. */
    private static int clampToPowerOfTwo(long value, int min, int max) {
        if (value < 1L) {
            return min;
        }
        final long powerOfTwo = Long.highestOneBit(value); // largest power of two ≤ value
        return clamp((int) Math.min(powerOfTwo, (long) max), min, max);
    }
}
