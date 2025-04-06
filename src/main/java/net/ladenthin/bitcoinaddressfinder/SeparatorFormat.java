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

import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

public enum SeparatorFormat {
    // <editor-fold desc="Common CSV-style separators">

    /**
     * Standard CSV separator (comma).
     */
    COMMA(","),
    /**
     * Common in European CSV formats where comma is used as a decimal
     * separator.
     */
    SEMICOLON(";"),
    /**
     * CSV variant with added space for human readability (e.g., "addr,
     * amount").
     */
    COMMA_SPACE(", "),
    // </editor-fold>

    // <editor-fold desc="Structural / programming-style separators">

    /**
     * Common in CLI tools, configs, or simple key-value pairs (e.g.,
     * "addr:amount").
     */
    COLON(":"),
    /**
     * Extended separator occasionally used in custom log or structured data
     * formats (e.g., "addr::amount").
     */
    DOUBLE_COLON("::"),
    /**
     * Key-value style separator, typically found in config or scripting files.
     */
    EQUALS("="),
    /**
     * Visual separator often used in logs or mapping formats (e.g.,
     * "addr->amount").
     */
    ARROW("->"),
    /**
     * Functional programming style or log format separator (e.g.,
     * "addr=>amount").
     */
    DOUBLE_ARROW("=>"),
    /**
     * Sometimes used in URI-like or REST-style address formats (e.g.,
     * "addr/amount").
     */
    SLASH("/"),
    // </editor-fold>

    // <editor-fold desc="Log/console-style separators">

    /**
     * Common in logs or console output for clear vertical splitting (e.g.,
     * "addr|amount").
     */
    PIPE("|"),
    /**
     * Rare scripting-style separator or used in lightweight exports.
     */
    CARET("^"),
    /**
     * Rarely used but useful to avoid conflicts with other symbols.
     */
    TILDE("~"),
    /**
     * Seen in logs or non-standard formats, often for inline comments or
     * metadata (e.g., "addr#amount").
     */
    HASH("#"),
    // </editor-fold>

    // <editor-fold desc="Whitespace-based separators">

    /**
     * Tab character separator, typically used in TSV (Tab-Separated Values)
     * formats.
     */
    TAB_SPLIT("\t"),
    /**
     * Simple space separator, used in CLI tools or minimalist export formats.
     */
    SPACE(" ");

    // </editor-fold>
    private final String symbol;

    SeparatorFormat(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Returns the list of separator formats sorted by descending symbol length.
     * <p>
     * This ensures that longer separators like "::" are evaluated before
     * shorter ones like ":" when used in splitting or pattern matching. This
     * prevents premature matches on partial separators that are substrings of
     * longer ones.
     *
     * @return an array of SeparatorFormat values sorted by descending length of
     * their symbols
     */
    public static SeparatorFormat[] getSortedSeparators() {
        return Arrays.stream(SeparatorFormat.values())
                .sorted(Comparator.comparingInt(
                        (SeparatorFormat s) -> s.getSymbol().length()
                ).reversed())
                .toArray(SeparatorFormat[]::new);
    }

    /**
     * Splits the given input string using the first matching
     * {@link SeparatorFormat} found, based on descending separator length
     * priority.
     * <p>
     * This method ensures that longer separators (e.g.,
     * {@link SeparatorFormat#DOUBLE_COLON}) are considered before shorter ones
     * (e.g., {@link SeparatorFormat#COLON}) to prevent premature splitting on
     * partial matches.
     * <p>
     * If no matching separator is found in the input, the method returns the
     * input as a single-element array.
     * <p>
     * Trailing and leading empty strings are preserved (e.g., {@code "|value|"}
     * results in {@code ["", "value", ""]}).
     *
     * @param input the string to split; must not be {@code null}
     * @return an array of string parts resulting from the split
     * @throws NullPointerException if the input is {@code null}
     */
    public static String[] split(String input) {
        for (SeparatorFormat separator : SeparatorFormat.getSortedSeparators()) {
            String[] parts = input.split(Pattern.quote(separator.getSymbol()), -1);
            if (parts.length > 1) {
                return parts;
            }
        }
        return new String[]{input};
    }
}
