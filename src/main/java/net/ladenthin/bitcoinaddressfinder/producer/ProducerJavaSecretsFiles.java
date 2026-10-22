// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.io.FileHelper;
import net.ladenthin.bitcoinaddressfinder.io.SecretsFile;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.statistics.ReadStatistic;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
    AtomicReference<@Nullable SecretsFile> currentSecretsFile = new AtomicReference<>();

    /**
     * Creates a new secrets-file producer.
     *
     * @param producerJavaSecretsFiles the producer configuration
     * @param consumer                 the downstream consumer
     * @param keyUtility               cryptographic helper
     * @param keyProducer              the secret supplying strategy (unused in this producer)
     * @param bitHelper                bit/batch-size helper
     * @param runtimeStatistics        shared runtime metrics sink for per-producer batch counts
     */
    public ProducerJavaSecretsFiles(
            CProducerJavaSecretsFiles producerJavaSecretsFiles,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper,
            RuntimeStatistics runtimeStatistics) {
        super(producerJavaSecretsFiles, consumer, keyUtility, keyProducer, bitHelper, runtimeStatistics);
        this.producerJavaSecretsFiles = producerJavaSecretsFiles;
    }

    @Override
    public void produceKeys() throws IOException {
        FileHelper fileHelper = new FileHelper();
        List<File> files = fileHelper.stringsToFiles(producerJavaSecretsFiles.secretsFiles);
        fileHelper.assertFilesExists(files);

        LOGGER.info("Starting secrets file processing...");
        for (File file : files) {
            if (!shouldRun.get()) {
                LOGGER.info("Key production stopped by flag.");
                break;
            }
            SecretsFile secretsFile = createSecretsFile(file);

            LOGGER.info("Processing secrets file: {}", file);
            currentSecretsFile.set(secretsFile);
            // interrupt() may have run after the flag check above, while no reader was registered to stop
            if (!shouldRun.get()) {
                secretsFile.interrupt();
            }
            secretsFile.readFile();
            currentSecretsFile.set(null);
            if (!shouldRun.get()) {
                LOGGER.warn("Secrets file processing interrupted in: {}", file);
                logProgress();
                return;
            }
            LOGGER.info("Finished processing: {}", file);

            logProgress();
        }
        if (shouldRun.get()) {
            LOGGER.info("All secrets files processed.");
        } else {
            LOGGER.warn("Secrets file processing interrupted; not every file was processed.");
        }
    }

    /**
     * Creates the reader for one secrets file. A seam for tests, which can act between the flag check at
     * the top of the file loop and the reader becoming visible to {@link #interrupt()}.
     *
     * @param file the secrets file to read
     * @return the reader for {@code file}
     */
    SecretsFile createSecretsFile(File file) {
        return new SecretsFile(
                network, file, producerJavaSecretsFiles.secretFormat, readStatistic, this::consumeSecrets);
    }

    private void logProgress() {
        LOGGER.info("Progress: Unsupported: " + readStatistic.getUnsupportedTotal() + ". Errors: "
                + readStatistic.errorCount + ". Current File progress: "
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
