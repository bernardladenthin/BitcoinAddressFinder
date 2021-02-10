{
    "command": "Find",
	"finder" : {
        "consumerJava" : {
            "lmdbConfigurationReadOnly" : {
                "lmdbDirectory" : "lmdb",
                "useProxyOptimal" : true
            },
            "threads" : 8,
            "delayEmptyConsumer" : 50,
            "printStatisticsEveryNSeconds" : 10,
            "queueSize" : 4,
            "runtimePublicKeyCalculationCheck" : false,
            "enableVanity" : false,
            "vanityPattern" : "1[Ee][Mm][Ii][Ll].*"
        },
        "producerOpenCL" : [
            {
                "privateKeyMaxNumBits" : 256,
                "platformIndex" : 0,
                "deviceType" : -1,
                "deviceIndex" : 0,
                "gridNumBits" : 18,
                "maxResultReaderThreads" : 4,
                "delayBlockedReader" : 50
            }
        ],
        "producerJava" : [
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
