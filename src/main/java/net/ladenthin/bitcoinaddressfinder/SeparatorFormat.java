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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
     * @return a list of SeparatorFormat values sorted by descending length of
     *         their symbols
     */
    public static List<SeparatorFormat> getSortedSeparators() {
        return Arrays.stream(SeparatorFormat.values())
                .sorted(Comparator.comparingInt(
                        (SeparatorFormat s) -> s.getSymbol().length()
                ).reversed())
                .toList();
    }
    
    /**
     * Recursively splits the given input string using all defined {@link SeparatorFormat} values,
     * in descending order of separator length.
     * <p>
     * Unlike the original {@link #split(String)} implementation which applies only the first matching
     * separator, this version performs a deep, recursive traversal, ensuring that all relevant separators
     * (e.g., {@link SeparatorFormat#DOUBLE_COLON}, {@link SeparatorFormat#COLON}, {@link SeparatorFormat#PIPE})
     * are applied in sequence. This guarantees complete and hierarchical resolution of mixed separator patterns.
     * <p>
     * This method is especially useful when inputs may include multiple nested or combined separators,
     * and all of them must be processed in order of priority.
     * <p>
     * Trailing and leading empty strings are preserved (e.g., {@code "|value|"} results in {@code ["", "value", ""]}).
     *
     * @param input the string to split; must not be {@code null}
     * @return an array of string parts resulting from full recursive splitting
     * @throws NullPointerException if the input is {@code null}
     */
    public static String[] split(String input) {
        List<String> result = new ArrayList<>();
        splitRecursive(input, result, getSortedSeparators(), 0);
        return result.toArray(new String[0]);
    }

    /**
    * Helper method for recursively applying separators to the input.
    * <p>
    * Each separator is applied in turn, starting with the longest one. If the current
    * separator splits the input, each resulting part is passed recursively to the next separator.
    * If no split occurs at the current level, the method proceeds to the next separator.
    *
    * @param input       the string to split
    * @param result      the list collecting all final split segments
    * @param separators  the list of separators to apply
    * @param index       the current separator index in the list
    */
   private static void splitRecursive(String input, List<String> result, List<SeparatorFormat> separators, int index) {
       if (index >= separators.size()) {
           result.add(input); // No more separators, store the remaining part
           return;
       }

       SeparatorFormat separator = separators.get(index);
       String[] parts = input.split(Pattern.quote(separator.getSymbol()), -1);

       if (parts.length > 1) {
           for (String part : parts) {
               splitRecursive(part, result, separators, index + 1);
           }
       } else {
           splitRecursive(input, result, separators, index + 1);
       }
   }

}
