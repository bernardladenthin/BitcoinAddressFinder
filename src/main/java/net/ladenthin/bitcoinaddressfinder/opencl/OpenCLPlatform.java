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

import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jocl.cl_platform_id;
import org.jspecify.annotations.NonNull;

@Immutable
@ToString
@EqualsAndHashCode
public class OpenCLPlatform implements Serializable  {
    @NonNull
    private final transient cl_platform_id id;
    
    /**
     * See {@link org.jocl.CL#CL_PLATFORM_NAME}.
     */
    @NonNull
    private final String platformName;
    
    @NonNull
    private final List<OpenCLDevice> openCLDevices;
    
    public OpenCLPlatform(cl_platform_id id, String platformName, List<OpenCLDevice> openCLDevices) {
        this.id = id;
        this.platformName = platformName;
        this.openCLDevices = Collections.unmodifiableList(openCLDevices);
    }

    public cl_platform_id getId() {
        return id;
    }

    public String getPlatformName() {
        return platformName;
    }

    public List<OpenCLDevice> getOpenCLDevices() {
        return openCLDevices;
    }
    
}
