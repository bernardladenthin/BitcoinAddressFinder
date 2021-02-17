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

import com.google.common.hash.Hashing;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.apache.commons.codec.binary.Hex;

public class BrainwalletFile extends AbstractPlaintextFile {

    private final Consumer<BigInteger> secretConsumer;

    public BrainwalletFile(@Nonnull File file, ReadStatistic readStatistic, @Nonnull Consumer<BigInteger> secretConsumer, AtomicBoolean shouldRun) {
        super(file, readStatistic, shouldRun);
        this.secretConsumer = secretConsumer;
    }

    @Override
    public void processLine(String line) {
        byte[] sha256 = Hashing.sha256().hashString(line, StandardCharsets.UTF_8).asBytes();
        String hexOfHash = Hex.encodeHexString( sha256 );
        BigInteger bigInteger = new BigInteger(hexOfHash, 16);
        secretConsumer.accept(bigInteger);
    }
}
