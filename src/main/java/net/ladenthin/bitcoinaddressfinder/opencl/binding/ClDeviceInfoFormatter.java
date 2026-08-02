// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import lombok.ToString;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL11;
import org.lwjgl.opencl.CL12;
import org.lwjgl.opencl.CL20;

/**
 * Renders OpenCL device-info bit fields as their symbolic names.
 *
 * <p>Replaces the {@code CL.stringFor_*} convenience methods that the previous binding provided and
 * LWJGL does not. The output format is deliberately identical to the old one: device reports are
 * user-facing, and {@code OpenCLDevice.toStringPretty()} is asserted line by line against a fixed
 * sample, so any deviation would be both a visible change and a test failure.
 *
 * <p>Set-valued fields render each name in ascending bit order, every name followed by a space. A
 * field whose value is zero renders as {@code (none)}; a field carrying only unrecognised bits
 * renders as the empty string. Enumerated fields render as the single matching name.
 *
 * <p>An instance class rather than a static utility, per the project convention on helper classes.
 */
@ToString
public class ClDeviceInfoFormatter {

    /**
     * Appended after every symbolic name of a set-valued bit field — including the last one, so the
     * rendered value carries a trailing space.
     *
     * <p>That trailing space is inherited rather than chosen: the previous binding emitted it, and a
     * parity sweep against it proved the behaviour. It is invisible in practice because the device
     * report is compared line by line after {@code stripTrailing()}, which is exactly why the quirk
     * survived unnoticed. Reproduced here so the two implementations agree byte for byte; dropping it
     * is a deliberate cleanup for after the old binding is gone, not a silent change now.
     */
    private static final char NAME_TERMINATOR = ' ';

    /**
     * Rendered value of a bit field whose bits are all unknown or unset. Not the empty string: the
     * previous binding printed this literal, and device reports are compared against fixed samples.
     */
    private static final String NO_FLAGS = "(none)";

    /**
     * {@code cl_device_type} bits in ascending order, paired with their names. The order is part of
     * the rendered output, so it is fixed here rather than derived from a map.
     */
    private static final long[] DEVICE_TYPE_BITS = {
        CL10.CL_DEVICE_TYPE_DEFAULT,
        CL10.CL_DEVICE_TYPE_CPU,
        CL10.CL_DEVICE_TYPE_GPU,
        CL10.CL_DEVICE_TYPE_ACCELERATOR,
        CL12.CL_DEVICE_TYPE_CUSTOM
    };

    /** Names matching {@link #DEVICE_TYPE_BITS}, index by index. */
    private static final String[] DEVICE_TYPE_NAMES = {
        "CL_DEVICE_TYPE_DEFAULT",
        "CL_DEVICE_TYPE_CPU",
        "CL_DEVICE_TYPE_GPU",
        "CL_DEVICE_TYPE_ACCELERATOR",
        "CL_DEVICE_TYPE_CUSTOM"
    };

    /**
     * {@code cl_command_queue_properties} bits in ascending order. The two OpenCL 2.0 device-queue
     * bits belong here as well; leaving them out made a device that reports them render as if it had
     * no queue properties at all.
     */
    private static final long[] QUEUE_PROPERTY_BITS = {
        CL10.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE,
        CL10.CL_QUEUE_PROFILING_ENABLE,
        CL20.CL_QUEUE_ON_DEVICE,
        CL20.CL_QUEUE_ON_DEVICE_DEFAULT
    };

    /** Names matching {@link #QUEUE_PROPERTY_BITS}, index by index. */
    private static final String[] QUEUE_PROPERTY_NAMES = {
        "CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE",
        "CL_QUEUE_PROFILING_ENABLE",
        "CL_QUEUE_ON_DEVICE",
        "CL_QUEUE_ON_DEVICE_DEFAULT"
    };

    /** {@code cl_device_fp_config} bits in ascending order. */
    private static final long[] FP_CONFIG_BITS = {
        CL10.CL_FP_DENORM,
        CL10.CL_FP_INF_NAN,
        CL10.CL_FP_ROUND_TO_NEAREST,
        CL10.CL_FP_ROUND_TO_ZERO,
        CL10.CL_FP_ROUND_TO_INF,
        CL10.CL_FP_FMA,
        CL11.CL_FP_SOFT_FLOAT,
        CL12.CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT
    };

    /** Names matching {@link #FP_CONFIG_BITS}, index by index. */
    private static final String[] FP_CONFIG_NAMES = {
        "CL_FP_DENORM",
        "CL_FP_INF_NAN",
        "CL_FP_ROUND_TO_NEAREST",
        "CL_FP_ROUND_TO_ZERO",
        "CL_FP_ROUND_TO_INF",
        "CL_FP_FMA",
        "CL_FP_SOFT_FLOAT",
        "CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT"
    };

    /** Creates a new {@link ClDeviceInfoFormatter}. */
    public ClDeviceInfoFormatter() {}

    /**
     * Renders a {@code cl_device_type} bit field.
     *
     * @param deviceType the device-type bits
     * @return the symbolic names joined by a space, empty if no known bit is set
     */
    public String formatDeviceType(long deviceType) {
        return formatBitField(deviceType, DEVICE_TYPE_BITS, DEVICE_TYPE_NAMES);
    }

    /**
     * Renders a {@code cl_device_local_mem_type} enumeration value.
     *
     * <p>Only {@code CL_LOCAL} and {@code CL_GLOBAL} are valid here. {@code CL_NONE} shares the value
     * {@code 0} but belongs to other enumerations, so it is reported as invalid rather than accepted
     * — matching the previous binding, whose behaviour a parity sweep confirmed.
     *
     * @param localMemType the local-memory type
     * @return {@code CL_LOCAL}, {@code CL_GLOBAL}, or an "INVALID" description for anything else
     */
    public String formatLocalMemType(int localMemType) {
        if (localMemType == CL10.CL_LOCAL) {
            return "CL_LOCAL";
        }
        if (localMemType == CL10.CL_GLOBAL) {
            return "CL_GLOBAL";
        }
        return "INVALID cl_device_local_mem_type: " + localMemType;
    }

    /**
     * Renders a {@code cl_command_queue_properties} bit field.
     *
     * @param queueProperties the queue-property bits
     * @return the symbolic names joined by a space, empty if no known bit is set
     */
    public String formatCommandQueueProperties(long queueProperties) {
        return formatBitField(queueProperties, QUEUE_PROPERTY_BITS, QUEUE_PROPERTY_NAMES);
    }

    /**
     * Renders a {@code cl_device_fp_config} bit field.
     *
     * @param fpConfig the floating-point configuration bits
     * @return the symbolic names joined by a space, empty if no known bit is set
     */
    public String formatDeviceFpConfig(long fpConfig) {
        return formatBitField(fpConfig, FP_CONFIG_BITS, FP_CONFIG_NAMES);
    }

    /**
     * Renders a bit field by collecting the names of every set bit, in the order given.
     *
     * @param value the bit field to render
     * @param bits  the recognised bits, in the order they should appear
     * @param names the names matching {@code bits}, index by index
     * @return the matching names joined by a space, empty if none matched
     */
    private String formatBitField(long value, long[] bits, String[] names) {
        // Only the all-clear field is reported as "(none)". A field carrying nothing but bits this
        // build does not know renders as the empty string instead - matching the previous binding,
        // as the parity sweep established. The distinction is worth keeping: "(none)" states that
        // the device has no such capability, while "" says the value could not be interpreted.
        if (value == 0L) {
            return NO_FLAGS;
        }
        final StringBuilder present = new StringBuilder();
        for (int i = 0; i < bits.length; i++) {
            if ((value & bits[i]) != 0L) {
                present.append(names[i]).append(NAME_TERMINATOR);
            }
        }
        return present.toString();
    }
}
