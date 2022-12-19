rem start /low java ^
java ^
--illegal-access=permit ^
--add-opens java.base/java.lang=ALL-UNNAMED ^
--add-opens java.base/java.io=ALL-UNNAMED ^
--add-opens java.base/java.nio=ALL-UNNAMED ^
--add-opens java.base/jdk.internal.ref=ALL-UNNAMED ^
--add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
--add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED ^
-server ^
-Xms512M ^
-Xmx16G ^
-Dlogback.configurationFile=logbackConfiguration.xml ^
-jar ^
bitcoinaddressfinder-1.1.0-SNAPSHOT-jar-with-dependencies.jar ^
config_Find_8CPUProducer.js >> log_Find_8CPUProducer.txt 2>&1
