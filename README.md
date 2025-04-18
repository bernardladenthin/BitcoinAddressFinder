# BitcoinAddressFinder
> 🚀 Fast address finder for Bitcoin and altcoins using OpenCL & Java – includes vanity address generation, balance checking, and offline support.
<!-- =========================== Build & Environment =========================== -->
[![OpenJDK](https://img.shields.io/badge/OpenJDK-21-blue)]()
[![JUnit](https://img.shields.io/badge/tested%20with-JUnit4-yellow)]()
[![Assembly](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/assembly.yml/badge.svg)](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/assembly.yml)
[![Coverage](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/coverage.yml/badge.svg)](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/coverage.yml)
[![Matrix CI](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/matrixci.yml/badge.svg)](https://github.com/bernardladenthin/BitcoinAddressFinder/actions/workflows/matrixci.yml)
<!-- =========================== Test Coverage =========================== -->
[![Coverage Status](https://coveralls.io/repos/github/bernardladenthin/BitcoinAddressFinder/badge.svg?branch=main)](https://coveralls.io/github/bernardladenthin/BitcoinAddressFinder?branch=main)
[![codecov](https://codecov.io/gh/bernardladenthin/BitcoinAddressFinder/graph/badge.svg?token=RRCR4ZC28T)](https://codecov.io/gh/bernardladenthin/BitcoinAddressFinder)
<!-- =========================== Package & Release =========================== -->
[![Maven Central](https://img.shields.io/maven-central/v/net.ladenthin/bitcoinaddressfinder.svg)](https://search.maven.org/artifact/net.ladenthin/bitcoinaddressfinder)
[![Release Date](https://img.shields.io/github/release-date/bernardladenthin/BitcoinAddressFinder)]()
[![Last Commit](https://img.shields.io/github/last-commit/bernardladenthin/BitcoinAddressFinder)]()
<!-- =========================== Quality & Analysis =========================== -->
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=bernardladenthin_BitcoinAddressFinder&metric=alert_status)](https://sonarcloud.io/dashboard?id=bernardladenthin_BitcoinAddressFinder)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=bernardladenthin_BitcoinAddressFinder&metric=code_smells)](https://sonarcloud.io/dashboard?id=bernardladenthin_BitcoinAddressFinder)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=bernardladenthin_BitcoinAddressFinder&metric=security_rating)](https://sonarcloud.io/dashboard?id=bernardladenthin_BitcoinAddressFinder)
<!-- =========================== Security & Compliance =========================== -->
[![Known Vulnerabilities](https://snyk.io/test/github/bernardladenthin/BitcoinAddressFinder/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/bernardladenthin/BitcoinAddressFinder?targetFile=pom.xml)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder?ref=badge_shield)
[![Dependencies](https://img.shields.io/librariesio/github/bernardladenthin/BitcoinAddressFinder)](https://libraries.io/github/bernardladenthin/BitcoinAddressFinder)
<!-- =========================== License & Contribution =========================== -->
[![Apache 2.0 License](https://img.shields.io/badge/License-Apache%202.0-orange)](./LICENSE)
[![Contribute with Gitpod](https://img.shields.io/badge/Contribute%20with-Gitpod-908a85?logo=gitpod)](https://gitpod.io/#https://github.com/bernardladenthin/BitcoinAddressFinder)
<!-- =========================== Sustainability =========================== -->
[![Treeware](https://img.shields.io/badge/dynamic/json?color=brightgreen&label=Treeware&query=%24.total&url=https%3A%2F%2Fpublic.offset.earth%2Fusers%2Ftreeware%2Ftrees)](https://treeware.earth)
[![Stand With Ukraine](https://raw.githubusercontent.com/vshymanskyy/StandWithUkraine/main/badges/StandWithUkraine.svg)](https://stand-with-ukraine.pp.ua)
<!--
TODO:
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/1234/badge)](https://bestpractices.coreinfrastructure.org/projects/1234)
-->

---

## Table of Contents
- [About BitcoinAddressFinder](#about-bitcoinaddressfinder)
- [Requirements](#requirements)
- [Quickstart](#quickstart)
- [Features](#features)
- [Address Database](#address-database)
  - [Import](#import)
  - [Create the Database by Yourself](#create-the-database-by-yourself)
  - [Export](#export)
  - [Use My Prepared Database](#use-my-prepared-database)
    - [Light Database](#light-database)
    - [Full Database](#full-database)
- [Pages and Projects for Address Lists](#pages-and-projects-to-get-lists-dumps-of-pubkeyhash-addresses)
- [Find Addresses](#find-addresses)
  - [Mixed Modes](#mixed-modes)
  - [Key Range](#key-range)
  - [OpenCL Acceleration](#opencl-acceleration)
    - [Built-in Self-Test (BIST)](#built-in-self-test-bist)
    - [Performance Benchmarks](#performance-benchmarks)
- [Collision Probability and Security Considerations](#collision-probability-and-security-considerations)
- [Similar Projects](#similar-projects)
  - [Deep Learning Private Key Prediction](#deep-learning-private-key-prediction)
- [Known Issues](#known-issues)
  - [Hybrid Graphics Performance (Low Throughput)](#hybrid-graphics-performance-low-throughput)
- [Future Improvements](#future-improvements)
  - [KeyProvider](#keyprovider)
- [Legal](#legal)
  - [Permitted Use Cases](#️-permitted-use-cases)
  - [Prohibited Use Cases](#-prohibited-use-cases)
  - [Legal References by Jurisdiction](#legal-references-by-jurisdiction)
- [License](#license)

---

## About BitcoinAddressFinder
**BitcoinAddressFinder** is a free, high-performance tool designed to rapidly scan random private keys for a wide range of cryptocurrencies — including Bitcoin, Bitcoin Cash, Bitcoin SV, Litecoin, Dogecoin, Dash, Zcash, and many more.

Its primary goal is to generate addresses (compressed and uncompressed) as efficiently as possible by combining the power of the Java Virtual Machine (JVM) with OpenCL for hardware acceleration.

Once generated, each address is checked against a high-performance database (**LMDB**) to determine whether it has ever been used — effectively identifying addresses that might hold a balance. This includes detecting potential RIPEMD160 hash collisions and exploring known or partially known keyspaces.

Whether you're searching for vanity addresses, verifying address usage, or experimenting with cryptographic edge cases, BitcoinAddressFinder is built for speed, flexibility, and offline use.

**Made with ❤️ in Germany**

Copyright (c) 2017-2025 Bernard Ladenthin.

## Requirements
- **Java 21 or newer** is required to run BitcoinAddressFinder.  
  Older versions such as Java 8, 11, or 17 are not supported.

- **OpenCL (optional)**:  
  You can choose between CPU-only and GPU-accelerated configurations.  
  When OpenCL is enabled, elliptic curve key generation can be offloaded to one or multiple OpenCL-capable devices (e.g., GPUs), greatly increasing performance.

  Multi-GPU setups are fully supported — each GPU can be configured individually to parallelize the workload and scan more keys per second.

## Quickstart
1. Download the binary (jar) from https://github.com/bernardladenthin/BitcoinAddressFinder/releases
2. Download and extract the light database from https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database
3. Download a configuration set like:
- [`logbackConfiguration.xml`](https://github.com/bernardladenthin/BitcoinAddressFinder/blob/main/examples/logbackConfiguration.xml)
- [`config_Find_1OpenCLDevice.js`](https://github.com/bernardladenthin/BitcoinAddressFinder/blob/main/examples/config_Find_1OpenCLDevice.js)
- [`run_Find_1OpenCLDevice.bat`](https://github.com/bernardladenthin/BitcoinAddressFinder/blob/main/examples/run_Find_1OpenCLDevice.bat)
4. Put all in one directory like the following structure
  * Downloads
    * lmdb
      * data.mdb
      * lock.mdb
    * bitcoinaddressfinder-1.3.0-SNAPSHOT-jar-with-dependencies.jar
    * logbackConfiguration.xml
    * config_Find_1OpenCLDevice.js
    * run_Find_1OpenCLDevice.bat
5. Run the file run_Find_1OpenCLDevice.bat

## Features
* Supports blockchain addresses based on [secp256k1](https://en.bitcoin.it/wiki/Secp256k1).
* Unit-tested, trusted open source that can be compiled easily by yourself.
* Vanity generation of Bitcoin addresses using regex patterns.
* Runs completely offline — no internet required or used. You can run it in a bunker with a generator in the middle of nowhere, and no one will know.
* No synchronization required to run multiple instances. Random numbers are used, so no coordinated search strategy is needed — just run it on multiple machines.
* Checks a high-performance database of known addresses to detect already used ones.
* Portable, platform-independent, runs on the JVM.
* Generates both uncompressed and compressed keys simultaneously.
* EC key generation via:
  * Multiple CPU threads
  * Multiple OpenCL devices (optional)

### ⚡ ECC Scalar Multiplication Optimizations
To accelerate `k·G` (private key × base point), the OpenCL kernel uses:

- **Windowed Non-Adjacent Form (wNAF)**:
  The scalar `k` is converted to a signed digit representation using a **window size of 4**.  
  This results in digits from the set `{±1, ±3, ±5, ±7}`, with at least one zero between non-zero digits.  
  This reduces the number of costly additions during multiplication.
  [Further explanation of wNAF on crypto.stackexchange.com](https://crypto.stackexchange.com/questions/82013/simple-explanation-of-sliding-window-and-wnaf-methods-of-elliptic-curve-point-mu)

- **Precomputed Table**:
  The kernel precomputes and stores the following multiples of the base point `G`:
  - `±1·G`, `±3·G`, `±5·G`, `±7·G`  
  These are stored in the `secp256k1_t` structure and used during scalar multiplication.

- **Left-to-Right Scalar Multiplication**:
  The multiplication loop scans the wNAF digits from most to least significant:
  - Each iteration **always doubles** the current point.
  - If the current digit is non-zero, it **adds the matching precomputed point**.

- **Optimized for GPGPU (not constant-time)**:
  To prioritize speed on OpenCL/CUDA devices, this implementation is **not constant-time** and may be vulnerable to side-channel attacks in adversarial environments.

- **Use of Constant Memory**:
  Precomputed points are stored in **constant GPU memory** (`__constant` via `CONSTANT_AS`), allowing fast access by all threads in a workgroup.

### 🔄 Planned Kernel Enhancements (Preview)
The current kernel computes `k·G` for each thread by applying the thread ID (`global_id`) to the least significant bits of the private key. The resulting point is stored as affine `(x, y)` coordinates.

As a next step, I plan to:
- Add an internal loop (e.g. 16,384 iterations) within the kernel
- Each iteration performs an additional `point_add`
- This turns the kernel into a **sequential scalar walker**, enabling:
  - Grid scanning of private key ranges per thread
  - Optimized batch key generation with fewer kernel dispatches

#### 🔐 Public Key Hashing (Next Step)
I also plan to integrate **SHA-256 + RIPEMD-160** hashing directly after point multiplication to compute the public key hash:
- Apply `sha256(x || y)` followed by `ripemd160`
- Enables direct GPU-side creation of Bitcoin addresses

#### 🚀 MSB-Zero Scalar Walk Strategy

To further accelerate brute-force key scanning, I plan to combine two advanced optimizations:

- **Zeroing the 96 most significant bits (MSB) of the private key**:  
  Since `RIPEMD160(SHA256(pubkey))` produces only a 160-bit hash, I can safely fix the upper 96 bits of the 256-bit private key to zero.  
  This reduces the effective entropy to 160 bits and significantly shortens the scalar used for ECC multiplication, improving performance.

- **Sequential Scalar Walker inside the kernel**:  
  Each GPU thread computes a single `k₀·G` using full scalar multiplication (wNAF), where `k₀` has 96 MSB set to zero and variable 160-bit LSB.  
  Then, a `for` loop performs multiple `point_add` operations:
  - This effectively walks linearly through the scalar space: `k₀`, `k₀+1`, `k₀+2`, ...
  - Avoids full scalar multiplication per iteration
  - Enables high-throughput sequential key space traversal with minimal kernel invocations

This approach massively improves GPU efficiency for address collision hunting or vanity key generation.

## Address Database
The addresses are stored in a high-performance database: [LMDB](https://github.com/LMDB).
The database can be used to check whether a generated address has ever been used.

### Import
The importer reads multiple `.txt` or `.text` files containing addresses in arbitrary order. Each line can contain a different format.
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
The exporter provides various output formats for Bitcoin and altcoin address data:

* **HexHash**  
  Exports addresses as raw `hash160` (RIPEMD-160 of the SHA-256 of the public key), encoded in hexadecimal. No version byte or checksum is included. Ideal for advanced usage and low-level comparison. Best viewed in fixed-width hex viewers (e.g., HxD).

* **FixedWidthBase58BitcoinAddress**  
  Exports Base58Check-encoded addresses (e.g., legacy `P2PKH`) in a fixed-width format for consistent alignment. No amount is included. Suitable for hex/byte-aligned visual inspection and batch comparison.

* **DynamicWidthBase58BitcoinAddressWithAmount**  
  Exports Base58Check-encoded addresses along with their associated amounts (e.g., balance or UTXO value), using a dynamic-width format. Suitable for human-readable CSV-like formats and analytics.

---

### Use My Prepared Database
I am in the process of building and publishing databases containing large sets of Bitcoin and altcoin addresses.  
(Refer to the **Import** section above for details on supported address formats.)

> The sources of these addresses are confidential, but you are fully permitted to extract, inspect, and use them.

You are also welcome to **extend this database** by importing your own address data. This allows you to build upon the existing dataset, tailoring it to your specific needs (e.g., adding additional coins, formats, or private address collections).

If you're missing any information or have questions about usage or content, feel free to ask or open an issue.

#### Light database
* Light (5.12 GiB), Last update: April 5, 2025
  * Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 124362254
  * Mapsize: 5248 MiB
  * Time to create the database: ~9 hours
  * Link (3.45 GiB zip archive): http://ladenthin.net/lmdb_light.zip
  * Link extracted addresses as txt (2.15 GiB zip archive); open with HxD, set 42 bytes each line: http://ladenthin.net/LMDBToAddressFile_Light_HexHash.zip

<details>
<summary>Checksums lmdb_light.zip</summary>

* CRC32: 99EE37B0
* MD5: EAF6A6D4B7DBC64CB57804DA5BE18925
* RipeMD160: 45A0B44CF60BE91052D4A28F77570161F5B3D5B5
* SHA-1: 370FF92091FCD02731EACB4DF653BD37510C94E2
* SHA-256: F5BDF608B00223B4860C60EC3C9B85965480AC1BD22A42B8AABFB8A2544562D5
* SHA-512: 4DA942FA926B8F337565CB808EE68BCCC0508D75BFA2E054CE3D29C13264D955B6B4FA54E5F3BBFF8E87F6C1981F554F408AD1B42C94C303A79DEF241C5327B8
* SHA3-224: F824FF851747CE699CD874916FAB7853805BFE30A1C7B33E8CC7FDBD
* SHA3-256: 471658927AA2AC4EBC1331B6D3827EE796BD07C81580AA5E8C4BD7D5DE09533B
* SHA3-384: DFAD1E371F653EC3A2E7BBB033AE66B85A9A7B01000FE95699D0059B4DA625A257F2CB51801789245B77553074A426AA
* SHA3-512: 439D5B25A9D1A536118AF4D78BA8F0B6299B4E74ABC6C804D8906899D05B7ADB8129EDE357FFE3CC3854DFF57F3B27C50F50C89BB7B051771A98570041675D82

</details>

<details>
<summary>Checksums LMDBToAddressFile_Light_HexHash.zip</summary>

* CRC32: 83635100
* MD5: E0BB2FC96E4E5B82D00C0229092AF707
* RipeMD160: 97BC2D1A9192D86CC9345717E227FD6E9515AA48
* SHA-1: DB212684E50535E5FC62D31A6051763B6BF7C1A0
* SHA-256: 82A659A6DADA81B152D315E6B8E2E09054A5A09E3312A5CB372A6F3BC121F3AE
* SHA-512: C586222D11C7C1C0C95AE79D3629B69A64D7624743B0DB31A6B3DC9A99A41289F9F1CD19441124335521D22B2D095343EFC807B977D104244829D7984CF31B08
* SHA3-224: 62FEA2316CDFDA95EA0099F6138EA03B96CF79DB0A0328DB22924910
* SHA3-256: 64CFEE1C539B0824B6908F58D9AF57619F047A2B77948D455D417EC378BE6FA4
* SHA3-384: 44BB41FF36CE467776C8A2FA06CC6C4194D991ECCDD7E9C3997ED3DA2BB12D4455A2CD548AB606AFE1F2E1E59C0D4817
* SHA3-512: C9DDC04455324430072F0BD09E516F2893371493A4132004813E4C3B1C4D77620CD395745718BB255AB09970A2372D27B65EBA7BCB73F55247EB5B2376F22993

</details>


#### Full database
* Full (57.0 GiB), Last update: April 5, 2025
  * Contains all Bitcoin addresses which are ever used and many altcoin addresses with and without amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 1349299900
  * Mapsize: 58368 MiB
  * Time to create the database: ~54 hours
  * Link (33.4 GiB zip archive): http://ladenthin.net/lmdb_full.zip
  * Link extracted addresses as txt (22.8 GiB zip archive); open with HxD, set 42 bytes each line: http://ladenthin.net/LMDBToAddressFile_Full_HexHash.zip

<details>
<summary>Checksums lmdb_full.zip</summary>

* CRC32: C9AB7CE4
* MD5: FD2D9A523EFBCF24D66EAD36FC3E2944
* RipeMD160: 8BE5D07BFE4CD70032157B79B97BCA2D8434E759
* SHA-1: 5087C66A61B5F99E40B565D65A4F04429EE8A4C8
* SHA-256: 2188E73C47C963526B47BD97122084F07A23639B46C412347FB471F4127FFDD7
* SHA-512: 2989E6D7869D01AA3316E6E0A2CE0D5903604B73EF038341C695F951DEA59138660DED5CB71EDF31026E96E67CCCD64EC8D3667FB9252B3CBC3873FCDEABD4F2
* SHA3-224: 8FFA21AE451F1DB916C5F2FF45FB4B5DDBAA74AB4F69A24C8A430B8C
* SHA3-256: D0414F8B79F9F07D1143C437F7F67B286DC662AC050CF01F46CE3E1AB6FB8948
* SHA3-384: C27253D3D7E5CC7C201C8310A81EF39F5BFFE238C9021375595CFA5B4B54BB8A357E01BC1BE05F2DF35994187BFEBEA6
* SHA3-512: 33709880FC837850ACDD20B090A96708CEAA4DF0428E417EACF5A98871E9452BBD2E20F928CE0BF80B1D05FEA699A27A3268F43DC5A8360162843FF4CA2A9929

</details>

<details>
<summary>Checksums LMDBToAddressFile_Full_HexHash.zip</summary>

* CRC32: 4ECDD8A8
* MD5: 5AD4EA40A76F9EF1DCF91FFDEC771A49
* RipeMD160: 93B3E3BBB14B42473273CFD7157A546F42B0DE0A
* SHA-1: D7AEB494BCE3583722EC693252EDC35B9BBFAB46
* SHA-256: D812845159006BBA220DFEF2807F2B29423EFB81351A300BEE10DD32404F8BE8
* SHA-512: EE1E6D1E0BE3BEA625630FA090B8ECACAF265AF3532B801CBF5306540CA434B52201986DA5C56A7E8DE23AF32D9E940FCABB00239126CA1A3EBF2864D190613C
* SHA3-224: A83AEF417D4CED80D3F9DC0868C9203231A0C64AB9D0863C41D0B1BB
* SHA3-256: 8D51844A454B43DC51E231E070032910E0BAC37C46E4EBC5C00FD92BC86E488E
* SHA3-384: 10F15281AF1D660F7087DE28D527FD7592213EC1A4A102C4C9C8637FB5F53323D1D0CCAD0B8D8BC0A48DECCFFCCAC28E
* SHA3-512: 1BC9F7C7FFD6978E034D38BEA2E4860A93B819C7CA37419D659FCAB30E0166E92E2CE97F23F09BFE621DFA49548DB7F72FAC5F508ADD0151A197D96275064583

</details>

---

## Pages and projects to get lists (dumps) of PubkeyHash addresses
* https://github.com/Pymmdrza/Rich-Address-Wallet
* https://github.com/mycroft/chainstate
* https://github.com/graymauser/btcposbal2csv
* https://blockchair.com/dumps
* https://balances.crypto-nerdz.org/

## Find Addresses
> ⚠️ **Security Warning**: This software is intended for research and educational purposes. Do **not** use it in production or on systems connected to the internet.

A secure environment should be fully isolated — ideally an air-gapped computer (physically disconnected from all networks).  
The software is highly optimized for performance and **not designed to run in constant time**, which means it may be vulnerable to **side-channel attacks** on shared or exposed systems.

For generated vanity addresses or private keys, consider storing them safely using a [paper wallet](https://en.bitcoin.it/wiki/Paper_wallet).

### Mixed Modes
You can combine **vanity address generation** with **database lookups** to enhance functionality and efficiency.

For example:
- Search for personalized (vanity) addresses using custom patterns **and**
- Simultaneously check if the generated addresses already **exist in the LMDB** database

This hybrid mode allows you to find rare or meaningful addresses while ensuring they haven’t been used before.

### Key Range

You can define a custom key range for private key generation—for example, limiting the search space to 64-bit keys.  
In this setup, the first 192 bits (256-bit - 64-bit) of the key are zeroed, effectively restricting the generation to a specific portion of the keyspace.

This feature can be used for:

- **Targeted key recovery**, such as searching for private keys from known ranges like the [Bitcoin Puzzle Transaction](https://privatekeys.pw/puzzles/bitcoin-puzzle-tx)
- **Verification and testing**, to prove that the software functions correctly within a predictable key range

### OpenCL Acceleration

To significantly boost the performance of EC key generation, the software supports OpenCL-based parallelization.

A shared secret (base key) is transferred to the OpenCL device along with a predefined grid size. Each OpenCL thread independently derives a unique EC key by incrementing the base key with its thread ID. This allows the generation of a batch of EC keys in a single execution cycle. Once generated, the keys are transferred back to the host (CPU) memory for further processing.

The CPU then hashes the X and Y coordinates of the public keys to derive the corresponding (Bitcoin/Altcoin) addresses. This division of labor offloads the computationally expensive elliptic curve operations to the GPU, allowing the CPU to focus on faster address hashing and database lookup operations—resulting in improved overall throughput.

#### Built-in Self-Test (BIST)

The OpenCL backend includes a built-in self-test mechanism that cross-verifies results from the GPU against a CPU-generated reference. This ensures that the OpenCL device is functioning correctly and producing valid EC keys—giving end users confidence in the reliability of their hardware-accelerated address search.

#### Performance Benchmarks

> **Note:** OpenCL generates uncompressed keys. Compressed keys can be derived from uncompressed ones with minimal overhead.

| GPU Model                   | Key Range (Bits) | Grid Size (Bits) | Effective Keys/s       | Notes                            |
|-----------------------------|------------------|------------------|------------------------|----------------------------------|
| Nvidia RTX 2060             | 256              | 18               | 2,160,000 keys/s       |                                  |
| Nvidia Quadro P2000         | 256              | 18               | 505,000 keys/s         |                                  |
| Nvidia Quadro P2000         | 64               | 18               | >1,000,000 keys/s      | CPU bottleneck observed          |
| Nvidia Quadro M2000M        | 256              | 16               | 205,000 keys/s         |                                  |
| Nvidia GTX 1050 Ti Mobile   | 64               | 16               | >1,000,000 keys/s      | CPU bottleneck observed          |
| Nvidia GTX 1050 Ti Mobile   | 256              | 16               | 550,000 keys/s         |                                  |


## Collision Probability and Security Considerations

> Isn't it impossible to find collisions?

The likelihood of discovering a collision—two different inputs that produce the same output hash—is **astronomically low** when using secure cryptographic functions like **SHA-256** and **RIPEMD-160**, which are employed in Bitcoin address generation.

However, discussions around potential vulnerabilities, theoretical attacks, or edge cases exist in the cryptography and Bitcoin communities.

For more in-depth information on collision resistance, address reuse risks, and cryptographic hash functions, refer to the following resources:

- [How to deal with collisions in Bitcoin addresses – Crypto StackExchange](https://crypto.stackexchange.com/questions/33821/how-to-deal-with-collisions-in-bitcoin-addresses)
- [Why haven't any SHA-256 collisions been found yet? – Crypto StackExchange](https://crypto.stackexchange.com/questions/47809/why-havent-any-sha-256-collisions-been-found-yet)
- [bitcoin-wallet-finder – Results and discussion](https://github.com/treyyoder/bitcoin-wallet-finder#results)
- [PKGenerator_Checker – Instructions](https://github.com/Frankenmint/PKGenerator_Checker#instructions)
- [BitBruteForce-Wallet – Requirements and usage](https://github.com/Xefrok/BitBruteForce-Wallet#requeriments)


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
* https://github.com/johncantrell97/bip39-solver-gpu

### Deep learning private key prediction
An export of the full database can be used to predict private keys with deep learning. A funny idea: https://github.com/DRSZL/BitcoinTensorFlowPrivateKeyPrediction

## Known Issues

### Hybrid Graphics Performance (Low Throughput)

Laptops with hybrid graphics—using both integrated (iGPU) and discrete (dGPU) GPUs—may suffer from significantly reduced performance when running compute-intensive OpenCL workloads like BitcoinAddressFinder. This is often due to:

- Shared memory between CPU and GPU
- Bandwidth limitations
- Automatic GPU switching (e.g., NVIDIA Optimus, AMD Enduro)
- Suboptimal GPU selection by drivers

#### Affected Devices and Recommendations

| Manufacturer       | Affected Series/Models                          | Recommendation                           |
|--------------------|--------------------------------------------------|------------------------------------------|
| **HP**             | ZBook G3, G4, G5, G7                             | Enter BIOS → set **Graphics** to *Discrete Only* |
| **Lenovo**         | ThinkPad X, T, and P Series (hybrid configs)     | Use **Lenovo Vantage** → Disable Hybrid Graphics |
| **Dell**           | Inspiron, XPS, Precision (with Optimus)          | BIOS → Disable Hybrid Mode (if available) |
| **MSI**            | Gaming laptops with switchable graphics          | Use **Dragon Center** to select dGPU     |
| **Razer**          | Blade models with NVIDIA Optimus                 | Use **Razer Synapse** to enforce dGPU use |
| **Apple (Intel)**  | MacBook Pro (pre-2021 with dual GPUs)            | macOS → Disable *Automatic Graphics Switching* in Energy Preferences |

> If your laptop uses hybrid graphics, always ensure that the **discrete GPU** is explicitly selected for OpenCL workloads to avoid severe performance bottlenecks.

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

- Incomplete Seed-Phrase as Private KeyProvider. Wished from @mirasu See #38
- Socket KeyProvider for independend KeyProvider via byte protocol
  - Ideas might be a screen recorder and use the visible screen downscaled as 256 bit input
- KeyProvider must get the grid size to increment properly on incremental based Producer
- ExecutableKeyProvider gets data from stdout

-----


## Legal

**BitcoinAddressFinder is not intended for malicious use**, and **you are solely responsible** for complying with your local laws, international regulations, and ethical standards.

This software must **not** be configured or used to attempt unauthorized access to cryptocurrency assets (e.g., scanning for RIPEMD-160 address collisions to gain access to third-party funds).  
Such activities are likely illegal in most jurisdictions and may carry serious penalties.

---

### ✅ Permitted Use Cases

You may use BitcoinAddressFinder for legitimate and research-focused purposes, such as:

- **Recovering lost private keys** associated with your own known public addresses
- Verifying whether generated addresses have ever been used to **prevent collisions**
- Running **performance benchmarks** and OpenCL testing
- Generating **vanity addresses** for personal or demonstrative use
- Conducting **offline cryptographic research** and educational exploration

---

### 🚫 Prohibited Use Cases

You must not use this tool for:

- **Gaining unauthorized access to cryptocurrency or wallet funds**
- Circumventing access controls or exploiting systems
- Engaging in unethical behavior or violating terms of service

---

### Legal References by Jurisdiction

Below are some non-exhaustive legal frameworks that may apply depending on your region:

#### Germany

- **§ 202c StGB** – *Vorbereiten des Ausspähens und Abfangens von Daten*  
  (Preparation of spying or intercepting data)

#### United States

- **Computer Fraud and Abuse Act (CFAA)**  
  Prohibits unauthorized access to protected computers, including use of tools for circumvention

- **Digital Millennium Copyright Act (DMCA)**  
  Sections on anti-circumvention tools and reverse engineering may apply in specific contexts

#### European Union

- **General Data Protection Regulation (GDPR)**  
  While not directly related, use of personally identifiable data or address targeting must comply

- **Directive 2013/40/EU** on Attacks Against Information Systems  
  Criminalizes the creation or possession of tools designed for unauthorized access

#### Other Notable References

- **UK**: Computer Misuse Act 1990  
- **Canada**: Criminal Code Sections 342.1 & 430  
- **Australia**: Criminal Code Act 1995 – Part 10.7 – Computer Offences

---

> ⚠️ The authors and maintainers of BitcoinAddressFinder assume **no responsibility** for how the tool is used.  
> It is your duty to ensure compliance with all relevant legal and ethical standards in your country and jurisdiction.

If in doubt, consult with a legal professional before using this software for anything beyond educational or personal purposes.

## License

It is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.
Some subprojects have a different license.

This package is [Treeware](https://treeware.earth). If you use it in production, then we ask that you [**buy the world a tree**](https://plant.treeware.earth/bernardladenthin/BitcoinAddressFinder) to thank us for our work. By contributing to the Treeware forest you’ll be creating employment for local families and restoring wildlife habitats.

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fbernardladenthin%2FBitcoinAddressFinder?ref=badge_large)
