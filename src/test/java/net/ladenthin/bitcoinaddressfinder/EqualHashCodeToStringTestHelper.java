// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import org.junit.jupiter.api.Assertions;
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
        Assertions.assertNotSame(instanceA, instanceADifferentReference);
        Assertions.assertNotSame(instanceA, instanceB);
        Assertions.assertNotSame(instanceA, instanceBDifferentReference);

        Assertions.assertNotSame(instanceB, instanceBDifferentReference);
        Assertions.assertNotSame(instanceB, instanceA);
        Assertions.assertNotSame(instanceB, instanceADifferentReference);
    }
}
