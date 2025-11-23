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
import java.io.IOException;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
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
     * Tests that the input is recursively split on all matching separators,
     * starting with the longest, respecting their defined priority.
     */
    @Test
    public void split_inputWithMultipleValidSeparators_recursivelySplitsAll() {
        // arrange
        String input = "a" + SeparatorFormat.DOUBLE_COLON.getSymbol() + "b" + SeparatorFormat.SEMICOLON.getSymbol() + "c";

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(3)));
        assertThat(result[0], is(equalTo("a")));
        assertThat(result[1], is(equalTo("b")));
        assertThat(result[2], is(equalTo("c")));
    }

    /**
     * Tests that when multiple separators are present, the input is recursively
     * split by all applicable separators, starting with the longest.
     */
    @Test
    public void split_multipleSeparatorsPresent_recursivelySplitsAll() {
        // arrange
        String input = "a" + SeparatorFormat.DOUBLE_COLON.getSymbol() + "b" + SeparatorFormat.COLON.getSymbol() + "c";

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(3)));
        assertThat(result[0], is(equalTo("a")));
        assertThat(result[1], is(equalTo("b")));
        assertThat(result[2], is(equalTo("c")));
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
     * Verifies that the {@link SeparatorFormat#DOUBLE_COLON} separator appears
     * before the {@link SeparatorFormat#COLON} in the sorted separator list.
     */
    @Test
    public void getSortedSeparators_doubleColonBeforeColon() {
        // act
        List<SeparatorFormat> sorted = SeparatorFormat.getSortedSeparators();

        // assert
        int doubleColonIndex = sorted.indexOf(SeparatorFormat.DOUBLE_COLON);
        int colonIndex = sorted.indexOf(SeparatorFormat.COLON);

        assertThat(doubleColonIndex, is(lessThan(colonIndex)));
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
    
    @Test
    public void split_recursiveSplitting_allRelevantSeparatorsAreResolvedInOrder() {
        // arrange
        String input = "first" 
            + SeparatorFormat.DOUBLE_COLON.getSymbol() 
            + "second" 
            + SeparatorFormat.COLON.getSymbol() 
            + "third" 
            + SeparatorFormat.PIPE.getSymbol() 
            + "fourth";

        // Expected splitting order:
        // 1. DOUBLE_COLON splits "first" and "second:third|fourth"
        // 2. COLON splits "second" and "third|fourth"
        // 3. PIPE splits "third" and "fourth"
        //
        // Expected output: ["first", "second", "third", "fourth"]

        // act
        String[] result = SeparatorFormat.split(input);

        // assert
        assertThat(result.length, is(equalTo(4)));
        assertThat(result[0], is(equalTo("first")));
        assertThat(result[1], is(equalTo("second")));
        assertThat(result[2], is(equalTo("third")));
        assertThat(result[3], is(equalTo("fourth")));
    }
}
