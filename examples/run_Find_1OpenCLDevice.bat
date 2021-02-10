rem start /low java ^
java ^
-server ^
-Xms512M ^
-Xmx16G ^
-Dlogback.configurationFile=logbackConfiguration.xml ^
-jar ^
bitcoinaddressfinder-1.0.0-SNAPSHOT-jar-with-dependencies.jar ^
config_Find_1OpenCLDevice.js >> log_Find_1OpenCLDevice.txt 2>&1
