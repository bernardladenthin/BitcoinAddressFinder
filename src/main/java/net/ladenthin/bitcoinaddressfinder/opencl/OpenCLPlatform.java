// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2022 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import org.jocl.cl_context_properties;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;

/**
 * Represents an OpenCL platform and its associated devices.
 *
 * @param platformName      the name of the OpenCL platform
 * @param contextProperties the context properties of the OpenCL platform
 * @param openCLDevices     a list of associated OpenCL devices
 */
@Immutable
public record OpenCLPlatform(
    @NonNull String platformName,
    @SuppressWarnings("Immutable")
    @NonNull cl_context_properties contextProperties,
    @NonNull ImmutableList<@NonNull OpenCLDevice> openCLDevices
) implements Serializable {

    public OpenCLPlatform(String platformName, cl_context_properties contextProperties, ImmutableList<@NonNull OpenCLDevice> openCLDevices) {
        this.platformName = platformName;
        this.contextProperties = contextProperties;
        this.openCLDevices = openCLDevices;
    }
}
