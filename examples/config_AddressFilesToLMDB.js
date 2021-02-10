{
    "command": "AddressFilesToLMDB",
    "addressFilesToLMDB" : {
        "addressesFiles" : [
            "addresses/fileContainingAddresses0.txt",
            "addresses/fileContainingAddresses1.tsv",
            "addresses/fileContainingAddresses2.csv"
        ],
        "lmdbConfigurationWrite" : {
            "logStatsOnInit" : true,
            "logStatsOnClose" : true,
            "deleteEmptyAddresses" : false,
            "useStaticAmount" : true,
            "staticAmount" : 0,
            "lmdbDirectory" : "lmdb",
            "initialMapSizeInMiB" : 16,
            "increaseMapAutomatically" : true,
            "increaseSizeInMiB" : 1
        }
    }
}
