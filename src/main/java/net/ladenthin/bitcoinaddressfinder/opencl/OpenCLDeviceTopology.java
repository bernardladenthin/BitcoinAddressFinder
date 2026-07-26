// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.jocl.CL.clGetDeviceInfo;

import org.jocl.Pointer;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a stable PCIe-topology fingerprint for an OpenCL device, so the same physical GPU exposed
 * by more than one OpenCL platform (a duplicate ICD registration, common on Windows AMD systems where
 * the driver and a second runtime both register) can be recognised and swept only once.
 *
 * <p>The OpenCL device id and platform index are <em>not</em> stable identifiers of a physical GPU:
 * two ICDs enumerate the same card under different {@code cl_device_id} handles and different platform
 * indices. Nor is the device name — a rig of identical cards shares it — so name-based de-duplication
 * would wrongly collapse a multi-GPU miner down to one device. The PCIe bus/device/function tuple is
 * the one identifier that is both unique per physical slot and identical across ICDs.
 *
 * <p>Only AMD's {@code cl_amd_device_attribute_query} extension (which exposes
 * {@code CL_DEVICE_TOPOLOGY_AMD}) is queried. Every other case — a different vendor, a missing
 * extension, or any driver error — returns {@code null}, which callers must treat as "unknown, keep
 * the device". A {@code null} therefore never causes a merge, so an unsupported device can only fall
 * back to today's behaviour (swept once per enumeration), never regress it.
 */
public final class OpenCLDeviceTopology {

    /**
     * {@code CL_DEVICE_TOPOLOGY_AMD} from {@code cl_ext.h}. Not defined by JOCL's {@code CL}, so it is
     * declared here.
     */
    private static final int CL_DEVICE_TOPOLOGY_AMD = 0x4037;

    /** The {@code cl_device_topology_amd} union is 24 bytes: {@code cl_uint} type then a 20-byte body. */
    private static final int CL_DEVICE_TOPOLOGY_AMD_BYTES = 24;

    /**
     * Offset of the PCIe {@code bus} byte inside {@code cl_device_topology_amd.pcie}: a leading
     * {@code cl_uint type} (4 bytes) then {@code cl_char unused[17]} put {@code bus} at byte 21, with
     * {@code device} and {@code function} following it.
     */
    private static final int PCIE_BUS_OFFSET = 21;

    private static final int PCIE_DEVICE_OFFSET = 22;
    private static final int PCIE_FUNCTION_OFFSET = 23;

    /** {@code cl_amd_device_topology_type_pcie} — the only topology type this parses. */
    private static final int CL_DEVICE_TOPOLOGY_TYPE_PCIE_AMD = 1;

    private static final String AMD_ATTRIBUTE_QUERY_EXTENSION = "cl_amd_device_attribute_query";

    private OpenCLDeviceTopology() {
        // static helper
    }

    /**
     * Returns a PCIe-topology fingerprint identifying the physical GPU behind this device, or
     * {@code null} when it cannot be determined (non-AMD vendor, missing extension, or a driver
     * error). Two device entries that share a non-null fingerprint are the same physical GPU.
     *
     * @param device the device to fingerprint
     * @return a stable {@code "amd-pcie:<bus>:<device>:<function>"} fingerprint, or {@code null} when
     *     the topology is unavailable
     */
    public static @Nullable String pciFingerprintOf(OpenCLDevice device) {
        String extensions = device.deviceExtensions();
        if (extensions == null || !extensions.contains(AMD_ATTRIBUTE_QUERY_EXTENSION)) {
            return null;
        }
        try {
            byte[] topology = new byte[CL_DEVICE_TOPOLOGY_AMD_BYTES];
            // A length-1 array receives the number of bytes actually written; the value is unused, but
            // the binding's nullness contract requires a non-null array here.
            long[] bytesWritten = new long[1];
            clGetDeviceInfo(
                    device.device(),
                    CL_DEVICE_TOPOLOGY_AMD,
                    CL_DEVICE_TOPOLOGY_AMD_BYTES,
                    Pointer.to(topology),
                    bytesWritten);
            if ((topology[0] & 0xFF) != CL_DEVICE_TOPOLOGY_TYPE_PCIE_AMD) {
                return null;
            }
            int bus = topology[PCIE_BUS_OFFSET] & 0xFF;
            int slot = topology[PCIE_DEVICE_OFFSET] & 0xFF;
            int function = topology[PCIE_FUNCTION_OFFSET] & 0xFF;
            return "amd-pcie:" + bus + ":" + slot + ":" + function;
        } catch (RuntimeException | LinkageError e) {
            // Any driver/JOCL failure: treat the topology as unknown so the device is kept, never
            // merged. De-duplication is a best-effort optimisation, not a correctness requirement.
            return null;
        }
    }
}
