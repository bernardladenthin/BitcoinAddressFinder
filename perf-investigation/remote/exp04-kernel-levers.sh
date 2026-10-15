#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
#
# exp04 -- with the host cap gone (FUSE_16, F6/F7): is there kernel headroom on this card?
# One arm (24/512, FUSE_16), one tuner run per build-option variant via the extraBuildOptions
# system property: KEYS_BATCH_INV 8 / 16 / 32, -cl-nv-maxrregcount 96 / 168. ~12 min.
source /root/common.sh
which python3 > /dev/null || apt-get install -y -qq python3 > /dev/null 2>&1
python3 - /root/t.json <<'PY'
import json,sys
d={"command":"TuneConfiguration","tuneConfiguration":{"secondsPerArm":20,"warmupSecondsPerArm":5,
 "targetDatabaseEntries":141045995,"batchSizeInBitsCandidates":[24],"keysPerWorkItemCandidates":[512],
 "extendSweepWhenWinnerIsAtTheEdge":False,"sweepFilterTypes":False,"verificationCostSamples":2000,
 "finder":{"keyProducerJavaRandom":[{"keyProducerId":"r","randomAlgorithm":"SECURE_RANDOM","customSeed":0,"privateKeyMaxNumBits":256}],
  "consumerJava":{"lmdbConfigurationReadOnly":{"lmdbDirectory":"lmdb","addressLookupBackend":"LMDB_ONLY","disableAddressLookup":False},
   "printStatisticsEveryNSeconds":10,"statisticsRateWindowSeconds":60,"threads":4,"queuePollTimeoutMillis":50,
   "queueSize":4,"awaitQueueEmptySeconds":60,"runtimePublicKeyCalculationCheck":False,"enableVanity":False},
  "awaitTerminateSeconds":31536000000,"producerJava":[],"producerJavaSecretsFiles":[],
  "producerOpenCL":[{"platformIndex":0,"deviceType":-1,"deviceIndex":0,"maxResultReaderThreads":4,"keyProducerId":"r",
   "batchSizeInBits":24,"keysPerWorkItem":512,"batchUsePrivateKeyIncrement":True,"runOnce":False,
   "enableGpuFilter":True,"transferAll":False,"gpuFilterType":"FUSE_16"}]}}}
json.dump(d,open(sys.argv[1],"w"))
PY
P=net.ladenthin.bitcoinaddressfinder.extraBuildOptions
BAF_PROPS=""                              baf base     /root/t.json
BAF_PROPS="-D$P=-DKEYS_BATCH_INV=8"       baf kbi8     /root/t.json
BAF_PROPS="-D$P=-DKEYS_BATCH_INV=32"      baf kbi32    /root/t.json
BAF_PROPS="-D$P=-cl-nv-maxrregcount=96"   baf rreg96   /root/t.json
BAF_PROPS="-D$P=-cl-nv-maxrregcount=168"  baf rreg168  /root/t.json
BAF_PROPS=""                              baf base2    /root/t.json
echo "=== exp04 done $(date -u +%FT%TZ)"
