{
    "command": "LMDBToAddressFile",
    "lmdbToAddressFile" : {
        "addressesFile" : "src/test/resources/testRoundtrip/export.txt",
        "addressFileOutputFormat" : "HexHash",
        "lmdbConfigurationReadOnly" : {
            "logStatsOnInit" : false,
            "logStatsOnClose" : false,
            "lmdbDirectory" : "src/test/resources/testRoundtrip/lmdb"
        }
    }
}
