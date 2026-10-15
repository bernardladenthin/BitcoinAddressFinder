#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
#
# exp01 -- first-pass diagnosis on one card (H1, H3, H4, H5, H6). ~25 min.
#   A  TuneConfiguration, 2 arms (22/256, 24/512), logGpuDiagnostics -> resource line + build log,
#      and a reproduction of the 2026-10-15 numbers on this host
#   B  GpuFuse8FilterBenchmark compact 22/256, profiling on:
#        filterEntries 1 M (L2-resident) vs 141 M (production size)   -> H3
#        kernelProfileStage FULL vs NO_HASH160                          -> H6
#      [profiling] lines give device kernel time per launch            -> H5
source /root/common.sh

cat > /root/tune.json <<'EOF'
{ "command": "TuneConfiguration",
  "tuneConfiguration": {
    "secondsPerArm": 15, "warmupSecondsPerArm": 5, "targetDatabaseEntries": 141045995,
    "batchSizeInBitsCandidates": [22, 24], "keysPerWorkItemCandidates": [256, 512],
    "extendSweepWhenWinnerIsAtTheEdge": false, "sweepFilterTypes": false, "verificationCostSamples": 2000,
    "finder": {
      "keyProducerJavaRandom": [ { "keyProducerId": "r", "randomAlgorithm": "SECURE_RANDOM", "customSeed": 0, "privateKeyMaxNumBits": 256 } ],
      "consumerJava": {
        "lmdbConfigurationReadOnly": { "lmdbDirectory": "lmdb", "addressLookupBackend": "LMDB_ONLY", "disableAddressLookup": false },
        "printStatisticsEveryNSeconds": 10, "statisticsRateWindowSeconds": 60, "threads": 4,
        "queuePollTimeoutMillis": 50, "queueSize": 4, "awaitQueueEmptySeconds": 60,
        "runtimePublicKeyCalculationCheck": false, "enableVanity": false },
      "awaitTerminateSeconds": 31536000000, "producerJava": [], "producerJavaSecretsFiles": [],
      "producerOpenCL": [ { "platformIndex": 0, "deviceType": -1, "deviceIndex": 0, "maxResultReaderThreads": 4,
        "keyProducerId": "r", "batchSizeInBits": 22, "keysPerWorkItem": 256, "batchUsePrivateKeyIncrement": true,
        "runOnce": false, "enableGpuFilter": true, "transferAll": false, "gpuFilterType": "FUSE_8",
        "logGpuDiagnostics": true } ] } } }
EOF
baf A-tune /root/tune.json

jmh B-filter-stage GpuFuse8FilterBenchmark -p gpuFilter=true -p batchSizeInBits=22 -p keysPerWorkItem=256 \
  -p filterEntries=1048576,141045995 -p kernelProfileStage=FULL,NO_HASH160 -p useReducedRadixField=true \
  -p profiling=true -p noInlineHelpers=false -f 1 -wi 1 -w 10 -i 2 -r 15
echo "=== exp01 done $(date -u +%FT%TZ)"
