# BitcoinAddressFinder
[![CI Status](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/assembly.yml/badge.svg)](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/assembly.yml)
[![CI Status](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/coverage.yml/badge.svg)](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/coverage.yml)
[![CI Status](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/matrixci.yml/badge.svg)](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/matrixci.yml)
[![Coverage Status](https://coveralls.io/repos/github/bernardladenthin/BitcoinAddressFinder/badge.svg?branch=main)](https://coveralls.io/github/bernardladenthin/BitcoinAddressFinder?branch=main)
[![codecov](https://codecov.io/gh/bernardladenthin/BitcoinAddressFinder/graph/badge.svg?token=RRCR4ZC28T)](https://codecov.io/gh/bernardladenthin/BitcoinAddressFinder)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder?ref=badge_shield)

<!--
[![Security Score](https://snyk-widget.herokuapp.com/badge/mvn/net.ladenthin/bitcoinaddressfinder/badge.svg)](https://snyk.io/test/github/bernardladenthin/BitcoinAddressFinder)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.ladenthin/bitcoinaddressfinder/badge.svg#)](https://maven-badges.herokuapp.com/maven-central/net.ladenthin/bitcoinaddressfinder)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/1234/badge)](https://bestpractices.coreinfrastructure.org/projects/1234)
-->
Free high performance tool for fast scanning random Bitcoin, Bitcoin Cash, Bitcoin SV, Litecoin, Dogecoin, Dash, Zcash (and many more) private keys and finding addresses with balance.
The main goal is to generate as fast as possible (Bitcoin/Altcoin) addresses using the JVM combined with OpenCL and check if the address (RIPEMD160 hash) was used/not used before. This includes possible hash collisions.

Copyright (c) 2017-2024 Bernard Ladenthin.

## Requirments
* Java 21 or newer. Java 8, 11, 17 is not supported anymore.

## Quickstart
1. Download the binary (jar) from https://github.com/bernardladenthin/BitcoinAddressFinder/releases
2. Download and extract the light database from https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database
3. Download a configuration set like
  1. https://github.com/bernardladenthin/BitcoinAddressFinder/blob/main/examples/logbackConfiguration.xml
  2. https://github.com/bernardladenthin/BitcoinAddressFinder/blob/main/examples/config_Find_1OpenCLDevice.js
  3. https://github.com/bernardladenthin/BitcoinAddressFinder/blob/main/examples/run_Find_1OpenCLDevice.bat
4. Put all in one directory like the following structure
  * Downloads
    * lmdb
      * data.mdb
      * lock.mdb
    * bitcoinaddressfinder-1.0.0-SNAPSHOT-jar-with-dependencies.jar
    * logbackConfiguration.xml
    * config_Find_1OpenCLDevice.js
    * run_Find_1OpenCLDevice.bat
5. Run the file run_Find_1OpenCLDevice.bat

## Features
* Support blockchain addresses which are based on [secp256k1](https://en.bitcoin.it/wiki/Secp256k1).
* Unit tested (trusted) open source which can be compiled easily from yourself.
* Vanitygen of bitcoin addresses using regex pattern.
* Runs completely offline. No internet required or used. You can run it in a bunker with an electric generator somewhere in nowhere and nobody knows it.
* No synchronisation necessary to run multiple instances. Random numbers are used and a search organization is not necessary. Just start on multiple computers.
* Check with a high performance database containing addresses if generated address are already in use.
* Portable, plattform independend, runs on JVM.
* Generate uncompressed and compressed keys at once.
* EC-Key generation via
  * Multiple CPU Threads
  * Multiple OpenCL devices (optional)

## Address database
The addresses will be inserted in a high performance database [LMDB](https://github.com/LMDB).
The database can be used to check if a generated addresses is ever used.

### Import
The importer read multiple txt/text files containing the following addresses in arbitrary order. Each line can contain a different format.
* P2PKH
  * bitcoin
  * bitcoin cash
  * bitcoin gold
  * blackcoin
  * dash
  * digibyte
  * dogecoin
  * feathercoin
  * litecoin
  * litecoin cash
  * namecoin
  * novacoin
  * reddcoin
  * vertcoin
  * ZCash
* P2WPKH
  * bitcoin Bech32

### Create the database by yourself
Useful txt/text file provider:
* http://blockdata.loyce.club/alladdresses/
* https://blockchair.com/dumps

### Export
The exporter writes all addresses in different formats:
* HexHash: The hash160 will be written encoded in hex without the amount. Optimal viewing with a viewer with a fixed width (e.g. HxD).
* FixedWidthBase58BitcoinAddress: The addresses will be written with a fixed width and without the amount. Optimal viewing with a viewer with a fixed width (e.g. HxD).
* DynamicWidthBase58BitcoinAddressWithAmount: The addresses will be written with amount.

### Use my prepared database
I am in the process of preparing databases filled with numerous Bitcoin and altcoin addresses (refer to the Import Support section for more information).
The sources of this information are confidential; however, you have the permission to extract any and all addresses.
Should there be any information you find lacking or have questions about, do not hesitate to ask.

#### Light database
* Light (5.25 GiB), Last update: March 22, 2024
  * Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 119509048
  * Mapsize: 5376 MiB
  * Time to create the database: ~9 hours
  * Link (3.3 GiB zip archive): http://ladenthin.net/lmdb_light.zip
  * Link extracted addresses (2.1 GiB zip archive): http://ladenthin.net/LMDBToAddressFile_Light_HexHash.zip

<details>
<summary>Checksums lmdb_light.zip</summary>

* CRC32: 3F1FDC9A
* MD5: 1F9776BD28ED26C1DA8D7B00DC387A2B
* RipeMD160: 8D1993B1CF022B940DB5AAA5C22CDDF816DE6CD1
* SHA-1: 95D907CED30F473428B37A59E6C9C8EDADCDD633
* SHA-256: A25A19966645C8D2F22CCA3478980AFBEE1964409C3744684241EFE5ED2DABC1
* SHA-512: 1F63C07CFB6287D87CEBE175BA1710041D133EA3B4A4A445F4BF155A00E1289A09B7EFC4A40F215C032A0D25EE357AE15E9DD3FB0C649C02149372877A9E9202
* SHA3-224: E23E6DA501C84B09C050A4EBEE255427EAAF2D2D0A9773355B055FEC
* SHA3-256: B575839DE86CEC95ACD370636CCAE35D463873F2A3A7D30296BBB9363E9AC659
* SHA3-384: B50DC230430187AC612AECFC64952129C2D54B6DE1BDBFCD239754F66D9CC2CB86A4C9DEBC196886C7B9B309A6F0439D
* SHA3-512: 75AA1D6B9379F4AAA6F01B11BE9F94B59173604EA89BAA9D4F15E3DAFAB87B9ACDDA76BB102EB83C20BA4FED85172B6D7D843EE93678C232395B1E586D23AE55

</details>


<details>
<summary>Checksums LMDBToAddressFile_Light_HexHash.zip</summary>
CRC32: CDF4DEAA
MD5: A3C18E19D12B661A52EABE3D9C3C98A5
RipeMD160: 86351035F7DF951E3BBFED6F0A1DD6E393E1A503
SHA-1: DAC3028774CA60EA34EA6C698DF18AEB1E3736FB
SHA-256: A47C074CEEDEE88C3DB4660B0A6668431195602248E11843ADC1170DB115EBE1
SHA-512: 5B135919C96782927170CC6675DA2B28130435B896B985D4406BDC7BBD64FE402362F9AD8CDE70F2DAE39D4A4500956BA76A9E40D19FAB8225FB6D9F919D006A
SHA3-224: F01CCFEED3775888569A9D36A0F456F3EF2251852771202D353081E7
SHA3-256: 8430D6F48E2D5BFD1544587AA423EC41764EE14FD619804DC6C89CE1A3D328F4
SHA3-384: F8A4A5AE2577B83F56E363C9C82D58EB782E8BCCDDEB8B92416F0A9C44438B268333D2B7B0BBE4A4ABFC35CDC1222FA7
SHA3-512: B7D69667D5AC44F5A5F7354E9C4AA34D662D837119B10C394C04A93B6F8C117F82F2F8BE576617FE7B4F940E71001949DEF1B59EC79FC920FF011050B3943B75

</details>


#### Full database
* Full (54.7 GiB), Last update: March 2, 2024
  * Contains all Bitcoin addresses which are ever used and many altcoin addresses with and without amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 1317131048
  * Mapsize: 56048 MiB
  * Time to create the database: ~54 hours
  * Link (37.7 GiB zip archive): http://ladenthin.net/lmdb_full.zip

<details>
<summary>Checksums</summary>

* CRC32: 1BBAB86F
* MD5: F3D2A7A9BBAE4AFC25D10AB3B6256FF8
* RipeMD160: 293AE3D16D55DFA7EE2E9ED0A48151A786144228
* SHA-1: 6BB264F0B14EAE533A3E659DC443902311C24083
* SHA-256: F524E087DFE0C3883B992BA09C0CD388D2ECC8E77403468AA45E5911D26854F0
* SHA-512: 83EA8E27F96439B5AF05E022A5DA3DCB0DA7035EFBB5CE5C99375DF073EA214B188C01F51F5AC254012620DBB0C87943B8F8E00DD77B85007B846AF38D4FF21A
* SHA3-224: 0BBD750196486C7A54BA4084C2A92C7C2A390D2096AD0ADD7E9AC6A1
* SHA3-256: 994CBA488664DE00DE5EBE89393C4DF07BB21F9624732D43A94899B9AD56D8C0
* SHA3-384: 82890D4A1813F929197A80A9C35384E5FCBCEFE197CD721BFCABF5453CF0011E435A847B0D06CAF6BE883BC6142BEE2F
* SHA3-512: 17E3287116AF342BE1677203B6773CCDDC510E2FB05526B7F13A5A2273EA978B4CB40E659807A51B5C1129B0C6780E22B47D1999428D8BB32412DBA3BB1BF0E8

</details>

## Pages and projects to get lists (dumps) of PubkeyHash addresses
* https://github.com/Pymmdrza/Rich-Address-Wallet
* https://github.com/mycroft/chainstate
* https://github.com/graymauser/btcposbal2csv
* https://blockchair.com/dumps
* https://balances.crypto-nerdz.org/

## Find addresses
**Attention**: Do not use this software in a productive, non safe environment. A safe environment might be a dedicated computer with an air gap / disconnected network. A side-channel attack is possible and the software is optimized for performance and not constant-time. You may use a [paper wallet](https://en.bitcoin.it/wiki/Paper_wallet) for created vanity keys.

### Mixed modes
Find personal vanity addresses and check if addresses already exists in the lmdb can be used together.

### Key range
A key range can be defined (e.g. 64-bit) whereas the first (e.g. 192-bit (256-bit - 64-bit)) are zeroed. This can be used to creaty keys in a specific range to find keys in a known range (e.g. [Bitcoin Puzzle Transaction](https://privatekeys.pw/puzzles/bitcoin-puzzle-tx)).
This can be also used to proof that the software works.

### OpenCL
To increase the performance of the EC-key generation OpenCL can be used.
A common secret is transfered to the OpenCL device with a fixed grid size. Each OpenCL thread creates a different EC-Key because it add its thread-id to the secret. Therefore a range of EC-keys for a fixed grid size is created at once and will be transfered back to the main memory.
The CPU is now able to hash the x,y coordinate of the EC-key to create (Bitcoin/Altcoin) addresses.
The CPU doesn't spend most of its time for EC-key generation and can be used more efficient for hashing and database lookups.

The OpenCL mode has a Built-in self-test (BIST) to compare the OpenCL results with CPU based EC-Key generation. This allows an end user to verify it's OpenCL device is working properly.

#### Performance
The effective keys / s using uncompressed and compressed keys. OpenCL creates uncompressed keys only. A compressed key can be deduced easily from the uncompressed key.

GPU | privateKeyMaxNumBits | gridNumBits | effective keys / s
------------ | ------------- | ------------- | -------------
Nvidia RTX 2060 | 256 | 18 | 2160 k keys / s
Nvidia Quadro P2000 | 256 | 18 | 505 k keys /s
Nvidia Quadro P2000 | 64 | 18 | more than 1000 k keys /s (CPU was at its limit)
Nvidia Quadro M2000M | 256 | 16 | 205 k keys /s
Nvidia GTX 1050 Ti Mobile | 64 | 16 | more than 1000 k keys /s (CPU was at its limit)
Nvidia GTX 1050 Ti Mobile | 256 | 16 | 550 k keys /s

## Collision probability and security concerns
It's impossible to find collisions, isn't it? 
Please find the answear for vulnerability questions somewhere else:
* https://crypto.stackexchange.com/questions/33821/how-to-deal-with-collisions-in-bitcoin-addresses
* https://crypto.stackexchange.com/questions/47809/why-havent-any-sha-256-collisions-been-found-yet
* https://github.com/treyyoder/bitcoin-wallet-finder#results
* https://github.com/Frankenmint/PKGenerator_Checker#instructions
* https://github.com/Xefrok/BitBruteForce-Wallet#requeriments

## Similar projects
* The [LBC](https://lbc.cryptoguru.org/) is optimized to find keys for the [Bitcoin Puzzle Transaction](https://privatekeys.pw/puzzles/bitcoin-puzzle-tx). It require communication to a server, doesn't support altcoin and pattern matching.
* https://privatekeys.pw/scanner/bitcoin
* https://allprivatekeys.com/get-lucky
* https://allprivatekeys.com/vanity-address
* https://github.com/treyyoder/bitcoin-wallet-finder
* https://github.com/albertobsd/keyhunt
* https://github.com/mvrc42/bitp0wn
* https://github.com/JeanLucPons/BTCCollider
* https://github.com/JeanLucPons/VanitySearch
* https://github.com/JamieAcharya/Bitcoin-Private-Key-Finder
* https://github.com/mingfunwong/all-bitcoin-private-key
* https://github.com/Frankenmint/PKGenerator_Checker
* https://github.com/Henshall/BitcoinPrivateKeyHunter
* https://github.com/Xefrok/BitBruteForce-Wallet
* https://github.com/Isaacdelly/Plutus
* https://github.com/Noname400/Hunt-to-Mnemonic
* https://github.com/Py-Project/Bitcoin-wallet-cracker

### Deep learning private key prediction
An export of the full database can be used to predict private keys with deep learning. A funny idea: https://github.com/DRSZL/BitcoinTensorFlowPrivateKeyPrediction

## Known issues
If you have a laptop like HP ZBook G3/G4/G5 "hybrid graphics" mode is very slow because of the shared memory. Please select in the BIOS "discrete graphics".

## Future improvements
- Refactor the entire key generation infrastructure to support a key provider. This provider should be configurable to supply private keys from various sources, such as Random, Secrets File, Key Range, and others. All consumers should retrieve keys from this provider.

### KeyProvider
- Key generation within a specific key range. See #27
Wished from themaster:
```
"privateKeyStartHex" : "0000000000000000000000000000000000000000000000037e26d5b1f3afe216"
"privateKeyEndHex" : "0000000000000000000000000000000000000000000000037e26d5b1ffffffff"
```
Wished from Ulugbek:
```
// Search started from given address. Would be nice if it can save last position...
"sequentalSearch" : true,
"startAddress" : xxxxxxxx,

// Random search with batches, here 100000. I,e. some random number is found and after 100000 sequental addresses should be checked.
"searchAsBatches" : true,
"searchBatchQuantity" : 100000,


// Random search within Address Space, with batches, here 100000.
"searchAsBatches" : true,
"searchAddressStart" : xxxxxxx,
"searchAddressEnd" : xxxxxxxy,
"searchBatchQuantity" : 100000
```

- Incomplete Seed-Phrase as Private Key provider. Wished from @mirasu See #38
- Socket key provider for independend key provider via byte protocol
  - Ideas might be a screen recorder and use the visible screen downscaled as 256 bit input

-----
## Legal
This software should not be configured and used to find (Bitcoin/Altcoin) address hash (RIPEMD-160) collisions and use (steal) credit from third-party (Bitcoin/Altcoin) addresses.
This mode might be allowed to recover lost private keys of your own public addresses only.

Another mostly legal use case is a check if the (Bitcoin/Altcoin) addresses hash (RIPEMD-160) is already in use to prevent yourself from a known hash (RIPEMD-160) collision and double use.

Some configurations are not allowed in some countries (definitely not complete):
* Germany: § 202c Vorbereiten des Ausspähens und Abfangens von Daten
* United States of America (USA): Computer Fraud and Abuse Act (CFAA)

## License

It is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.
Some subprojects have a different license.



[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder?ref=badge_large)