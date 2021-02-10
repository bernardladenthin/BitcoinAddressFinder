# BitcoinAddressFinder
A high performance bitcoin address finder.
The main goal is to generate as fast as possible (Bitcoin/Altcoin) addresses using the JVM combined with OpenCL and check if the address (RIPEMD160 hash) was used/not used before. This includes possible hash collisions.

Copyright (c) 2017-2021 Bernard Ladenthin.

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
* Light (3GiB), Last update: February 10, 2021
  * Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
  * Unique entries: 65170711
  * Link (1.8GiB packed): http://ladenthin.net/lmdb_light.zip
  * Checksums:
    * CRC32: 7195F4B7
    * MD5: 7D30D0589FE36AD1688118D3DA7F02F7
    * RIPEMD-160: FF8C8D925E48A740A044A511E973450EC2E4F285
    * SHA-1: FF471DF78C8A6555E70BB89B0AC66345181CCABE
    * SHA-256: 7AE7B71986D6267523A48E831AA00538E40A985AD415ADB7D5803084F5C6351C
    * SHA3-224: 5D00BE0AC0B1DCC40EC890E63ADB7DB74122E4E5FCAE45AF52ACC391
    * SHA3-256: A0E39E8CC7A9B1D6AFE961515A54ECF510F9412A58C6F0E5774D71241D94A7A5
    * SHA3-384: B8DCC266E8E5F7776DC93A65BA8B5C9CD383E78F445D5A09B395EB1BB5B5F8D555945CA6807769BF22F8925957EE99F0
    * SHA3-512: 92F8AF64BAFAB5556D0569F3AD30718258C5EA3E15CE9AF054C419A398A0494A3E3F21BBB9A8C21C9D29294B4EF5DE3D98B0851392D28DCD4FA5F6CC59C4424C
  * Mapsize: 2752 MiB
  * Static amount of 0 is used to allow better compression.
* Full (41GiB), Last update: February 2, 2021
  * Contains all Bitcoin addresses which are ever used and many altcoin addresses with and without amount.
  * Unique entries: 769941703
  * Link (18.9GiB packed): http://ladenthin.net/lmdb_full.zip
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
* https://github.com/treyyoder/bitcoin-wallet-finder

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

-----
## Legal
This software should not be configured and used to find (Bitcoin/Altcoin) address hash (RIPEMD-160) collisions and use (steal) credit from third-party (Bitcoin/Altcoin) addresses.
This mode might be allowed to recover lost private keys of your own public addresses only.

Another legal use case is a check if the (Bitcoin/Altcoin) addresses hash (RIPEMD-160) is already in use to prevent yourself from a known hash (RIPEMD-160) collision and double use is allowed.

Some configurations are not allowed in some countries:
* Germany: § 202c Vorbereiten des Ausspähens und Abfangens von Daten

## License

It is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.
