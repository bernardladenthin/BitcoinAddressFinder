// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;

public class FinderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void interrupt_noExceptionThrown() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder, new AtomicBoolean(true));
        // act
        finder.interrupt();
        // assert
    }
    
    // <editor-fold defaultstate="collapsed" desc="getAllProducers">
    @Test
    public void getAllProducers_noProducersSet_returnEmptyList() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder, new AtomicBoolean(true));
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, is(empty()));
    }
    
    @Test
    public void getAllProducers_producersSet_returnList() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        cFinder.producerJava.add(new CProducerJava());
        cFinder.producerJavaSecretsFiles.add(new CProducerJavaSecretsFiles());
        cFinder.producerOpenCL.add(new CProducerOpenCL());
        Finder finder = new Finder(cFinder, new AtomicBoolean(true));
        finder.configureProducer();
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, hasSize(3));
    }
    // </editor-fold>

}
