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
import java.util.function.Consumer;
import net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat;
import net.ladenthin.bitcoinaddressfinder.configuration.UnknownSecretFormatException;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.DumpedPrivateKey;
import org.jspecify.annotations.NonNull;

public class SecretsFile extends AbstractPlaintextFile {

    private final CSecretFormat secretFormat;
    private final Consumer<BigInteger[]> secretConsumer;
    private final Network network;

    public SecretsFile(@NonNull Network network, @NonNull File file, @NonNull CSecretFormat secretFormat, @NonNull ReadStatistic readStatistic, @NonNull Consumer<BigInteger[]> secretConsumer) {
        super(file, readStatistic);
        this.network = network;
        this.secretFormat = secretFormat;
        this.secretConsumer = secretConsumer;
    }

    @Override
    public void processLine(String line) {
        final BigInteger secret;
        switch (secretFormat) {
            case STRING_DO_SHA256:
                byte[] sha256 = Hashing.sha256().hashString(line, StandardCharsets.UTF_8).asBytes();
                String hexOfHash = Hex.encodeHexString( sha256 );
                secret = new BigInteger(hexOfHash, 16);
                break;
            case BIG_INTEGER:
                secret = new BigInteger(line);
                break;
            case SHA256:
                secret = new BigInteger(line, 16);
                break;
            case DUMPED_RIVATE_KEY:
                DumpedPrivateKey dpk = DumpedPrivateKey.fromBase58(network, line);
                secret = dpk.getKey().getPrivKey();
                break;
            default:
                throw new UnknownSecretFormatException(secretFormat);
        }
        final BigInteger[] secrets = new BigInteger[1];
        secrets[0] = secret;
        secretConsumer.accept(secrets);
    }
}
