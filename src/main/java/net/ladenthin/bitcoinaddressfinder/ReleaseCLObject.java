// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
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
