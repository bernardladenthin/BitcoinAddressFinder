// @formatter:off
/**
 * Copyright 2022 Bernard Ladenthin bernard.ladenthin@gmail.com
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
