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

import java.io.InputStream;

public enum BIP39Wordlist {
    
    CHINESE_SIMPLIFIED("chinese_simplified.txt"),
    CHINESE_TRADITIONAL("chinese_traditional.txt"),
    CZECH("czech.txt"),
    ENGLISH("english.txt"),
    FRENCH("french.txt"),
    ITALIAN("italian.txt"),
    JAPANESE("japanese.txt"),
    KOREAN("korean.txt"),
    PORTUGUESE("portuguese.txt"),
    RUSSIAN("russian.txt"),
    SPANISH("spanish.txt"),
    TURKISH("turkish.txt");
    
    /**
     * Unicode character for an ideographic space (U+3000), used in East Asian languages such as Japanese.
     * This space is wider than a standard space and is required for correct formatting of Japanese mnemonics.
     */
    public static final String IDEOGRAPHIC_SPACE = "\u3000";

    /**
     * Standard ASCII space character (U+0020).
     * Used as the default separator between words in most BIP39 wordlists, such as English.
     */
    public static final String NORMAL_SPACE = " ";
    
    /**
    * The name of the wordlist file associated with this BIP39 language.
    * <p>
    * This filename is used to locate the corresponding wordlist resource file in the
    * {@code /mnemonic/wordlist/} directory of the classpath (e.g., {@code english.txt}).
    */
    private final String fileName;
    
    /**
     * Constructs a BIP39 wordlist enum constant with the associated filename.
     *
     * @param fileName the name of the wordlist file (e.g., {@code "english.txt"}), which is used
     *                 to load the corresponding wordlist resource from the classpath
     */
    BIP39Wordlist(String fileName) {
        this.fileName = fileName;
    }

    /**
    * Loads the BIP39 wordlist file as an {@link InputStream} for this language.
    * <p>
    * The wordlist file is expected to be located in the resource path:
    * {@code /mnemonic/wordlist/{fileName}}, where {@code fileName} corresponds
    * to the file name associated with the enum constant (e.g. {@code english.txt}).
    * <p>
    * This method is used to initialize a {@link MnemonicCode} instance with the correct
    * wordlist for a given language.
    *
    * @return the input stream of the wordlist file for this language, or {@code null}
    *         if the resource is not found.
    */
    public InputStream getWordListStream() {
        return BIP39Wordlist.class.getResourceAsStream("/mnemonic/wordlist/" + fileName);
    }
    
    /**
     * Converts a filename-like language name into the corresponding {@link BIP39Wordlist} enum constant.
     * <p>
     * The input typically comes from wordlist filenames in the format {@code mnemonic/wordlist/{language}.txt},
     * for example:
     * <ul>
     *   <li>{@code chinese_simplified.txt} → {@code CHINESE_SIMPLIFIED}</li>
     *   <li>{@code english.txt} → {@code ENGLISH}</li>
     * </ul>
     * <p>
     * To perform the conversion, this method:
     * <ul>
     *   <li>Converts the name to upper case</li>
     *   <li>Replaces hyphens with underscores (if any)</li>
     * </ul>
     * This enables consistent mapping between file-based wordlist identifiers and enum values.
     *
     * @param name the lowercase filename-based language identifier, e.g. {@code "english"} or {@code "chinese_simplified"}
     * @return the corresponding {@link BIP39Wordlist} enum constant
     * @throws IllegalArgumentException if no matching enum exists
     */
    public static BIP39Wordlist fromLanguageName(String name) {
        return valueOf(name.toUpperCase().replace('-', '_'));
    }
    
    /**
    * Returns the word separator used in the mnemonic phrase for the given language.
    * <p>
    * Most languages use a single space (" ") as the separator between words.
    * However, Japanese uses the IDEOGRAPHIC SPACE (U+3000) to conform with the official BIP39 specification.
    *
    * @return the word separator specific to the language
    */
    public String getSeparator() {
        if (this == JAPANESE) {
            return IDEOGRAPHIC_SPACE;
        }
        return NORMAL_SPACE;
    }
}
