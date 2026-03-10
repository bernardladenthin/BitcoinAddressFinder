// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Test;

/**
 * Unit tests for {@link BIP39Wordlist}.
 */
public class BIP39WordlistTest {

    // <editor-fold defaultstate="collapsed" desc="constants">
    @Test
    public void ideographicSpace_constant_hasCorrectUnicodeValue() {
        // arrange
        String expected = "\u3000";

        // act
        String actual = BIP39Wordlist.IDEOGRAPHIC_SPACE;

        // assert
        assertThat(actual, is(equalTo(expected)));
    }

    @Test
    public void normalSpace_constant_hasCorrectValue() {
        // arrange
        String expected = " ";

        // act
        String actual = BIP39Wordlist.NORMAL_SPACE;

        // assert
        assertThat(actual, is(equalTo(expected)));
    }

    @Test
    public void ideographicSpace_constant_isOneCharacter() {
        // act
        String space = BIP39Wordlist.IDEOGRAPHIC_SPACE;

        // assert
        assertThat(space.length(), is(equalTo(1)));
    }

    @Test
    public void normalSpace_constant_isOneCharacter() {
        // act
        String space = BIP39Wordlist.NORMAL_SPACE;

        // assert
        assertThat(space.length(), is(equalTo(1)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getWordListStream">
    @Test
    public void getWordListStream_englishWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.ENGLISH.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_chineseSimplifiedWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.CHINESE_SIMPLIFIED.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_chineseTraditionalWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.CHINESE_TRADITIONAL.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_czechWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.CZECH.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_frenchWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.FRENCH.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_italianWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.ITALIAN.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_japaneseWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.JAPANESE.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_koreanWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.KOREAN.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_portugueseWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.PORTUGUESE.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_russianWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.RUSSIAN.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_spanishWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.SPANISH.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_turkishWordlist_returnsNonNullStream() {
        // act
        InputStream stream = BIP39Wordlist.TURKISH.getWordListStream();

        // assert
        assertThat(stream, is(notNullValue()));
    }

    @Test
    public void getWordListStream_allWordlists_returnNonNullStream() {
        // assert
        for (BIP39Wordlist wordlist : BIP39Wordlist.values()) {
            // act
            InputStream stream = wordlist.getWordListStream();

            // assert
            assertThat("Expected non-null stream for wordlist: " + wordlist.name(), stream, is(notNullValue()));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="fromLanguageName">
    @Test
    public void fromLanguageName_lowercaseEnglish_returnsEnglishEnum() {
        // arrange
        String name = "english";

        // act
        BIP39Wordlist result = BIP39Wordlist.fromLanguageName(name);

        // assert
        assertThat(result, is(equalTo(BIP39Wordlist.ENGLISH)));
    }

    @Test
    public void fromLanguageName_lowercaseJapanese_returnsJapaneseEnum() {
        // arrange
        String name = "japanese";

        // act
        BIP39Wordlist result = BIP39Wordlist.fromLanguageName(name);

        // assert
        assertThat(result, is(equalTo(BIP39Wordlist.JAPANESE)));
    }

    @Test
    public void fromLanguageName_underscoredChineseSimplified_returnsChineseSimplifiedEnum() {
        // arrange
        String name = "chinese_simplified";

        // act
        BIP39Wordlist result = BIP39Wordlist.fromLanguageName(name);

        // assert
        assertThat(result, is(equalTo(BIP39Wordlist.CHINESE_SIMPLIFIED)));
    }

    @Test
    public void fromLanguageName_uppercaseEnglish_returnsEnglishEnum() {
        // arrange
        String name = "ENGLISH";

        // act
        BIP39Wordlist result = BIP39Wordlist.fromLanguageName(name);

        // assert
        assertThat(result, is(equalTo(BIP39Wordlist.ENGLISH)));
    }

    @Test
    public void fromLanguageName_mixedCaseFrench_returnsFrenchEnum() {
        // arrange
        String name = "French";

        // act
        BIP39Wordlist result = BIP39Wordlist.fromLanguageName(name);

        // assert
        assertThat(result, is(equalTo(BIP39Wordlist.FRENCH)));
    }

    @Test
    public void fromLanguageName_hyphenatedChineseSimplified_returnsChineseSimplifiedEnum() {
        // arrange
        // hyphens are converted to underscores by fromLanguageName
        String name = "chinese-simplified";

        // act
        BIP39Wordlist result = BIP39Wordlist.fromLanguageName(name);

        // assert
        assertThat(result, is(equalTo(BIP39Wordlist.CHINESE_SIMPLIFIED)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromLanguageName_unknownLanguage_throwsException() {
        // act
        BIP39Wordlist.fromLanguageName("klingon");
    }

    @Test
    public void fromLanguageName_allWordlistNames_roundtripSucceeds() {
        // assert - each enum name can be converted back to itself via lower-case
        for (BIP39Wordlist wordlist : BIP39Wordlist.values()) {
            // arrange
            String name = wordlist.name().toLowerCase();

            // act
            BIP39Wordlist result = BIP39Wordlist.fromLanguageName(name);

            // assert
            assertThat(result, is(equalTo(wordlist)));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getSeparator">
    @Test
    public void getSeparator_japaneseWordlist_returnsIdeographicSpace() {
        // act
        String separator = BIP39Wordlist.JAPANESE.getSeparator();

        // assert
        assertThat(separator, is(equalTo(BIP39Wordlist.IDEOGRAPHIC_SPACE)));
    }

    @Test
    public void getSeparator_englishWordlist_returnsNormalSpace() {
        // act
        String separator = BIP39Wordlist.ENGLISH.getSeparator();

        // assert
        assertThat(separator, is(equalTo(BIP39Wordlist.NORMAL_SPACE)));
    }

    @Test
    public void getSeparator_chineseSimplifiedWordlist_returnsNormalSpace() {
        // act
        String separator = BIP39Wordlist.CHINESE_SIMPLIFIED.getSeparator();

        // assert
        assertThat(separator, is(equalTo(BIP39Wordlist.NORMAL_SPACE)));
    }

    @Test
    public void getSeparator_chineseTraditionalWordlist_returnsNormalSpace() {
        // act
        String separator = BIP39Wordlist.CHINESE_TRADITIONAL.getSeparator();

        // assert
        assertThat(separator, is(equalTo(BIP39Wordlist.NORMAL_SPACE)));
    }

    @Test
    public void getSeparator_spanishWordlist_returnsNormalSpace() {
        // act
        String separator = BIP39Wordlist.SPANISH.getSeparator();

        // assert
        assertThat(separator, is(equalTo(BIP39Wordlist.NORMAL_SPACE)));
    }

    @Test
    public void getSeparator_allNonJapaneseWordlists_returnNormalSpace() {
        // assert
        for (BIP39Wordlist wordlist : BIP39Wordlist.values()) {
            if (wordlist == BIP39Wordlist.JAPANESE) {
                continue;
            }

            // act
            String separator = wordlist.getSeparator();

            // assert
            assertThat("Expected normal space for wordlist: " + wordlist.name(), separator, is(equalTo(BIP39Wordlist.NORMAL_SPACE)));
        }
    }
    // </editor-fold>
}
