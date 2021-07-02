# BitcoinAddressFinder
Free high performance tool for fast scanning random Bitcoin, Bitcoin Cash, Bitcoin SV, Litecoin, Dogecoin, Dash, Zcash (and many more) private keys and finding addresses with balance.
The main goal is to generate as fast as possible (Bitcoin/Altcoin) addresses using the JVM combined with OpenCL and check if the address (RIPEMD160 hash) was used/not used before. This includes possible hash collisions.

Copyright (c) 2017-2021 Bernard Ladenthin.

## Requirments
* Java 8, newer versions are not supported and doesn't work (see #8).

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
* Light (3.21 GiB), Last update: June 8, 2021
  * Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 78059862
  * Mapsize: 3296 MiB
  * Time to create the database: ~4 hours
  * Link (2.1 GiB zip archive): http://ladenthin.net/lmdb_light.zip
    * CRC32: BA810113
    * MD5: 8E714F1730999B01607E7992F27DB670
    * RIPEMD-160: 09BAF4AE9F4FDD4944484563F3A70839358E7A75
    * SHA-1: F7E8384092D53A08F7806A33CFB7514D5E3A17D1
    * SHA-256: 3D25517D77153C2AB9C7F3BCDA1282B239DBE4C13AD08474D83E061B10732C69
    * SHA3-224: 1CD23DFC992BE384A2C8F3DFC830B4D291A27AA4032AF6C7BED4D4D9
    * SHA3-256: 8B2398B8F207D8F3033AEAFB8B23C30E7CC3B96E1AF0BBD1C5538156C4AE3A16
    * SHA3-384: 1187076FFAEABFAB9256B68254EA8553963A2604A5FA4748D3BC7B877BDFED071234E213D500B677B6B3B18488A6FC52
    * SHA3-512: 20B00BB3A31C76104F65810FAB079D70809D8751FC329B5626120A1F5F08F9D3F2853067007CBFA4CB1C9A0910EDA31813F86A59962D3DAC2CAD3BAAC678453A

* Full (32.2 GiB), Last update: February 10, 2021
  * Contains all Bitcoin addresses which are ever used and many altcoin addresses with and without amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 781419881
  * Mapsize: 32992 MiB
  * Time to create the database: ~34 hours
  * Link (17.5 GiB zip archive): http://ladenthin.net/lmdb_full.zip
    * CRC32: 04D50A18
    * MD5: 6E8968DBF4A698C908BFAC5A2315F52D
    * RIPEMD-160: C5BFDBDDE6FCD829E15F893D874E2C203798D1DD
    * SHA-1: 226F309BDB355C8867256434568A3E752E6BF94C
    * SHA-256: 6130F9879C6C14B4BE2C27403B5910BACA517366D32578134B65FF68853FA574
    * SHA3-224: 61666FA472D9B3477FF98E845781BD564192C19BF4371C9BF22F7B49
    * SHA3-256: BB516A17C8DBC2357E610B70445DC1606AE57927B22B8763DE186D52B96C6CF9
    * SHA3-384: B65FB4EDF10FC496300F7AD70C17C3DF3E351F4FB7F117AD11AFB67F155123291DCF28B4E417227BB0C9491E6FBEE6C8
    * SHA3-512: B30959C4A21B187782EFB04C268B392555F774C3025DE51BE34FE63249D42724AA907650C53A1C67D38D5A63D74E5E99A80DBF8630F8D611C709FE9DCD6706FF

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

