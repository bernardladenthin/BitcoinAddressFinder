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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

public class ReadStatisticTest {

    // <editor-fold defaultstate="collapsed" desc="defaultValues">
    @Test
    public void readStatistic_newInstance_successfulIsZero() {
        // arrange, act
        ReadStatistic readStatistic = new ReadStatistic();

        // assert
        assertThat(readStatistic.successful, is(equalTo(0L)));
    }

    @Test
    public void readStatistic_newInstance_unsupportedIsZero() {
        // arrange, act
        ReadStatistic readStatistic = new ReadStatistic();

        // assert
        assertThat(readStatistic.unsupported, is(equalTo(0L)));
    }

    @Test
    public void readStatistic_newInstance_currentFileProgressIsZero() {
        // arrange, act
        ReadStatistic readStatistic = new ReadStatistic();

        // assert
        assertThat(readStatistic.currentFileProgress, is(equalTo(0.0)));
    }

    @Test
    public void readStatistic_newInstance_errorsIsEmpty() {
        // arrange, act
        ReadStatistic readStatistic = new ReadStatistic();

        // assert
        assertThat(readStatistic.errors, is(empty()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="mutation">
    @Test
    public void readStatistic_incrementSuccessful_reflectsNewValue() {
        // arrange
        ReadStatistic readStatistic = new ReadStatistic();

        // act
        readStatistic.successful = 42L;

        // assert
        assertThat(readStatistic.successful, is(equalTo(42L)));
    }

    @Test
    public void readStatistic_incrementUnsupported_reflectsNewValue() {
        // arrange
        ReadStatistic readStatistic = new ReadStatistic();

        // act
        readStatistic.unsupported = 7L;

        // assert
        assertThat(readStatistic.unsupported, is(equalTo(7L)));
    }

    @Test
    public void readStatistic_setCurrentFileProgress_reflectsNewValue() {
        // arrange
        ReadStatistic readStatistic = new ReadStatistic();

        // act
        readStatistic.currentFileProgress = 55.5;

        // assert
        assertThat(readStatistic.currentFileProgress, is(equalTo(55.5)));
    }

    @Test
    public void readStatistic_addError_errorsPresentInList() {
        // arrange
        ReadStatistic readStatistic = new ReadStatistic();

        // act
        readStatistic.errors.add("parse error at line 3");

        // assert
        assertThat(readStatistic.errors, hasSize(1));
        assertThat(readStatistic.errors.get(0), is(equalTo("parse error at line 3")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toString">
    @Test
    @ToStringTest
    public void toString_defaultInstance_containsAllFieldNames() {
        // arrange
        ReadStatistic readStatistic = new ReadStatistic();

        // act
        String result = readStatistic.toString();

        // assert
        assertThat(result, containsString("successful="));
        assertThat(result, containsString("unsupported="));
        assertThat(result, containsString("currentFileProgress="));
        assertThat(result, containsString("errors="));
    }

    @Test
    @ToStringTest
    public void toString_withModifiedValues_containsUpdatedValues() {
        // arrange
        ReadStatistic readStatistic = new ReadStatistic();
        readStatistic.successful = 10L;
        readStatistic.unsupported = 3L;
        readStatistic.currentFileProgress = 75.0;
        readStatistic.errors.add("some error");

        // act
        String result = readStatistic.toString();

        // assert
        assertThat(result, containsString("successful=10"));
        assertThat(result, containsString("unsupported=3"));
        assertThat(result, containsString("currentFileProgress=75.0"));
        assertThat(result, containsString("some error"));
    }
    // </editor-fold>
}
