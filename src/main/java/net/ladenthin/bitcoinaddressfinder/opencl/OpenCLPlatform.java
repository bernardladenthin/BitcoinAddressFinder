// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClPlatformId;
import org.jspecify.annotations.NonNull;

/**
 * Represents an OpenCL platform and its associated devices.
 *
 * <p>Carries the platform handle rather than a prepared context-property list. The properties are a
 * throwaway argument of {@code clCreateContext} — building them at the point of use keeps this a
 * plain description of the machine, and it removes a binding-specific structure from a type that the
 * layers above pass around.
 *
 * @param platformId    the platform handle
 * @param platformName  the name of the OpenCL platform
 * @param openCLDevices a list of associated OpenCL devices
 */
@Immutable
public record OpenCLPlatform(
        @NonNull ClPlatformId platformId,
        @NonNull String platformName,
        @NonNull ImmutableList<@NonNull OpenCLDevice> openCLDevices)
        implements Serializable {}
