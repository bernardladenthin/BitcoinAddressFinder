rem start /low java ^
java ^
--add-opens java.base/java.lang=ALL-UNNAMED ^
--add-opens java.base/java.io=ALL-UNNAMED ^
--add-opens java.base/java.nio=ALL-UNNAMED ^
--add-opens java.base/jdk.internal.ref=ALL-UNNAMED ^
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED ^
--add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
--add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED ^
-Xms512m ^
-Xmx512m ^
-Dlogback.configurationFile=logbackConfiguration.xml ^
-jar ^
bitcoinaddressfinder-1.4.0-jar-with-dependencies.jar ^
config_OpenCLInfo.json
rem >> log_OpenCLInfo.txt 2>&1
