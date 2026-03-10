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

import org.bitcoinj.base.Network;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Test;

/**
 * Unit tests for {@link NetworkParameterFactory}.
 */
public class NetworkParameterFactoryTest {

    // <editor-fold defaultstate="collapsed" desc="getNetwork">
    @Test
    public void getNetwork_noArguments_returnsNonNull() {
        // arrange
        NetworkParameterFactory factory = new NetworkParameterFactory();

        // act
        Network network = factory.getNetwork();

        // assert
        assertThat(network, is(notNullValue()));
    }

    @Test
    public void getNetwork_calledTwice_returnsSameNetworkId() {
        // arrange
        NetworkParameterFactory factory = new NetworkParameterFactory();

        // act
        Network first = factory.getNetwork();
        Network second = factory.getNetwork();

        // assert
        assertThat(first.id(), is(equalTo(second.id())));
    }

    @Test
    public void getNetwork_differentFactoryInstances_returnSameNetworkId() {
        // arrange
        NetworkParameterFactory factory1 = new NetworkParameterFactory();
        NetworkParameterFactory factory2 = new NetworkParameterFactory();

        // act
        Network network1 = factory1.getNetwork();
        Network network2 = factory2.getNetwork();

        // assert
        assertThat(network1.id(), is(equalTo(network2.id())));
    }

    @Test
    public void getNetwork_noArguments_returnsMainNet() {
        // arrange
        NetworkParameterFactory factory = new NetworkParameterFactory();

        // act
        Network network = factory.getNetwork();

        // assert
        assertThat(network.id(), is(equalTo("org.bitcoin.production")));
    }
    // </editor-fold>
}
