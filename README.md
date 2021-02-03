# BitcoinAddressFinder
A high performance bitcoin address finder.
The main goal is to generate as fast as possible (Bitcoin/Altcoin) addresses using the JVM and OpenCL and check if the address was used/not used before.

Copyright (c) 2017-2021 Bernard Ladenthin.

## Features
* Support blockchain addresses which are based on [secp256k1](https://en.bitcoin.it/wiki/Secp256k1).
* Unit tested (trusted) open source which can be compiled easily from yourself.
* Vanitygen of bitcoin addresses using regex pattern.
* Runs completely offline. No internet required or used. You can run it in a bunker with an electric generator somewhere in nowhere and nobody knows it.
* No synchronisation necessary to run multiple instances. Random numbers are used and a search organization is not necessary. Yust start on multiple computers.
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
Last update: February 2, 2021
* Light (3GB)
  * Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
  * Unique entries: 53595905
  * Link (1.5GB packed): http://ladenthin.net/lmdb_light.zip
  * Checksums:
    * CRC32: F105D7D2
    * MD5: 67205F0663C1704A9718E04763547003
    * RIPEMD-160: 105123329E19E42317873D5DFCEB99B089983CE0
    * SHA-1: 65BE8590B525339192683AD0123F5CEADE8408FA
    * SHA-256: C9EE740E8AADFB89DAB5B009792FDA8A880241D2A689FC31E461B78D0E198575
    * SHA3-224: B28D4499F5A2CFA2424243BC01E2DFE9C57D38ABDB841E9872674899
    * SHA3-256: B1C491D8E2111DB654632A20CE4D90F0CB6BB5B839AD57B8B9F34C4A38D5C610
    * SHA3-384: C28AF0ECE7A4A4CCBA3804954B72023E1598BB7D584D203AD54827148FAD7FD959F46AF2B652530219CD1530D71E9271
    * SHA3-512: 495264374AC4AA3E4A360D23650E31CF2CF3FD39C13AFF3BB1AF413029FBFC8C1D355D6BDD38DA47212A2C392190D06F44716F1F2A667FFF6D7C6BB9EE382E29
  * Mapsize: 2944 MiB
  * Static amount of 1 is used to allow better compression.
* Full (41GB)
  * Contains all Bitcoin addresses which are ever used and many altcoin addresses with and without amount.
  * Unique entries: 769941703
  * Link (18.9GB packed): http://ladenthin.net/lmdb_full.zip
  * Checksums:
    * CRC32: E7301034
    * MD5: 9FCFA57CEA79EDFE7D2A6614E03CD3B2
    * RIPEMD-160: E00E67B0182C0F6239AF75C6B21EA537B32D97A3
    * SHA-1: A1262B72760BA1E0B07887BF93698C1DF97B05E8
    * SHA-256: D66FF291A685A6CCE0E0D0CBF039335BB10EAB9B4F4AF2C575A67F4DAE8DB0DA
    * SHA3-224: 32229857893B18925F056D75C6E1CFD4805F5EA27FE23B9967BDB76D
    * SHA3-256: C679A3410ADEB4C1DE3973B67974A0E9EB27B487BCB72712FE9D1F47939790A3
    * SHA3-384: 2B67B1AA3A48ACCB9F7F9350500FFF7CE27A9D5CBD76C42A512E44F24A5CDC32AEF056829173ADE8E97E05F228F0B11D
    * SHA3-512: 68D66A9360842C559E9B9F012B611AFD1ACE69686627A818D67772A3BC3DEA61D79DB55337A635B8D069460ACABCB375D4C6310490DAD6E22A52E830EABC2111
  * Mapsize: 40960 MiB
  * Static amount of 1 is used to allow better compression.

## Pages and projects to get lists (dumps) of PubkeyHash addresses
* https://github.com/mycroft/chainstate
* https://github.com/graymauser/btcposbal2csv
* https://blockchair.com/dumps
* https://balances.crypto-nerdz.org/

## Find addresses

### Mixed modes
Find personal vanity addresses and check if addresses already exists in the lmdb can be used together.

### Key range
A key range can be defined (e.g. 64-bit) whereas the first 192-bit (256-bit - 64-bit) are zeroed. This can be used to creaty keys in a specific range to find keys in a known range (e.g. [Bitcoin Puzzle Transaction](https://privatekeys.pw/puzzles/bitcoin-puzzle-tx)).

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

## Similar projects
* The [LBC](https://lbc.cryptoguru.org/) is optimized to find keys for the [Bitcoin Puzzle Transaction](https://privatekeys.pw/puzzles/bitcoin-puzzle-tx). It require communication to a server, doesn't support altcoin and pattern matching.
* https://privatekeys.pw/scanner/bitcoin
* https://allprivatekeys.com/get-lucky
* https://allprivatekeys.com/vanity-address

-----
## Legal
This software should not be configured and used to find (Bitcoin/Altcoin) address hash (RIPEMD-160) collisions and use (steal) credit from third-party (Bitcoin/Altcoin) addresses.
This mode might be allowed to recover lost private keys of your own public addresses only.

Another legal use case is a check if the (Bitcoin/Altcoin) addresses hash (RIPEMD-160) is already in use to prevent yourself from a known hash (RIPEMD-160) collision and double use is allowed.

Some configurations are not allowed in some countries:
* Germany: § 202c Vorbereiten des Ausspähens und Abfangens von Daten

## License

It is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.
