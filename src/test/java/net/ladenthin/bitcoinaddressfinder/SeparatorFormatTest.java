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

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Test;
import java.util.Arrays;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link SeparatorFormat}.
 */
@RunWith(DataProviderRunner.class)
public class SeparatorFormatTest {

    /**
     * Tests that the input is correctly split into two parts using a valid
     * separator between them.
     */
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void split_separatorBetweenTwoParts_returnsSplitParts(String addressSeparator) {
        // arrange
        String expectedLeft = "leftPart";
        String expectedRight = "rightPart";
        String input = expectedLeft + addressSeparator + expectedRight;

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(2)));
        assertThat(result[0], is(equalTo(expectedLeft)));
        assertThat(result[1], is(equalTo(expectedRight)));
    }

    /**
     * Tests that an input string consisting only of a separator returns two
     * empty strings after splitting.
     */
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void split_inputEqualsSeparator_returnsEmptyStrings(String separator) {
        // arrange
        String input = separator;

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(2)));
        assertThat(result[0], is(equalTo("")));
        assertThat(result[1], is(equalTo("")));
    }

    /**
     * Tests that if no separator is present in the input, the original input is
     * returned as a single element.
     */
    @Test
    public void split_noSeparatorInInput_returnsWholeInput() {
        // arrange
        String input = "justARegularString";

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(1)));
        assertThat(result[0], is(equalTo(input)));
    }

    /**
     * Tests that when multiple separators are present, the longest one is used
     * for splitting first.
     */
    @Test
    public void split_multipleSeparatorsPresent_firstMatchUsed() {
        // arrange
        String input = "a" + SeparatorFormat.DOUBLE_COLON.getSymbol() + "b" + SeparatorFormat.COLON.getSymbol() + "c";

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(2)));
        assertThat(result[0], is(equalTo("a")));
        assertThat(result[1], is(equalTo("b" + SeparatorFormat.COLON.getSymbol() + "c"))); // Only "::" should split
    }

    /**
     * Tests that passing null as input to split() throws a
     * NullPointerException.
     */
    @Test(expected = NullPointerException.class)
    public void split_nullInput_throwsNullPointerException() {
        // act
        SeparatorFormat.split(null);
    }

    /**
     * Tests that if the input starts and ends with a separator, the result
     * contains empty prefix and suffix parts.
     */
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void split_inputStartsAndEndsWithSeparator_returnsEmptyAndMiddleAndEmpty(String separator) {
        // arrange
        String input = separator + "middle" + separator;

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(3)));
        assertThat(result[0], is(equalTo("")));
        assertThat(result[1], is(equalTo("middle")));
        assertThat(result[2], is(equalTo("")));
    }

    /**
     * Verifies that the {@link SeparatorFormat.DOUBLE_COLON} separator appears
     * before the {@link SeparatorFormat.COLON} in the sorted separator list.
     */
    @Test
    public void getSortedSeparators_doubleColonBeforeColon() {
        // act
        SeparatorFormat[] sorted = SeparatorFormat.getSortedSeparators();

        // assert
        int doubleColonIndex = java.util.Arrays.asList(sorted).indexOf(SeparatorFormat.DOUBLE_COLON);
        int colonIndex = java.util.Arrays.asList(sorted).indexOf(SeparatorFormat.COLON);

        assertThat(doubleColonIndex, is(lessThan(colonIndex)));
    }

    /**
     * Tests that the input is split on the longest matching separator even when
     * multiple valid separators exist.
     */
    @Test
    public void split_inputWithMultipleValidSeparatorsButSplitOnLongestFirst() {
        // arrange
        String input = "a" + SeparatorFormat.DOUBLE_COLON.getSymbol() + "b" + SeparatorFormat.SEMICOLON.getSymbol() + "c";

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(2)));
        assertThat(result[0], is(equalTo("a")));
        assertThat(result[1], is(equalTo("b" + SeparatorFormat.SEMICOLON.getSymbol() + "c")));
    }

    /**
     * Tests that when the separator appears at the end of the input, the last
     * part is returned as an empty string.
     */
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void split_trailingSeparator_returnsEmptyLastPart(String separator) {
        // arrange
        String input = "value" + separator;

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(2)));
        assertThat(result[0], is(equalTo("value")));
        assertThat(result[1], is(equalTo("")));
    }

    /**
     * Tests that when the separator appears at the beginning of the input, the
     * first part is returned as an empty string.
     */
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void split_leadingSeparator_returnsEmptyFirstPart(String separator) {
        // arrange
        String input = separator + "value";

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(2)));
        assertThat(result[0], is(equalTo("")));
        assertThat(result[1], is(equalTo("value")));
    }

    /**
     * Verifies that every SeparatorFormat constant returns a non-null,
     * non-empty symbol.
     */
    @Test
    public void getSymbol_eachSeparator_returnsExpectedString() {
        for (SeparatorFormat separator : SeparatorFormat.values()) {
            assertThat(separator.getSymbol(), is(notNullValue()));
            assertThat(separator.getSymbol().length(), is(greaterThan(0)));
        }
    }

    @Test
    public void allSeparators_areUnique() {
        long uniqueCount = Arrays.stream(SeparatorFormat.values())
                .map(SeparatorFormat::getSymbol)
                .distinct()
                .count();
        assertThat(uniqueCount, is(equalTo((long) SeparatorFormat.values().length)));
    }

    @Test
    public void split_spaceSeparator_surroundedByText_returnsParts() {
        String input = "left" + SeparatorFormat.SPACE.getSymbol() + "right";
        String[] result = SeparatorFormat.split(input);
        assertThat(result.length, is(equalTo(2)));
    }
}
