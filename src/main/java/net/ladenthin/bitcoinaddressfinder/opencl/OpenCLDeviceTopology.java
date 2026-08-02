// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.OpenClBinding;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a stable fingerprint of the <em>physical</em> GPU behind an OpenCL device, so the same
 * card exposed by more than one OpenCL platform — a duplicate ICD registration, common on Windows
 * AMD systems where the driver and a second runtime both register — is recognised and swept only
 * once.
 *
 * <h2>Why neither the handle nor the name will do</h2>
 * The {@code cl_device_id} and the platform index are not stable identifiers of a card: two ICDs
 * enumerate the same GPU under different handles at different indices. The device name is not one
 * either, in the opposite direction — a rig of identical cards shares it, so merging on the name
 * would collapse a working multi-GPU machine down to a single device. What is needed is an
 * identifier that is the same across ICDs and different across slots.
 *
 * <h2>Three sources, vendor-neutral first</h2>
 * <ol>
 *   <li>{@code cl_khr_device_uuid} — 16 opaque bytes that exist to identify a physical device.
 *   <li>{@code cl_khr_pci_bus_info} — the PCIe domain/bus/device/function tuple.
 *   <li>{@code cl_amd_device_attribute_query} — AMD's older vendor equivalent, for drivers
 *       predating the Khronos extensions.
 * </ol>
 *
 * <p>The first two are Khronos standards, and they are the reason this is not an AMD-only facility:
 * a contributor's device dump shows both an NVIDIA RTX 500 Ada and an Intel Arc reporting them, so
 * keying on the vendor extension alone would leave every non-AMD machine with no fingerprint at all.
 *
 * <h2>Unknown never merges</h2>
 * Every failure — no supporting extension, a driver error, an implausible payload — yields
 * {@code null}, which callers must read as "unknown, keep this device". A {@code null} therefore
 * cannot cause a merge, so an unsupported device falls back to today's behaviour (swept once per
 * enumeration entry) and can never be wrongly folded into another.
 */
@ToString
public class OpenCLDeviceTopology {

    /** {@code CL_DEVICE_UUID_KHR} from {@code cl_ext.h}; not exposed as a binding constant. */
    private static final int CL_DEVICE_UUID_KHR = 0x106A;

    /** {@code CL_UUID_SIZE_KHR}: the UUID is exactly this many opaque bytes. */
    private static final int CL_UUID_SIZE_KHR = 16;

    /** {@code CL_DEVICE_PCI_BUS_INFO_KHR} from {@code cl_ext.h}. */
    private static final int CL_DEVICE_PCI_BUS_INFO_KHR = 0x410F;

    /** {@code cl_device_pci_bus_info_khr}: four {@code cl_uint}s — domain, bus, device, function. */
    private static final int PCI_BUS_INFO_BYTES = 16;

    /** {@code CL_DEVICE_TOPOLOGY_AMD} from {@code cl_ext.h}. */
    private static final int CL_DEVICE_TOPOLOGY_AMD = 0x4037;

    /** The {@code cl_device_topology_amd} union is 24 bytes: a {@code cl_uint} type then a 20-byte body. */
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

    private static final String DEVICE_UUID_EXTENSION = "cl_khr_device_uuid";
    private static final String PCI_BUS_INFO_EXTENSION = "cl_khr_pci_bus_info";
    private static final String AMD_ATTRIBUTE_QUERY_EXTENSION = "cl_amd_device_attribute_query";

    /**
     * Splits {@code CL_DEVICE_EXTENSIONS}, which is space-separated. Empty tokens are dropped so a
     * trailing or doubled space — both occur in real driver strings — cannot match anything.
     */
    private static final Splitter EXTENSION_SPLITTER = Splitter.on(' ').omitEmptyStrings();

    private final ClApi clApi;

    /** Creates a topology resolver using the project's default OpenCL binding. */
    public OpenCLDeviceTopology() {
        this(OpenClBinding.defaultClApi());
    }

    /**
     * Creates a topology resolver on an explicit OpenCL API, so tests can substitute it.
     *
     * @param clApi the OpenCL API used to read the identity properties
     */
    public OpenCLDeviceTopology(ClApi clApi) {
        this.clApi = clApi;
    }

    /**
     * Returns a fingerprint identifying the physical GPU behind this device, or {@code null} when it
     * cannot be determined. Two device entries sharing a non-null fingerprint are the same card.
     *
     * @param device the device to fingerprint
     * @return a stable fingerprint, or {@code null} when no source is available
     */
    public @Nullable String physicalDeviceFingerprintOf(OpenCLDevice device) {
        String extensions = device.deviceExtensions();
        if (supportsDeviceUuid(extensions)) {
            String uuid = parseDeviceUuid(query(device, CL_DEVICE_UUID_KHR, CL_UUID_SIZE_KHR));
            if (uuid != null) {
                return uuid;
            }
        }
        if (supportsPciBusInfo(extensions)) {
            String pci = parsePciBusInfo(query(device, CL_DEVICE_PCI_BUS_INFO_KHR, PCI_BUS_INFO_BYTES));
            if (pci != null) {
                return pci;
            }
        }
        if (supportsAmdTopology(extensions)) {
            return parseAmdTopology(query(device, CL_DEVICE_TOPOLOGY_AMD, CL_DEVICE_TOPOLOGY_AMD_BYTES));
        }
        return null;
    }

    /**
     * Reads a device-info property into a byte array.
     *
     * @param device the device to query
     * @param param  the {@code cl_device_info} parameter name
     * @param bytes  the expected payload size
     * @return the payload, or an empty array when the driver rejected the query — the parsers below
     *     reject it on length, so an empty array and a refusal are the same outcome without a null
     */
    private byte[] query(OpenCLDevice device, int param, int bytes) {
        // Any driver failure is absorbed by the API and surfaces as an empty payload: the identity
        // is then unknown, so the device is kept and never merged. De-duplication is a best-effort
        // optimisation, not a correctness requirement.
        return clApi.getDeviceInfoBytesOrEmpty(device.device(), param, bytes);
    }

    /**
     * Renders {@code CL_DEVICE_UUID_KHR} as a fingerprint.
     *
     * <p>The bytes are opaque by specification, so they are reproduced verbatim rather than
     * interpreted — no endianness applies. An all-zero UUID is rejected: that is what a driver
     * returns when it has nothing to report, and it would make every such device look identical.
     *
     * @param uuid the raw 16-byte payload, or {@code null}
     * @return the fingerprint, or {@code null} when the payload is unusable
     */
    @VisibleForTesting
    @Nullable
    String parseDeviceUuid(byte @Nullable [] uuid) {
        if (uuid == null || uuid.length != CL_UUID_SIZE_KHR) {
            return null;
        }
        StringBuilder hex = new StringBuilder(2 * CL_UUID_SIZE_KHR);
        boolean allZero = true;
        for (byte value : uuid) {
            if (value != 0) {
                allZero = false;
            }
            hex.append(String.format(Locale.ROOT, "%02x", value));
        }
        return allZero ? null : "uuid:" + hex;
    }

    /**
     * Renders {@code cl_device_pci_bus_info_khr} as a fingerprint.
     *
     * <p>The four fields are {@code cl_uint}s in <b>host</b> byte order, so they are read with
     * {@link ByteOrder#nativeOrder()}. Reading them with a fixed endianness would yield a
     * fingerprint that is stable on one machine and meaningless on another — worse than none, since
     * a meaningless fingerprint can merge two distinct cards.
     *
     * @param payload the raw 16-byte payload, or {@code null}
     * @return the fingerprint, or {@code null} when the payload is unusable
     */
    @VisibleForTesting
    @Nullable
    String parsePciBusInfo(byte @Nullable [] payload) {
        if (payload == null || payload.length != PCI_BUS_INFO_BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.nativeOrder());
        long domain = Integer.toUnsignedLong(buffer.getInt());
        long bus = Integer.toUnsignedLong(buffer.getInt());
        long slot = Integer.toUnsignedLong(buffer.getInt());
        long function = Integer.toUnsignedLong(buffer.getInt());
        return "pci:" + domain + ":" + bus + ":" + slot + ":" + function;
    }

    /**
     * Renders AMD's {@code cl_device_topology_amd} as a fingerprint.
     *
     * <p>Kept for drivers predating the Khronos extensions above. The leading {@code type} is a
     * {@code cl_uint} and is read as one; anything other than the PCIe variant carries no slot
     * information this can use.
     *
     * @param topology the raw 24-byte union, or {@code null}
     * @return the fingerprint, or {@code null} when the payload is unusable
     */
    @VisibleForTesting
    @Nullable
    String parseAmdTopology(byte @Nullable [] topology) {
        if (topology == null || topology.length != CL_DEVICE_TOPOLOGY_AMD_BYTES) {
            return null;
        }
        int type = ByteBuffer.wrap(topology).order(ByteOrder.nativeOrder()).getInt();
        if (type != CL_DEVICE_TOPOLOGY_TYPE_PCIE_AMD) {
            return null;
        }
        int bus = topology[PCIE_BUS_OFFSET] & 0xFF;
        int slot = topology[PCIE_DEVICE_OFFSET] & 0xFF;
        int function = topology[PCIE_FUNCTION_OFFSET] & 0xFF;
        return "amd-pcie:" + bus + ":" + slot + ":" + function;
    }

    /**
     * Returns whether the extension list advertises {@code cl_khr_device_uuid}.
     *
     * @param extensions the space-separated {@code CL_DEVICE_EXTENSIONS} string
     * @return {@code true} if the extension is present
     */
    @VisibleForTesting
    boolean supportsDeviceUuid(String extensions) {
        return hasExtension(extensions, DEVICE_UUID_EXTENSION);
    }

    /**
     * Returns whether the extension list advertises {@code cl_khr_pci_bus_info}.
     *
     * @param extensions the space-separated {@code CL_DEVICE_EXTENSIONS} string
     * @return {@code true} if the extension is present
     */
    @VisibleForTesting
    boolean supportsPciBusInfo(String extensions) {
        return hasExtension(extensions, PCI_BUS_INFO_EXTENSION);
    }

    /**
     * Returns whether the extension list advertises {@code cl_amd_device_attribute_query}.
     *
     * @param extensions the space-separated {@code CL_DEVICE_EXTENSIONS} string
     * @return {@code true} if the extension is present
     */
    @VisibleForTesting
    boolean supportsAmdTopology(String extensions) {
        return hasExtension(extensions, AMD_ATTRIBUTE_QUERY_EXTENSION);
    }

    /**
     * Matches an extension as a whole token.
     *
     * <p>A substring test would report support for {@code cl_khr_device_uuid} on a device advertising
     * only {@code cl_khr_device_uuid_extended} — a different extension, whose absence would then
     * surface as a driver error rather than as the honest "unsupported".
     *
     * @param extensions the space-separated extension list
     * @param extension  the extension to look for
     * @return {@code true} if the list contains it as a whole token
     */
    private boolean hasExtension(String extensions, String extension) {
        for (String candidate : EXTENSION_SPLITTER.split(extensions)) {
            if (candidate.equals(extension)) {
                return true;
            }
        }
        return false;
    }
}
