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
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.SecretsFile} when a
 * {@link CSecretFormat} value is encountered that is not handled by the
 * implementation. This indicates a programming error: a new enum constant was
 * added to {@link CSecretFormat} but the corresponding handling branch was not
 * implemented.
 */
public class UnknownSecretFormatException extends IllegalArgumentException {

    private final CSecretFormat secretFormat;

    public UnknownSecretFormatException(CSecretFormat secretFormat) {
        super("Unknown secret format: " + secretFormat);
        this.secretFormat = secretFormat;
    }

    public CSecretFormat getSecretFormat() {
        return secretFormat;
    }
}
