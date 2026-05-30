// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that reads secret values from one or more files and forwards them through the consumer.
 */
public class ProducerJavaSecretsFiles extends ProducerJava {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerJavaSecretsFiles.class);

    private final Network network = new NetworkParameterFactory().getNetwork();

    private final CProducerJavaSecretsFiles producerJavaSecretsFiles;

    private final ReadStatistic readStatistic = new ReadStatistic();

    @NonNull
    AtomicReference<SecretsFile> currentSecretsFile = new AtomicReference<>();

    /**
     * Creates a new secrets-file producer.
     *
     * @param producerJavaSecretsFiles the producer configuration
     * @param consumer                 the downstream consumer
     * @param keyUtility               cryptographic helper
     * @param keyProducer              the secret supplying strategy (unused in this producer)
     * @param bitHelper                bit/batch-size helper
     */
    public ProducerJavaSecretsFiles(
            CProducerJavaSecretsFiles producerJavaSecretsFiles,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper) {
        super(producerJavaSecretsFiles, consumer, keyUtility, keyProducer, bitHelper);
        this.producerJavaSecretsFiles = producerJavaSecretsFiles;
    }

    @Override
    public void produceKeys() throws IOException {
        FileHelper fileHelper = new FileHelper();
        List<File> files = fileHelper.stringsToFiles(producerJavaSecretsFiles.files);
        fileHelper.assertFilesExists(files);

        LOGGER.info("Starting secrets file processing...");
        for (File file : files) {
            if (!shouldRun.get()) {
                LOGGER.info("Key production stopped by flag.");
                break;
            }
            SecretsFile secretsFile = new SecretsFile(
                    network, file, producerJavaSecretsFiles.secretFormat, readStatistic, this::consumeSecrets);

            LOGGER.info("Processing secrets file: {}", file);
            currentSecretsFile.set(secretsFile);
            secretsFile.readFile();
            currentSecretsFile.set(null);
            LOGGER.info("Finished processing: {}", file);

            logProgress();
        }
        LOGGER.info("All secrets files processed.");
    }

    @Override
    public void processSecrets(BigInteger[] secrets) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private void logProgress() {
        LOGGER.info("Progress: Unsupported: " + readStatistic.getUnsupportedTotal() + ". Errors: "
                + readStatistic.errors.size() + ". Current File progress: "
                + String.format("%.2f", readStatistic.currentFileProgress) + "%.");
    }

    @Override
    public void interrupt() {
        super.interrupt();
        SecretsFile secretsFile = currentSecretsFile.get();
        if (secretsFile != null) {
            secretsFile.interrupt();
        }
    }
}
