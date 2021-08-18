rem start /low java ^
java ^
-server ^
-Xms512m ^
-Xmx512m ^
-Dlogback.configurationFile=logbackConfiguration.xml ^
-jar ^
bitcoinaddressfinder-1.0.0-SNAPSHOT-jar-with-dependencies.jar ^
config_LMDBToAddressFile.js >> log_LMDBToAddressFile.txt 2>&1
