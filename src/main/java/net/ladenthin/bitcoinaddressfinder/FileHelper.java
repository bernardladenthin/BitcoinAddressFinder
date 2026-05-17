// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileHelper {
    
    private final Logger logger = LoggerFactory.getLogger(FileHelper.class);
    
    public List<File> stringsToFiles(List<String> strings) {
        List<File> files = new ArrayList<>();
        for (String string : strings) {
            files.add(new File(string));
        }
        return files;
    }

    public void assertFilesExists(List<File> files) throws IllegalArgumentException {
        logger.info("Validating that all input files exist...");
        for (File file : files) {
            if (!file.exists()) {
                throw new IllegalArgumentException("Missing file: " + file);
            }
            logger.debug("Found file: {}", file);
        }
        logger.info("All input files verified successfully.");
    }
}
