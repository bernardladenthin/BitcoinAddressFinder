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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileHelperTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final FileHelper fileHelper = new FileHelper();

    // <editor-fold defaultstate="collapsed" desc="stringsToFiles">
    @Test
    public void stringsToFiles_emptyList_returnsEmptyList() {
        // act
        List<File> result = fileHelper.stringsToFiles(Collections.emptyList());

        // assert
        assertThat(result, is(empty()));
    }

    @Test
    public void stringsToFiles_singlePath_returnsFileWithSamePath() {
        // arrange
        String path = "/some/path/to/file.txt";

        // act
        List<File> result = fileHelper.stringsToFiles(Collections.singletonList(path));

        // assert
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getPath(), is(equalTo(path)));
    }

    @Test
    public void stringsToFiles_multiplePaths_returnsFilesInSameOrder() {
        // arrange
        List<String> paths = Arrays.asList("/first/file.txt", "/second/file.txt", "/third/file.txt");

        // act
        List<File> result = fileHelper.stringsToFiles(paths);

        // assert
        assertThat(result, hasSize(3));
        assertThat(result.get(0).getPath(), is(equalTo("/first/file.txt")));
        assertThat(result.get(1).getPath(), is(equalTo("/second/file.txt")));
        assertThat(result.get(2).getPath(), is(equalTo("/third/file.txt")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="assertFilesExists">
    @Test
    public void assertFilesExists_emptyList_noExceptionThrown() {
        // act
        fileHelper.assertFilesExists(Collections.emptyList());

        // assert
    }

    @Test
    public void assertFilesExists_existingFile_noExceptionThrown() throws IOException {
        // arrange
        File tempFile = folder.newFile("filehelper_test.tmp");

        // act
        fileHelper.assertFilesExists(Collections.singletonList(tempFile));

        // assert
    }

    @Test
    public void assertFilesExists_multipleExistingFiles_noExceptionThrown() throws IOException {
        // arrange
        File tempFile1 = folder.newFile("filehelper_test_a.tmp");
        File tempFile2 = folder.newFile("filehelper_test_b.tmp");

        // act
        fileHelper.assertFilesExists(Arrays.asList(tempFile1, tempFile2));

        // assert
    }

    @Test(expected = IllegalArgumentException.class)
    public void assertFilesExists_missingFile_throwsIllegalArgumentException() {
        // arrange
        File nonExistentFile = new File("/this/path/does/not/exist/file.txt");

        // act
        fileHelper.assertFilesExists(Collections.singletonList(nonExistentFile));
    }

    @Test
    public void assertFilesExists_missingFile_exceptionMessageContainsFilePath() {
        // arrange
        File nonExistentFile = new File("/this/path/does/not/exist/file.txt");

        // act
        try {
            fileHelper.assertFilesExists(Collections.singletonList(nonExistentFile));
            fail("Expected IllegalArgumentException was not thrown");
        } catch (IllegalArgumentException e) {
            // assert
            assertThat(e.getMessage(), containsString(nonExistentFile.toString()));
        }
    }
    // </editor-fold>
}
