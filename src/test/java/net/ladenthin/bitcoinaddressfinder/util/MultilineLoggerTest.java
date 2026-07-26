// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

public class MultilineLoggerTest {

    private final MultilineLogger multilineLogger = new MultilineLogger();

    /**
     * Captures every line the logger was handed as the {@code {}} argument.
     *
     * @param logger the mock the block was logged to
     * @return the emitted lines, in order
     */
    private static List<String> emittedLines(Logger logger) {
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeastOnce()).info(eq("{}"), lines.capture());
        return lines.getAllValues();
    }

    // <editor-fold defaultstate="collapsed" desc="info">
    @Test
    public void info_multipleLines_emitsOneRecordPerLine() {
        Logger logger = mock(Logger.class);

        multilineLogger.info(logger, "first\nsecond\nthird");

        assertThat(emittedLines(logger), contains("first", "second", "third"));
    }

    /**
     * Blank lines are layout, not noise: a report separates its sections with them, and dropping
     * them would re-flow the block the caller formatted.
     */
    @Test
    public void info_blankLines_areKept() {
        Logger logger = mock(Logger.class);

        multilineLogger.info(logger, "header\n\nbody");

        assertThat(emittedLines(logger), contains("header", "", "body"));
    }

    @Test
    public void info_singleLine_emitsOneRecord() {
        Logger logger = mock(Logger.class);

        multilineLogger.info(logger, "just one");

        assertThat(emittedLines(logger), contains("just one"));
    }

    /**
     * The whole point of the split is that no emitted line can carry a further break past the
     * appender — a line that still contained one could forge a record with a prefix of its own
     * choosing, which is exactly what the log pattern's CRLF guard exists to prevent. All three
     * break forms must therefore be consumed, not just {@code \n}.
     */
    @Test
    public void info_everyLineBreakForm_isConsumedSoNoEmittedLineContainsOne() {
        Logger logger = mock(Logger.class);

        multilineLogger.info(logger, "unix\nwindows\r\nmac\rend");

        List<String> emitted = emittedLines(logger);
        assertThat(emitted, contains("unix", "windows", "mac", "end"));
        boolean anyLineStillHasABreak =
                emitted.stream().anyMatch(line -> line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0);
        assertThat(anyLineStillHasABreak, is(equalTo(false)));
    }

    /**
     * Lines are passed as an argument rather than as the format string, so a {@code "{}"} in the
     * data is rendered literally instead of consuming a later argument.
     */
    @Test
    public void info_lineContainingPlaceholder_isPassedAsArgumentNotFormat() {
        Logger logger = mock(Logger.class);

        multilineLogger.info(logger, "value is {} here");

        verify(logger).info("{}", "value is {} here");
    }

    /**
     * A trailing break yields a trailing empty line rather than being swallowed, which is what
     * {@code split(…, -1)} buys; the block's own trailing blank line is part of its layout.
     */
    @Test
    public void info_trailingLineBreak_yieldsTrailingEmptyLine() {
        Logger logger = mock(Logger.class);

        multilineLogger.info(logger, "line\n");

        assertThat(emittedLines(logger), contains("line", ""));
    }

    @Test
    public void info_emptyBlock_emitsOneEmptyRecord() {
        Logger logger = mock(Logger.class);

        multilineLogger.info(logger, "");

        assertThat(emittedLines(logger), contains(""));
    }
    // </editor-fold>

}
