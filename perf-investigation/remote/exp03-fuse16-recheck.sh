#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
#
# exp03 -- re-measure with FUSE_16 (F6) on another card: FUSE_16 vs FUSE_8, 4 consumer threads.
# TuneConfiguration on the same arms with gpuFilterType FUSE_8 vs FUSE_16 (~256x fewer survivors),
# consumer threads 4 vs 8 for FUSE_8. ~15 min.
source /root/common.sh
mk() {  # mk <file> <gpuFilterType> <consumerThreads>
python3 - "$1" "$2" "$3" <<'PY'
import json,sys
d={"command":"TuneConfiguration","tuneConfiguration":{"secondsPerArm":15,"warmupSecondsPerArm":5,
 "targetDatabaseEntries":141045995,"batchSizeInBitsCandidates":[24],"keysPerWorkItemCandidates":[256,512,1024],
 "extendSweepWhenWinnerIsAtTheEdge":False,"sweepFilterTypes":False,"verificationCostSamples":2000,
 "finder":{"keyProducerJavaRandom":[{"keyProducerId":"r","randomAlgorithm":"SECURE_RANDOM","customSeed":0,"privateKeyMaxNumBits":256}],
  "consumerJava":{"lmdbConfigurationReadOnly":{"lmdbDirectory":"lmdb","addressLookupBackend":"LMDB_ONLY","disableAddressLookup":False},
   "printStatisticsEveryNSeconds":10,"statisticsRateWindowSeconds":60,"threads":int(sys.argv[3]),"queuePollTimeoutMillis":50,
   "queueSize":4,"awaitQueueEmptySeconds":60,"runtimePublicKeyCalculationCheck":False,"enableVanity":False},
  "awaitTerminateSeconds":31536000000,"producerJava":[],"producerJavaSecretsFiles":[],
  "producerOpenCL":[{"platformIndex":0,"deviceType":-1,"deviceIndex":0,"maxResultReaderThreads":4,"keyProducerId":"r",
   "batchSizeInBits":22,"keysPerWorkItem":256,"batchUsePrivateKeyIncrement":True,"runOnce":False,
   "enableGpuFilter":True,"transferAll":False,"gpuFilterType":sys.argv[2]}]}}}
json.dump(d,open(sys.argv[1],"w"))
PY
}
which python3 > /dev/null || apt-get install -y -qq python3 > /dev/null 2>&1
mk /root/t16.json FUSE_16 4;  baf tune-FUSE16-t4 /root/t16.json
mk /root/t8.json  FUSE_8 4;   baf tune-FUSE8-t4  /root/t8.json

echo "=== exp03 done $(date -u +%FT%TZ)"
