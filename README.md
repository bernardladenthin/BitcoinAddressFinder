# BitcoinAddressFinder
[![CI Status](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/maven.yml/badge.svg)](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/maven.yml)
[![Actions Status](https://github.com/bernardladenthin/BitcoinAddressFinder/workflows/CI/badge.svg)](https://github.com/bernardladenthin/BitcoinAddressFinder/actions)
[![Coverage Status](https://coveralls.io/repos/github/bernardladenthin/BitcoinAddressFinder/badge.svg?branch=main)](https://coveralls.io/github/bernardladenthin/BitcoinAddressFinder?branch=main)
[![Codecov](https://codecov.io/github/bernardladenthin/BitcoinAddressFinder/coverage.png)](https://codecov.io/gh/bernardladenthin/BitcoinAddressFinder)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder?ref=badge_shield)

<!--
[![Security Score](https://snyk-widget.herokuapp.com/badge/mvn/net.ladenthin/bitcoinaddressfinder/badge.svg)](https://snyk.io/test/github/bernardladenthin/BitcoinAddressFinder)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.ladenthin/bitcoinaddressfinder/badge.svg#)](https://maven-badges.herokuapp.com/maven-central/net.ladenthin/bitcoinaddressfinder)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/1234/badge)](https://bestpractices.coreinfrastructure.org/projects/1234)
-->
Free high performance tool for fast scanning random Bitcoin, Bitcoin Cash, Bitcoin SV, Litecoin, Dogecoin, Dash, Zcash (and many more) private keys and finding addresses with balance.
The main goal is to generate as fast as possible (Bitcoin/Altcoin) addresses using the JVM combined with OpenCL and check if the address (RIPEMD160 hash) was used/not used before. This includes possible hash collisions.

Copyright (c) 2017-2021 Bernard Ladenthin.

## Requirments
* Java 11 or newer. Java 8 is not supported anymore.

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
#### Light database
* Light (4.60 GiB), Last update: November 15, 2022
  * Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 111983796
  * Mapsize: 4712 MiB
  * Time to create the database: ~6 hours
  * Link (3.1 GiB zip archive): http://ladenthin.net/lmdb_light.zip

<details>
<summary>Checksums</summary>

* CRC32: 82f0a398
* MD5: ed2cec10b9013ba72b3e1d073df2206c
* RIPEMD-160: a5c8836d7c3106d0d68486331ef319dad7560d44
* SHA-1: bf73bb546744f15a3f436fc6d3288cf618e0fc87
* SHA-256: 70386188fb8bd15e057ca74a0ceb941e07b1e8a6f3a3e00d30642c0a4502781f
* SHA-512: 24afc89d4713ce7cdff176dd924daa02024d1e33f66db25dfc12b253529b4e86867e20e5624a551de25a18f73fe8ec4d7346313ed0b5f3aee56529fcc40dd992
* SHA3-224: 99b53de575124826e9ab426a61e204a82328b5c13b6d5529464df808
* SHA3-256: 5c581df215ec3b599244f3d5e835509ba49b65c345c6ee0a367e1b75bedb5142
* SHA3-384: e80f23680958ea5c017999776405342442e81c1878b7167bf341c01148268a10d32d308f3b2b4db677d8f4ceabd95500
* SHA3-512: 1f7f8d3541bd2caf01a0a35730a901635cbac83dd0a9c933b56980dba50db28ea5ef2227d1fd1804e528d6ded815af5696bddf366f97030fae07cd3f6efafc10

</details>

#### Full database
* Full (40.0 GiB), Last update: November 15, 2022
  * Contains all Bitcoin addresses which are ever used and many altcoin addresses with and without amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 1088415723
  * Mapsize: 40976 MiB
  * Time to create the database: ~44 hours
  * Link (25.6 GiB zip archive): http://ladenthin.net/lmdb_full.zip

<details>
<summary>Checksums</summary>

* CRC32: b5f5c25e
* MD5: 5f2af15377d1b58f93ddd83e9a0456c4
* RIPEMD-160: 2683367e6387e274123fc932863a7f4b6330d42e
* SHA-1: cf046e92c1077cb0306a74d2b413b3ecd91430e6
* SHA-256: ac05a34d878ba50f87d866bad97e515fe855d4653c5d9f9acdfae9bb44b83a05
* SHA-512: d73112eb22e5d2c9d4be3f6f8021609b01e3427625a5b93a8caa2f1d8302c893765709562ef8f856077bc9ada1395f781ebbd80c2101f43bd4a5f74f270d6ddc
* SHA3-224: a19b7d951b1c20b1022dabaf2b47de68f6299ceeba73723e2036ed81
* SHA3-256: e9f124a3c4130012eeb0ed29de19c8da8231974c37b91251ec4a564278f9fae6
* SHA3-384: 58ca5fb2246e2a5ad76cf2197d37d867117fc4018e5ebeb5a144f24a1eac706fb2b56fe07e4fd1394abfb0f15a2a670d
* SHA3-512: a10aa63918f30940e6048d0145a8c7b8a04dd0bc6586852879050c25f042137f93b196dd34e862ccb02ddd8fe3dbe74316e8fda834cecd0cf8bf48ba26d127a2

</details>

## Pages and projects to get lists (dumps) of PubkeyHash addresses
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

## Known issues
If you have a laptop like HP ZBook G3/G4/G5 "hybrid graphics" mode is very slow because of the shared memory. Please select in the BIOS "discrete graphics".

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