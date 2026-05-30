// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helpers for converting between path strings and {@link File} objects and validating their existence.
 */
public class FileHelper {

    /** Creates a new {@link FileHelper}. */
    public FileHelper() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(FileHelper.class);

    /**
     * Converts a list of path strings to {@link File} objects.
     *
     * @param strings the list of path strings
     * @return the corresponding list of {@link File} instances (same order)
     */
    public List<File> stringsToFiles(Collection<String> strings) {
        List<File> files = new ArrayList<>(strings.size());
        for (String string : strings) {
            files.add(new File(string));
        }
        return files;
    }

    /**
     * Asserts that every file in {@code files} exists on disk.
     *
     * @param files the files to verify
     * @throws IllegalArgumentException if any file is missing
     */
    public void assertFilesExists(List<File> files) {
        LOGGER.info("Validating that all input files exist...");
        for (File file : files) {
            if (!file.exists()) {
                throw new IllegalArgumentException("Missing file: " + file);
            }
            LOGGER.debug("Found file: {}", file);
        }
        LOGGER.info("All input files verified successfully.");
    }
}
