// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.util;

import java.util.regex.Pattern;
import org.slf4j.Logger;

/**
 * Emits a multi-line block of tool-generated text as one log record per line, so it stays readable
 * under the project's CRLF-normalising log pattern.
 *
 * <h2>The problem</h2>
 * Both the bundled {@code logback.xml} and {@code examples/logbackConfiguration.xml} wrap the
 * message body in {@code %replace(%msg){'[\r\n]+', ' | '}}. That is the log-injection control: it
 * guarantees that whatever reaches a {@code LOGGER.*} argument — a line from a user-supplied address
 * file, a socket or ZMQ payload, a driver-supplied string — cannot forge a log record with a
 * fabricated timestamp, level and logger name, because every line break in the body is folded into
 * a {@code " | "} separator.
 *
 * <p>The control does its job, but it also flattens the blocks this tool deliberately formats as
 * blocks: the {@code TuneConfiguration} report, the OpenCL device dump, the transformed
 * configuration echo. A 40-line tuning report arrives as a single line with {@code " | "} between
 * every row, and users have resorted to pasting it into an editor and replacing the separators by
 * hand to read it.
 *
 * <h2>Why splitting is not a weakening of that control</h2>
 * The property the {@code %replace} defends is that <b>every physical line in the log carries a
 * prefix the appender wrote</b> — an attacker must not be able to produce a line whose prefix they
 * chose. Splitting a block on its line breaks and logging each piece separately preserves exactly
 * that: each piece becomes its own record and the appender prepends its own genuine timestamp,
 * level and logger name to it. Injected text can still occupy a line, but never a line it prefixes
 * itself, which is the same guarantee flattening provides.
 *
 * <p>Two details keep it that way, and both are load-bearing:
 *
 * <ul>
 *   <li>The split consumes <em>all</em> line-break forms ({@code \r\n}, {@code \r}, {@code \n}), so
 *       no piece can contain a further break to smuggle one past the appender. The {@code %replace}
 *       still runs on each piece and is simply a no-op.
 *   <li>Each piece is logged as an <em>argument</em> ({@code logger.info("{}", line)}), never as the
 *       format string, so a {@code "{}"} occurring in the data cannot consume a later argument.
 * </ul>
 *
 * <p>What does change is that one logical event becomes N records. That is a log-parsing trade, not
 * a security one, and it is the reason this is applied deliberately to specific tool-generated
 * blocks rather than wired in globally.
 */
public class MultilineLogger {

    /** Creates a new {@link MultilineLogger}. */
    public MultilineLogger() {}

    /**
     * Matches every line-break form, so no emitted piece can contain one. Deliberately not
     * {@code \n} alone: a lone {@code \r} is a line break to a terminal and would otherwise ride
     * along inside a piece.
     */
    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n");

    /**
     * Logs {@code block} at {@code INFO}, one record per line.
     *
     * <p>Empty lines are emitted rather than skipped: the blank lines in a report are part of its
     * layout, and dropping them would re-flow the block the caller took care to format.
     *
     * @param logger the logger to emit to, so each record carries the calling class's logger name
     * @param block  the text to emit; a block without any line break is logged as a single record
     */
    public void info(Logger logger, String block) {
        for (String line : LINE_BREAK.split(block, -1)) {
            logger.info("{}", line);
        }
    }
}
