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
package net.ladenthin.bitcoinaddressfinder;

/**
 * Exception thrown when no more secrets are available.
 * 
 * This class extends RuntimeException because it is used with methods such as
 * {@link java.util.Random#nextBytes(byte[])} and {@link java.math.BigInteger#BigInteger(int, Random)},
 * which require a {@code Random} instance that only throws unchecked exceptions.
 */
public class NoMoreSecretsAvailableException extends RuntimeException {
    
    public NoMoreSecretsAvailableException() {
        super();
    }

    public NoMoreSecretsAvailableException(String message) {
        super(message);
    }

    public NoMoreSecretsAvailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoMoreSecretsAvailableException(Throwable cause) {
        super(cause);
    }
}
