// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import org.lwjgl.opencl.CL;
import org.lwjgl.system.Configuration;

/**
 * {@link ClLibraryInitializer} backed by LWJGL's {@link CL} bootstrap.
 *
 * <p>Initialisation order matters here. {@link CL} loads the native library from a static
 * initializer unless {@link Configuration#OPENCL_EXPLICIT_INIT} has been set beforehand, which would
 * take the choice of library name out of this class's hands and make the fallback in
 * {@link OpenClLibraryLoader} unreachable. The flag is therefore set from this class's own static
 * initializer, which runs when this class is first touched — necessarily before any method here
 * references {@link CL} and thus before {@link CL} is initialised.
 *
 * <p>Consequence worth knowing: whichever code touches {@code org.lwjgl.opencl.CL} first in a JVM
 * decides how the library gets loaded. As long as the {@code opencl} package reaches the binding
 * only through this seam, that first touch is this class.
 */
public class LwjglClLibraryInitializer implements ClLibraryInitializer {

    static {
        // Must happen before org.lwjgl.opencl.CL is class-initialized; see the class comment.
        Configuration.OPENCL_EXPLICIT_INIT.set(Boolean.TRUE);
    }

    /** Creates a new {@link LwjglClLibraryInitializer}. */
    public LwjglClLibraryInitializer() {}

    @Override
    public void create() {
        CL.create();
    }

    @Override
    public void create(String libraryName) {
        CL.create(libraryName);
    }

    @Override
    public boolean isCreated() {
        try {
            CL.getICD();
            return true;
        } catch (IllegalStateException e) {
            // LWJGL reports "OpenCL library has not been loaded." this way; there is no query API.
            return false;
        }
    }
}
