// @formatter:off
/**
 * Copyright 2021 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import org.junit.Assert;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class EqualHashCodeToStringTestHelper {
    
    private final Object instanceA;
    private final Object instanceADifferentReference;
    
    private final Object instanceB;
    private final Object instanceBDifferentReference;

    public EqualHashCodeToStringTestHelper(Object instanceA, Object instanceADifferentReference, Object instanceB, Object instanceBDifferentReference) {
        this.instanceA = instanceA;
        this.instanceADifferentReference = instanceADifferentReference;
        this.instanceB = instanceB;
        this.instanceBDifferentReference = instanceBDifferentReference;
    }
    
    public void assertEqualsHashCodeToStringAIsDifferentToB() {
        assertDifferenceReference();
        
        // equals
        assertThat(instanceA, is(equalTo(instanceADifferentReference)));
        assertThat(instanceB, is(equalTo(instanceBDifferentReference)));
        assertThat(instanceA, is(not(equalTo(instanceB))));
        assertThat(instanceA, is(not(equalTo(instanceBDifferentReference))));
        assertThat(instanceADifferentReference, is(not(equalTo(instanceBDifferentReference))));
        
        // hashCode
        assertThat(instanceA.hashCode(), is(equalTo(instanceADifferentReference.hashCode())));
        assertThat(instanceB.hashCode(), is(equalTo(instanceBDifferentReference.hashCode())));
        assertThat(instanceA.hashCode(), is(not(equalTo(instanceB.hashCode()))));
        assertThat(instanceA.hashCode(), is(not(equalTo(instanceBDifferentReference.hashCode()))));
        assertThat(instanceADifferentReference.hashCode(), is(not(equalTo(instanceBDifferentReference.hashCode()))));
        
        // toString
        assertThat(instanceA.toString(), is(equalTo(instanceADifferentReference.toString())));
        assertThat(instanceB.toString(), is(equalTo(instanceBDifferentReference.toString())));
        assertThat(instanceA.toString(), is(not(equalTo(instanceB.toString()))));
        assertThat(instanceA.toString(), is(not(equalTo(instanceBDifferentReference.toString()))));
        assertThat(instanceADifferentReference.toString(), is(not(equalTo(instanceBDifferentReference.toString()))));
    }
    
    public void assertEqualsHashCodeToStringAIsEqualToB() {
        assertDifferenceReference();
        
        // equals
        assertThat(instanceA, is(equalTo(instanceADifferentReference)));
        assertThat(instanceB, is(equalTo(instanceBDifferentReference)));
        assertThat(instanceA, is(equalTo(instanceB)));
        
        // hashCode
        assertThat(instanceA.hashCode(), is(equalTo(instanceADifferentReference.hashCode())));
        assertThat(instanceB.hashCode(), is(equalTo(instanceBDifferentReference.hashCode())));
        assertThat(instanceA.hashCode(), is(equalTo(instanceB.hashCode())));
        
        // toString
        assertThat(instanceA.toString(), is(equalTo(instanceADifferentReference.toString())));
        assertThat(instanceB.toString(), is(equalTo(instanceBDifferentReference.toString())));
        assertThat(instanceA.toString(), is(equalTo(instanceB.toString())));
    }

    private void assertDifferenceReference() {
        Assert.assertNotSame(instanceA, instanceADifferentReference);
        Assert.assertNotSame(instanceA, instanceB);
        Assert.assertNotSame(instanceA, instanceBDifferentReference);

        Assert.assertNotSame(instanceB, instanceBDifferentReference);
        Assert.assertNotSame(instanceB, instanceA);
        Assert.assertNotSame(instanceB, instanceADifferentReference);
    }
}
