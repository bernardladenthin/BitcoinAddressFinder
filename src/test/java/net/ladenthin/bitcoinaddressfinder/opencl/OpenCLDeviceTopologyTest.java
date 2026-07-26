// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OpenCLDeviceTopology}.
 *
 * <p>The byte layouts are asserted directly rather than through a device, because the whole point of
 * this class is to survive hardware nobody testing it owns. A wrong offset produces a plausible but
 * meaningless fingerprint, and a meaningless fingerprint either merges two distinct GPUs — silently
 * halving a rig — or fails to merge one card seen twice. Neither shows up as an error at runtime.
 */
public class OpenCLDeviceTopologyTest {

    public OpenCLDeviceTopologyTest() {}

    // <editor-fold defaultstate="collapsed" desc="cl_khr_device_uuid">
    /**
     * {@code CL_DEVICE_UUID_KHR} is 16 opaque bytes that identify the physical device. Opaque means
     * they are reproduced verbatim: no endianness applies, and interpreting them would be wrong.
     */
    @Test
    public void parseDeviceUuid_sixteenBytes_isRenderedVerbatimAsHex() {
        byte[] uuid = new byte[] {
            (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
            (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
            (byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0x98,
            (byte) 0x76, (byte) 0x54, (byte) 0x32, (byte) 0x10
        };

        assertThat(OpenCLDeviceTopology.parseDeviceUuid(uuid), is(equalTo("uuid:0123456789abcdeffedcba9876543210")));
    }

    /** An all-zero UUID is what a driver returns when it has nothing to report. It identifies nothing. */
    @Test
    public void parseDeviceUuid_allZero_isRejected() {
        assertThat(OpenCLDeviceTopology.parseDeviceUuid(new byte[16]), is(nullValue()));
    }

    @Test
    public void parseDeviceUuid_wrongLength_isRejected() {
        assertThat(OpenCLDeviceTopology.parseDeviceUuid(new byte[8]), is(nullValue()));
    }

    /** Two different devices must not collide. */
    @Test
    public void parseDeviceUuid_differentUuids_produceDifferentFingerprints() {
        byte[] first = new byte[16];
        first[0] = 1;
        byte[] second = new byte[16];
        second[15] = 1;

        assertThat(
                OpenCLDeviceTopology.parseDeviceUuid(first),
                is(not(equalTo(OpenCLDeviceTopology.parseDeviceUuid(second)))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="cl_khr_pci_bus_info">
    /**
     * {@code cl_device_pci_bus_info_khr} is four {@code cl_uint}s — domain, bus, device, function —
     * in host byte order. Reading them with a fixed endianness would produce a fingerprint that is
     * stable on one machine and wrong on another, which is worse than none.
     */
    @Test
    public void parsePciBusInfo_fourHostOrderIntegers_areReadInOrder() {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());
        buffer.putInt(0).putInt(0x0B).putInt(0x00).putInt(0x01);

        assertThat(OpenCLDeviceTopology.parsePciBusInfo(buffer.array()), is(equalTo("pci:0:11:0:1")));
    }

    /**
     * Two cards in different slots must stay apart — this is the case that a name-based merge would
     * get wrong on a rig of identical GPUs.
     */
    @Test
    public void parsePciBusInfo_differentSlots_produceDifferentFingerprints() {
        ByteBuffer first = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());
        first.putInt(0).putInt(0x0B).putInt(0).putInt(0);
        ByteBuffer second = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());
        second.putInt(0).putInt(0x0C).putInt(0).putInt(0);

        assertThat(
                OpenCLDeviceTopology.parsePciBusInfo(first.array()),
                is(not(equalTo(OpenCLDeviceTopology.parsePciBusInfo(second.array())))));
    }

    @Test
    public void parsePciBusInfo_wrongLength_isRejected() {
        assertThat(OpenCLDeviceTopology.parsePciBusInfo(new byte[12]), is(nullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="cl_amd_device_attribute_query">
    /**
     * {@code cl_device_topology_amd} is a union: a leading {@code cl_uint type}, then
     * {@code cl_char unused[17]}, putting bus/device/function at bytes 21, 22 and 23 of 24. The type
     * is a {@code cl_uint} in host order, so it is read as one rather than as its first byte.
     */
    @Test
    public void parseAmdTopology_pcieUnion_readsBusDeviceFunctionFromTheTail() {
        byte[] topology = new byte[24];
        ByteBuffer.wrap(topology).order(ByteOrder.nativeOrder()).putInt(1); // CL_DEVICE_TOPOLOGY_TYPE_PCIE_AMD
        topology[21] = (byte) 0x0B;
        topology[22] = (byte) 0x00;
        topology[23] = (byte) 0x01;

        assertThat(OpenCLDeviceTopology.parseAmdTopology(topology), is(equalTo("amd-pcie:11:0:1")));
    }

    /** Bus numbers above 127 must not come back negative. */
    @Test
    public void parseAmdTopology_highBusNumber_isUnsigned() {
        byte[] topology = new byte[24];
        ByteBuffer.wrap(topology).order(ByteOrder.nativeOrder()).putInt(1);
        topology[21] = (byte) 0xC1;

        assertThat(OpenCLDeviceTopology.parseAmdTopology(topology), is(equalTo("amd-pcie:193:0:0")));
    }

    /** Any topology type other than PCIe carries no slot information this can use. */
    @Test
    public void parseAmdTopology_nonPcieType_isRejected() {
        byte[] topology = new byte[24];
        ByteBuffer.wrap(topology).order(ByteOrder.nativeOrder()).putInt(0);
        topology[21] = (byte) 0x0B;

        assertThat(OpenCLDeviceTopology.parseAmdTopology(topology), is(nullValue()));
    }

    @Test
    public void parseAmdTopology_wrongLength_isRejected() {
        assertThat(OpenCLDeviceTopology.parseAmdTopology(new byte[16]), is(nullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="extension selection">
    /**
     * The vendor-neutral Khronos extensions come first. Both an NVIDIA RTX 500 and an Intel Arc
     * report {@code cl_khr_device_uuid} and {@code cl_khr_pci_bus_info} (observed in a contributor's
     * device dump), so keying de-duplication on the AMD vendor extension alone would leave every
     * non-AMD machine without a fingerprint at all.
     */
    @Test
    public void supportedQuery_khronosExtensionsPresent_arePreferredOverTheVendorOne() {
        String extensions = "cl_khr_device_uuid cl_khr_pci_bus_info cl_amd_device_attribute_query";

        assertThat(OpenCLDeviceTopology.supportsDeviceUuid(extensions), is(true));
        assertThat(OpenCLDeviceTopology.supportsPciBusInfo(extensions), is(true));
        assertThat(OpenCLDeviceTopology.supportsAmdTopology(extensions), is(true));
    }

    @Test
    public void supportedQuery_noIdentityExtensions_reportsNoneSupported() {
        String extensions = "cl_khr_fp64 cl_khr_icd cl_khr_global_int32_base_atomics";

        assertThat(OpenCLDeviceTopology.supportsDeviceUuid(extensions), is(false));
        assertThat(OpenCLDeviceTopology.supportsPciBusInfo(extensions), is(false));
        assertThat(OpenCLDeviceTopology.supportsAmdTopology(extensions), is(false));
    }

    /**
     * Extension names are matched as whole tokens. {@code cl_khr_device_uuid_something} is a
     * different extension, and a substring match would claim support this device does not have.
     */
    @Test
    public void supportedQuery_extensionNameIsASubstringOfAnother_isNotMistakenForSupport() {
        assertThat(OpenCLDeviceTopology.supportsDeviceUuid("cl_khr_device_uuid_extended"), is(false));
    }
    // </editor-fold>
}
