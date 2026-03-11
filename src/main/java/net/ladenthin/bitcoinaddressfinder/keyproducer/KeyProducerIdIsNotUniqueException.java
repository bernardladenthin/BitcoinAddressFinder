// @formatter:off
/**
 * Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder.keyproducer;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.Finder} during
 * producer initialisation when two or more producers share the same
 * {@code keyProducerId}. Each producer must be identified by a unique string
 * so that hits can be attributed to the correct producer.
 */
public class KeyProducerIdIsNotUniqueException extends RuntimeException {

    private final String id;

    public KeyProducerIdIsNotUniqueException(String id) {
        super("Key producer id must be unique: " + id);
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
