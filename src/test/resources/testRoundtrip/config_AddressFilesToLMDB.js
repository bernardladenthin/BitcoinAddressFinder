{
    "command": "AddressFilesToLMDB",
    "addressFilesToLMDB" : {
        "addressesFiles" : [
            "src/test/resources/testRoundtrip/addresses/fileContainingAddresses0.txt",
            "src/test/resources/testRoundtrip/addresses/fileContainingAddresses1.tsv",
            "src/test/resources/testRoundtrip/addresses/fileContainingAddresses2.csv"
        ],
        "lmdbConfigurationWrite" : {
            "logStatsOnInit" : true,
            "logStatsOnClose" : true,
            "deleteEmptyAddresses" : false,
            "useStaticAmount" : true,
            "staticAmount" : 0,
            "lmdbDirectory" : "src/test/resources/testRoundtrip/lmdb",
            "initialMapSizeInMiB" : 16,
            "increaseMapAutomatically" : true,
            "increaseSizeInMiB" : 1
        }
    }
}
