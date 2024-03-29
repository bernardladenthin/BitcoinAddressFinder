{
    "command": "Find",
    "finder" : {
        "consumerJava" : {
            "lmdbConfigurationReadOnly" : {
                "lmdbDirectory" : "lmdb",
                "useProxyOptimal" : true,
                "logStatsOnInit" : true,
                "logStatsOnClose" : false
            },
            "threads" : 4,
            "delayEmptyConsumer" : 50,
            "printStatisticsEveryNSeconds" : 10,
            "queueSize" : 4,
            "runtimePublicKeyCalculationCheck" : false,
            "enableVanity" : false,
            "vanityPattern" : "1[Ee][Mm][Ii][Ll].*"
        },
        "producerOpenCL" : [
        ],
        "producerJavaSecretsFiles" : [
            {
                "files" : [
                    "secrets/fileContainingSecrets_BIG_INTEGER.txt"
                ],
                "runOnce" : true,
                "secretFormat" : "BIG_INTEGER",
                "logSecretBase" : true,
                "gridNumBits" : 0
            },
            {
                "files" : [
                    "secrets/fileContainingSecrets_DUMPED_RIVATE_KEY.txt"
                ],
                "runOnce" : true,
                "secretFormat" : "DUMPED_RIVATE_KEY",
                "logSecretBase" : true,
                "gridNumBits" : 0
            },
            {
                "files" : [
                    "secrets/fileContainingSecrets_SHA256.txt"
                ],
                "runOnce" : true,
                "secretFormat" : "SHA256",
                "logSecretBase" : true,
                "gridNumBits" : 0
            },
            {
                "files" : [
                    "secrets/fileContainingSecrets_STRING_DO_SHA256.txt"
                ],
                "runOnce" : true,
                "secretFormat" : "STRING_DO_SHA256",
                "logSecretBase" : true,
                "gridNumBits" : 0
            }
        ]
    }
}
