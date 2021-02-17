// @formatter:off
/**
 * Copyright 2021 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileHelper {
    
    private final Logger logger = LoggerFactory.getLogger(FileHelper.class);
    
    public List<File> stringsToFiles(List<String> strings) {
        List<File> files = new ArrayList<File>();
        for (String string : strings) {
            files.add(new File(string));
        }
        return files;
    }

    public void assertFilesExists(List<File> files) throws IllegalArgumentException {
        logger.info("check if all files exists ...");
        for (File file : files) {
            if (!file.exists()) {
                throw new IllegalArgumentException("The file does not exists: " + file.getAbsolutePath());
            }
            logger.info("file exists: " + file.getAbsolutePath());
        }
        logger.info("... all files exists.");
    }
}
