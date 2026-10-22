#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

# pocl's CPU device runs kernels in work-groups of up to 4096 by default, and the key kernel then
# overflows the 8 MB stack of pocl's worker thread (SIGSEGV, exit 139/134). The finder launches every
# kernel there with a local work size of at most 256 itself and does not need this variable; it stays
# as a second cap (a lower value here also lowers the finder's limit). Other OpenCL drivers ignore it.
export POCL_MAX_WORK_GROUP_SIZE="${POCL_MAX_WORK_GROUP_SIZE:-256}"

# OpenCL drivers that compile kernels in-process with LLVM (measured: pocl) replace the JVM's SIGSEGV
# handler, and concurrent safepoints can then kill the JVM without an hs_err file. Preloading the
# JDK's signal chaining (libjsig) keeps the JVM's handlers in front. Linux only.
if [ "$(uname -s)" = "Linux" ]; then
  JSIG="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")/lib/libjsig.so"
  if [ -f "$JSIG" ]; then
    export LD_PRELOAD="$JSIG${LD_PRELOAD:+:$LD_PRELOAD}"
  fi
fi

# start /low java \
java \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.io=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/jdk.internal.ref=ALL-UNNAMED \
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
--add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
--add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
--add-exports java.base/java.lang=ALL-UNNAMED \
--add-exports java.base/java.io=ALL-UNNAMED \
--add-exports java.base/java.nio=ALL-UNNAMED \
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
--add-exports java.base/sun.nio.ch=ALL-UNNAMED \
--add-exports jdk.management/com.sun.management.internal=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
-Xms512M \
-Xmx16G \
-Dlogback.configurationFile=logbackConfiguration.xml \
-jar \
bitcoinaddressfinder-1.9.0-SNAPSHOT-jar-with-dependencies.jar \
config_Find_1OpenCLDevice.json
# >> log_Find_1OpenCLDevice.txt 2>&1
