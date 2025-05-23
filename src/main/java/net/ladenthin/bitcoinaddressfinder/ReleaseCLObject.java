// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder;

/**
 * Represents an OpenCL-related object that holds a native resource and must be explicitly released.
 * <p>
 * Extends {@link AutoCloseable} to support usage with Java's {@code try-with-resources} construct.
 * This interface is intended for resources such as {@link org.jocl.cl_mem}, {@link org.jocl.cl_kernel}, etc.
 * which require manual cleanup via OpenCL's native API.
 * </p>
 *
 * <p>
 * Implementations must ensure that {@link #close()} releases the underlying native resource exactly once
 * and that {@link #isClosed()} accurately reflects the release status.
 * </p>
 *
 * Example usage:
 * <pre>{@code
 * try (ReleaseCLObject buffer = MyCLBuffer.create(context, size)) {
 *     // Use buffer safely
 * }
 * // buffer is automatically released
 * }</pre>
 */
public interface ReleaseCLObject extends AutoCloseable {
    
    /**
     * Indicates whether the underlying OpenCL resource has already been released.
     *
     * @return {@code true} if {@link #close()} has been called and the resource is no longer valid,
     *         {@code false} otherwise
     */
    boolean isClosed();

    /**
     * Releases the native OpenCL resource if it has not been released yet.
     * <p>
     * This method must be idempotent, calling it multiple times should have no effect
     * after the first call.
     * </p>
     *
     * @throws RuntimeException if the release fails
     */
    @Override
    void close();
}
