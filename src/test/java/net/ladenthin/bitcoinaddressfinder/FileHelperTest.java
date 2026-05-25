// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
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
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileHelperTest {

    @TempDir
    public java.nio.file.Path folder;

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
        assertThat(result.get(0), is(equalTo(new File(path))));
    }

    @Test
    public void stringsToFiles_multiplePaths_returnsFilesInSameOrder() {
        // arrange
        List<String> paths = Arrays.asList("/first/file.txt", "/second/file.txt", "/third/file.txt");

        // act
        List<File> result = fileHelper.stringsToFiles(paths);

        // assert
        assertThat(result, hasSize(3));
        assertThat(result.get(0), is(equalTo(new File(paths.get(0)))));
        assertThat(result.get(1), is(equalTo(new File(paths.get(1)))));
        assertThat(result.get(2), is(equalTo(new File(paths.get(2)))));
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
        File tempFile = java.nio.file.Files.createFile(folder.resolve("filehelper_test.tmp")).toFile();

        // act
        fileHelper.assertFilesExists(Collections.singletonList(tempFile));

        // assert
    }

    @Test
    public void assertFilesExists_multipleExistingFiles_noExceptionThrown() throws IOException {
        // arrange
        File tempFile1 = java.nio.file.Files.createFile(folder.resolve("filehelper_test_a.tmp")).toFile();
        File tempFile2 = java.nio.file.Files.createFile(folder.resolve("filehelper_test_b.tmp")).toFile();

        // act
        fileHelper.assertFilesExists(Arrays.asList(tempFile1, tempFile2));

        // assert
    }

    @Test
    public void assertFilesExists_missingFile_throwsIllegalArgumentException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // arrange
            File nonExistentFile = new File("/this/path/does/not/exist/file.txt");
    
            // act
            fileHelper.assertFilesExists(Collections.singletonList(nonExistentFile));
        });
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
