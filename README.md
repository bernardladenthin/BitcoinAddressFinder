# BitcoinAddressFinder
A high performance bitcoin address finder.
The main goal is to generate as fast as possible (Bitcoin/Altcoin) addresses using the JVM and OpenCL and check if the address was not used before.

Copyright (c) 2017-2021 Bernard Ladenthin.

## Features
* Unit tested (trusted) open source which can be compiled easily from yourself.
* Vanitygen of bitcoin addresses.
* Runs completely offline. No internet required or used. You can run it in a bunker with an electric generator somewhere in nowhere and nobody knows it.
* No synchronisation necessary to run multiple instances. Random numbers are used and a search organization is not necessary. Yust start on multiple computers.
* Check with a high performance database containing addresses if generated address are already in use.
* Portable, plattform independend, runs on JVM.
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
Last update: February 1, 2021
* Light (3GB): Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
* Full (60GB): Contains all Bitcoin addresses which are ever used and many altcoin addresses with amount.

## Pages and projects to get lists (dumps) of PubkeyHash addresses
https://github.com/mycroft/chainstate
https://github.com/graymauser/btcposbal2csv
https://blockchair.com/dumps
https://balances.crypto-nerdz.org/

## Find addresses

### Key range
A key range can be defined (e.g. 64-bit) whereas the first 192-bit (256-bit - 64-bit) are zeroed. This can be used to creaty keys in a specific range to find keys in a known range (e.g. [Bitcoin Puzzle Transaction](https://privatekeys.pw/puzzles/bitcoin-puzzle-tx)).

The LBC to close the same but in an ordered and organized range, the project also require a communication https://lbc.cryptoguru.org/

### OpenCL
To increase the performance of the EC-key generation OpenCL can be used.
A common secret is transfered to the OpenCL device with a fixed grid size. Each OpenCL thread creates a different EC-Keys. Each OpenCL thread add its thread-id to the secret. Therefore a range of EC-keys for a fixed grid size is created at once and will be transfered back to the main memory.
The CPU is now able to hash the x,y coordinate of the EC-key to create (Bitcoin/Altcoin) addresses.
The CPU doesn't spend most of its time for EC-key generation and can be used more efficient for hashing and database lookups.

#### Performance
GPU | gridNumBits | keys / s
------------ | ------------- | -------------
Nvidia RTX 2060 | 18 | 2.16 M keys / s
Nvidia Quadro P2000 | 18 | 506 k keys /s
Nvidia Quadro M2000M | 16 | 206 k keys /s

## Collision probability
It's impossible to find collisions, isn't it? 
Please find the answear somewhere else:
* https://crypto.stackexchange.com/questions/33821/how-to-deal-with-collisions-in-bitcoin-addresses
* 

-----
## Legal
This software should not be configured and used to find (Bitcoin/Altcoin) address hash (RIPEMD-160) collisions and use (steal) credit from third-party (Bitcoin/Altcoin) addresses.
This mode might be allowed to recover lost private keys of your own public addresses only.

Another legal use case is a check if the (Bitcoin/Altcoin) addresses hash (RIPEMD-160) is already in use to prevent yourself from a known hash (RIPEMD-160) collision and double use is allowed.

This might be not legal in many countries:
* Germany: § 202c Vorbereiten des Ausspähens und Abfangens von Daten 

## License

It is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.
