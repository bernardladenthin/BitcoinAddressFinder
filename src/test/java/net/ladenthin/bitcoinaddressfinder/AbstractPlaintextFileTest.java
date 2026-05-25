// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.lmdbjava.LmdbException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.io.TempDir;

public class AbstractPlaintextFileTest {

    @TempDir
    public java.nio.file.Path folder;

    // <editor-fold defaultstate="collapsed" desc="readFile">
    @Test
    public void readFile_emptyFile_processLineNeverCalled() throws IOException {
        // arrange
        File emptyFile = java.nio.file.Files.createFile(folder.resolve("empty.txt")).toFile();
        ReadStatistic readStatistic = new ReadStatistic();
        RecordingPlaintextFile sut = new RecordingPlaintextFile(emptyFile, readStatistic);

        // act
        sut.readFile();

        // assert
        assertThat(sut.processedLines, is(empty()));
    }

    @Test
    public void readFile_singleLine_processLineCalledOnceWithCorrectContent() throws IOException {
        // arrange
        File file = java.nio.file.Files.createFile(folder.resolve("single.txt")).toFile();
        Files.writeString(file.toPath(), "hello world");
        ReadStatistic readStatistic = new ReadStatistic();
        RecordingPlaintextFile sut = new RecordingPlaintextFile(file, readStatistic);

        // act
        sut.readFile();

        // assert
        assertThat(sut.processedLines, hasSize(1));
        assertThat(sut.processedLines.get(0), is(equalTo("hello world")));
    }

    @Test
    public void readFile_multipleLines_processLineCalledForEachLine() throws IOException {
        // arrange
        File file = java.nio.file.Files.createFile(folder.resolve("multi.txt")).toFile();
        Files.writeString(file.toPath(), "line1\nline2\nline3");
        ReadStatistic readStatistic = new ReadStatistic();
        RecordingPlaintextFile sut = new RecordingPlaintextFile(file, readStatistic);

        // act
        sut.readFile();

        // assert
        assertThat(sut.processedLines, hasSize(3));
        assertThat(sut.processedLines.get(0), is(equalTo("line1")));
        assertThat(sut.processedLines.get(1), is(equalTo("line2")));
        assertThat(sut.processedLines.get(2), is(equalTo("line3")));
    }

    @Test
    public void readFile_processLineThrowsRuntimeException_errorAddedToReadStatistic() throws IOException {
        // arrange
        File file = java.nio.file.Files.createFile(folder.resolve("error.txt")).toFile();
        Files.writeString(file.toPath(), "bad line");
        ReadStatistic readStatistic = new ReadStatistic();
        ThrowingPlaintextFile sut = new ThrowingPlaintextFile(file, readStatistic, new RuntimeException("parse error"));

        // act
        sut.readFile();

        // assert
        assertThat(readStatistic.errors, hasSize(1));
        assertThat(readStatistic.errors.get(0), is(equalTo("bad line")));
    }

    @Test
    public void readFile_processLineThrowsLmdbException_exceptionPropagated() throws IOException {
        org.junit.jupiter.api.Assertions.assertThrows(LmdbException.class, () -> {
            // arrange
            File file = java.nio.file.Files.createFile(folder.resolve("lmdb.txt")).toFile();
            Files.writeString(file.toPath(), "some line");
            ReadStatistic readStatistic = new ReadStatistic();
            LmdbException mockLmdbException = mock(LmdbException.class);
            ThrowingPlaintextFile sut = new ThrowingPlaintextFile(file, readStatistic, mockLmdbException);
    
            // act
            sut.readFile();
        });
    }

    @Test
    public void readFile_multipleExceptionLines_allErrorsAddedToReadStatistic() throws IOException {
        // arrange
        File file = java.nio.file.Files.createFile(folder.resolve("multierror.txt")).toFile();
        Files.writeString(file.toPath(), "bad1\nbad2\nbad3");
        ReadStatistic readStatistic = new ReadStatistic();
        ThrowingPlaintextFile sut = new ThrowingPlaintextFile(file, readStatistic, new RuntimeException("error"));

        // act
        sut.readFile();

        // assert
        assertThat(readStatistic.errors, hasSize(3));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="interrupt">
    @Test
    public void interrupt_calledBeforeReadFile_readFileProcessesNoLines() throws IOException {
        // arrange
        File file = java.nio.file.Files.createFile(folder.resolve("lines.txt")).toFile();
        Files.writeString(file.toPath(), "line1\nline2\nline3");
        ReadStatistic readStatistic = new ReadStatistic();
        RecordingPlaintextFile sut = new RecordingPlaintextFile(file, readStatistic);

        // act
        sut.interrupt();
        sut.readFile();

        // assert
        assertThat(sut.processedLines, is(empty()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="calculateFileProgress">
    @Test
    public void readFile_singleLineFile_fileProgressIsSetToNonZero() throws IOException {
        // arrange
        File file = java.nio.file.Files.createFile(folder.resolve("progress.txt")).toFile();
        Files.writeString(file.toPath(), "content");
        ReadStatistic readStatistic = new ReadStatistic();
        RecordingPlaintextFile sut = new RecordingPlaintextFile(file, readStatistic);

        // act
        sut.readFile();

        // assert
        assertThat(readStatistic.currentFileProgress > 0.0, is(true));
    }

    @Test
    public void readFile_fileWithContent_fileProgressReachesHundredPercent() throws IOException {
        // arrange
        File file = java.nio.file.Files.createFile(folder.resolve("full.txt")).toFile();
        Files.writeString(file.toPath(), "line1\nline2");
        ReadStatistic readStatistic = new ReadStatistic();
        RecordingPlaintextFile sut = new RecordingPlaintextFile(file, readStatistic);

        // act
        sut.readFile();

        // assert
        assertThat(readStatistic.currentFileProgress, is(equalTo(100.0)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="utf8 encoding">
    @Test
    public void readFile_asciiContent_contentPreserved() throws IOException {
        // arrange
        File file = java.nio.file.Files.createFile(folder.resolve("ascii.txt")).toFile();
        Files.write(file.toPath(), "simple ascii".getBytes(StandardCharsets.ISO_8859_1));
        ReadStatistic readStatistic = new ReadStatistic();
        RecordingPlaintextFile sut = new RecordingPlaintextFile(file, readStatistic);

        // act
        sut.readFile();

        // assert
        assertThat(sut.processedLines, hasSize(1));
        assertThat(sut.processedLines.get(0), containsString("simple ascii"));
    }
    // </editor-fold>

    /**
     * Concrete implementation of {@link AbstractPlaintextFile} for testing. Records all processed lines.
     */
    private static class RecordingPlaintextFile extends AbstractPlaintextFile {

        final List<String> processedLines = new ArrayList<>();

        RecordingPlaintextFile(@NonNull File file, @NonNull ReadStatistic readStatistic) {
            super(file, readStatistic);
        }

        @Override
        protected void processLine(String line) {
            processedLines.add(line);
        }
    }

    /**
     * Concrete implementation of {@link AbstractPlaintextFile} for testing. Always throws the provided exception
     * from {@link #processLine(String)}.
     */
    private static class ThrowingPlaintextFile extends AbstractPlaintextFile {

        private final RuntimeException exceptionToThrow;

        ThrowingPlaintextFile(@NonNull File file, @NonNull ReadStatistic readStatistic, RuntimeException exceptionToThrow) {
            super(file, readStatistic);
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        protected void processLine(String line) {
            throw exceptionToThrow;
        }
    }
}
