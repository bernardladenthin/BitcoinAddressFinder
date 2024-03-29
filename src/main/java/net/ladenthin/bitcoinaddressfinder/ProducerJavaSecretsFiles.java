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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerJavaSecretsFiles extends ProducerJava {

    private final Logger logger = LoggerFactory.getLogger(ProducerJavaSecretsFiles.class);
    
    private final CProducerJavaSecretsFiles producerJavaSecretsFiles;

    private final ReadStatistic readStatistic = new ReadStatistic();

    @NonNull
    AtomicReference<SecretsFile> currentSecretsFile = new AtomicReference<>();

    public ProducerJavaSecretsFiles(CProducerJavaSecretsFiles producerJavaSecretsFiles, Consumer consumer, KeyUtility keyUtility, KeyProducer keyProducer) {
        super(producerJavaSecretsFiles, consumer, keyUtility, keyProducer);
        this.producerJavaSecretsFiles = producerJavaSecretsFiles;
    }
    
    @Override
    public void produceKeys() {
        try {
            final NetworkParameters networkParameters = new NetworkParameterFactory().getOrCreate();

            FileHelper fileHelper = new FileHelper();
            List<File> files = fileHelper.stringsToFiles(producerJavaSecretsFiles.files);
            fileHelper.assertFilesExists(files);

            logger.info("Iterate secrets files ...");
            for (File file : files) {
                if (!shouldRun.get()) {
                    break;
                }
                SecretsFile secretsFile = new SecretsFile(
                    networkParameters,
                    file,
                    producerJavaSecretsFiles.secretFormat,
                    readStatistic,
                    this::processSecret
                );

                logger.info("process: " + file.getAbsolutePath());
                currentSecretsFile.set(secretsFile);
                secretsFile.readFile();
                currentSecretsFile.set(null);
                logger.info("finished: " + file.getAbsolutePath());

                logProgress();
                logger.info("... iterate secrets files done.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void logProgress() {
        logger.info("Progress: Unsupported: " + readStatistic.unsupported + ". Errors: " + readStatistic.errors.size() + ". Current File progress: " + String.format("%.2f", readStatistic.currentFileProgress) + "%.");
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
