REM SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
REM
REM SPDX-License-Identifier: Apache-2.0

rem start /low java ^
java ^
--add-opens java.base/java.lang=ALL-UNNAMED ^
--add-opens java.base/java.io=ALL-UNNAMED ^
--add-opens java.base/java.nio=ALL-UNNAMED ^
--add-opens java.base/jdk.internal.ref=ALL-UNNAMED ^
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED ^
--add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
--add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED ^
--add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED ^
--add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED ^
--add-exports java.base/java.lang=ALL-UNNAMED ^
--add-exports java.base/java.io=ALL-UNNAMED ^
--add-exports java.base/java.nio=ALL-UNNAMED ^
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED ^
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED ^
--add-exports java.base/sun.nio.ch=ALL-UNNAMED ^
--add-exports jdk.management/com.sun.management.internal=ALL-UNNAMED ^
--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED ^
--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED ^
--add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED ^
--add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED ^
--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED ^
--add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED ^
--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED ^
--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED ^
-Xms4g ^
-Xmx8g ^
-Dlogback.configurationFile=logbackConfiguration.xml ^
-jar ^
bitcoinaddressfinder-1.6.1-jar-with-dependencies.jar ^
config_TuneConfiguration.json
rem >> log_TuneConfiguration.txt 2>&1
