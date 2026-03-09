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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

public class ProducerStateTest {

    // <editor-fold defaultstate="collapsed" desc="values">
    @Test
    public void values_allStatesPresent_countIsFour() {
        // arrange, act
        ProducerState[] states = ProducerState.values();

        // assert
        assertThat(states.length, is(equalTo(4)));
    }

    @Test
    public void values_ordinalOfUninitialized_isZero() {
        // arrange, act, assert
        assertThat(ProducerState.UNINITIALIZED.ordinal(), is(equalTo(0)));
    }

    @Test
    public void values_ordinalOfInitialized_isOne() {
        // arrange, act, assert
        assertThat(ProducerState.INITIALIZED.ordinal(), is(equalTo(1)));
    }

    @Test
    public void values_ordinalOfRunning_isTwo() {
        // arrange, act, assert
        assertThat(ProducerState.RUNNING.ordinal(), is(equalTo(2)));
    }

    @Test
    public void values_ordinalOfNotRunning_isThree() {
        // arrange, act, assert
        assertThat(ProducerState.NOT_RUNNING.ordinal(), is(equalTo(3)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="name">
    @Test
    public void name_uninitialized_returnsExpectedString() {
        // arrange, act, assert
        assertThat(ProducerState.UNINITIALIZED.name(), is(equalTo("UNINITIALIZED")));
    }

    @Test
    public void name_initialized_returnsExpectedString() {
        // arrange, act, assert
        assertThat(ProducerState.INITIALIZED.name(), is(equalTo("INITIALIZED")));
    }

    @Test
    public void name_running_returnsExpectedString() {
        // arrange, act, assert
        assertThat(ProducerState.RUNNING.name(), is(equalTo("RUNNING")));
    }

    @Test
    public void name_notRunning_returnsExpectedString() {
        // arrange, act, assert
        assertThat(ProducerState.NOT_RUNNING.name(), is(equalTo("NOT_RUNNING")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="valueOf">
    @Test
    public void valueOf_uninitializedString_returnsUninitializedState() {
        // arrange, act, assert
        assertThat(ProducerState.valueOf("UNINITIALIZED"), is(equalTo(ProducerState.UNINITIALIZED)));
    }

    @Test
    public void valueOf_initializedString_returnsInitializedState() {
        // arrange, act, assert
        assertThat(ProducerState.valueOf("INITIALIZED"), is(equalTo(ProducerState.INITIALIZED)));
    }

    @Test
    public void valueOf_runningString_returnsRunningState() {
        // arrange, act, assert
        assertThat(ProducerState.valueOf("RUNNING"), is(equalTo(ProducerState.RUNNING)));
    }

    @Test
    public void valueOf_notRunningString_returnsNotRunningState() {
        // arrange, act, assert
        assertThat(ProducerState.valueOf("NOT_RUNNING"), is(equalTo(ProducerState.NOT_RUNNING)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void valueOf_unknownString_throwsIllegalArgumentException() {
        // arrange, act, assert
        ProducerState.valueOf("UNKNOWN_STATE");
    }
    // </editor-fold>
}
