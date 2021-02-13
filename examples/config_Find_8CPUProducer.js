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
        "producerJava" : [
            {
                "gridNumBits" : 14,
                "privateKeyMaxNumBits" : 256
            },
            {
                "gridNumBits" : 14,
                "privateKeyMaxNumBits" : 256
            },
            {
                "gridNumBits" : 14,
                "privateKeyMaxNumBits" : 256
            },
            {
                "gridNumBits" : 14,
                "privateKeyMaxNumBits" : 256
            },
            {
                "gridNumBits" : 14,
                "privateKeyMaxNumBits" : 256
            },
            {
                "gridNumBits" : 14,
                "privateKeyMaxNumBits" : 256
            },
            {
                "gridNumBits" : 14,
                "privateKeyMaxNumBits" : 256
            },
            {
                "gridNumBits" : 14,
                "privateKeyMaxNumBits" : 256
            }
        ]
    }
}
