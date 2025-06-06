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
**BitcoinAddressFinder** is a free, high-performance tool for scanning random private keys across a wide range of cryptocurrencies — including Bitcoin, Bitcoin Cash, Bitcoin SV, Litecoin, Dogecoin, Dash, Zcash, and many more.

Its core purpose is to generate both **compressed** and **uncompressed** addresses with maximum efficiency, combining the portability of the **Java Virtual Machine (JVM)** with **OpenCL-powered GPU acceleration**.

Each generated address is checked against a high-speed **LMDB** database to detect whether it has ever been used — identifying possible balances, known keyspaces, and even **RIPEMD160 hash collisions**.

🔍 Whether you're generating vanity addresses, verifying address usage, or experimenting with cryptographic edge cases, **BitcoinAddressFinder** is built for **speed**, **flexibility**, and **fully offline operation**.

> 🔐 Runs air-gapped · ⚡ GPU-accelerated · 🧪 Unit-tested · 🛠️ Extensible

**Made with ❤️ in Germany** 
 
Copyright (c) 2017-2025 Bernard Ladenthin

## Requirements
- **Java 21 or newer** is required to run BitcoinAddressFinder.  
  Older versions such as Java 8, 11, or 17 are not supported.

- **🚀 OpenCL (optional)**:  
  You can run this software in either **CPU-only** mode or with **GPU acceleration via OpenCL** for significantly enhanced performance.

  When OpenCL is enabled:
  - **Elliptic Curve Key Generation** is offloaded to one or more OpenCL-capable devices (e.g., GPUs), dramatically boosting key scanning throughput.
  - **SHA-256** and **RIPEMD-160** hashing operations are also offloaded to the GPU, further reducing CPU load and increasing overall efficiency.
  - **Multi-GPU setups are fully supported** — each device can be configured individually, enabling efficient parallelization and scalability across multiple GPUs.

## Quickstart
1. Download the binary (jar) from https://github.com/bernardladenthin/BitcoinAddressFinder/releases
2. Download and extract the light database from https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database
3. Download a configuration set like:
- [`logbackConfiguration.xml`](https://github.com/bernardladenthin/BitcoinAddressFinder/blob/main/examples/logbackConfiguration.xml)
- [`config_Find_1OpenCLDevice.json`](https://github.com/bernardladenthin/BitcoinAddressFinder/blob/main/examples/config_Find_1OpenCLDevice.json)
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

## ✨ Features
* 📐 Supports blockchain addresses based on [secp256k1](https://en.bitcoin.it/wiki/Secp256k1)
* 🛡️ Unit-tested, trusted open source that can be compiled easily by yourself
* 🎯 Vanity generation of Bitcoin addresses using regex patterns
* 🔌 Runs entirely offline — no internet connection is required or used. Suitable for air-gapped systems and isolated environments — even in a bunker with a generator and zero connectivity.
* 🤹 No synchronization required to run multiple instances. Random numbers are used, so no coordinated search strategy is needed — just run it on multiple machines
* ⚡ Checks a high-performance database of known addresses to detect already used ones
* 📦 Portable, platform-independent, runs on the JVM
* 🔁 Generates both uncompressed and compressed keys simultaneously
* 🧮 EC key generation via:
  * 🧵 Multiple CPU threads
  * 🖥️ Multiple OpenCL devices (optional)

### ⚡ ECC Scalar Multiplication Optimizations
To accelerate **elliptic curve scalar multiplication** (`k·G`, i.e. private key × base point), the OpenCL kernel applies the following optimizations:

- **Windowed Non-Adjacent Form (wNAF)**:  
  The scalar `k` is converted to a signed digit representation using a **window size of 4**.  
  This results in digits from the set `{±1, ±3, ±5, ±7}`, with at least one zero between non-zero digits.  
  This reduces the number of costly additions during multiplication.  
  [Further explanation of wNAF on crypto.stackexchange.com](https://crypto.stackexchange.com/questions/82013/simple-explanation-of-sliding-window-and-wnaf-methods-of-elliptic-curve-point-mu)

- **Precomputed Table**:  
  The kernel precomputes and stores the following multiples of the base point `G`:  
  `±1·G`, `±3·G`, `±5·G`, `±7·G`  
  These are stored in the `secp256k1_t` structure and reused during scalar multiplication.

- **Left-to-Right Scalar Multiplication**:  
  The multiplication loop scans the wNAF digits from most to least significant:  
  - Each iteration **always doubles** the current point.  
  - If the current digit is non-zero, it **adds the matching precomputed point**.

- **Optimized for GPGPU (not constant-time)**:  
  To prioritize speed on OpenCL/CUDA devices, this implementation is **not constant-time** and may be vulnerable to side-channel attacks in adversarial environments.

- **Use of Constant Memory**:  
  Precomputed points are stored in **constant GPU memory** (`__constant` via `CONSTANT_AS`), allowing fast access by all threads in a workgroup.

### 🔄 Scalar Walker per Kernel (`loopCount`)
The OpenCL kernel supports a **loop-based scalar strategy** controlled by the `loopCount` parameter. Each GPU thread generates multiple EC keys by:

- Computing the first key via full scalar multiplication: `P₀ = k₀·G` (using `point_mul_xy`)  
- Computing subsequent keys via efficient affine additions: `Pₙ₊₁ = Pₙ + G` (using `point_add_xy`)

This enables high-throughput, grid-parallel **linear keyspace traversal** like:  
`k₀·G, (k₀ + 1)·G, ..., (k₀ + loopCount - 1)·G`

Example:  
- If `loopCount = 8`, each thread generates 8 keys  
- Grid size is reduced by a factor of 8  
- All results are written to global memory

> ✅ Lower `loopCount` values like 4 or 8 are often ideal.  
> ❌ Higher values may reduce GPU occupancy due to fewer active threads.

### 🚀 MSB-Zero Optimization
To accelerate elliptic curve multiplication, BitcoinAddressFinder applies a **160-bit private key optimization**:

- The upper 96 bits of each 256-bit private key are set to zero  
- Only the lower 160 bits are randomized and traversed

This reduction in scalar size speeds up `k·G` computations, resulting in significantly better performance during brute-force and batch-based key scanning.

> ✅ Matches the size of `RIPEMD160(SHA256(pubkey))`  
> ✅ Especially effective when combined with OpenCL acceleration

### 🚀 In-Memory Address Cache (`loadToMemoryCacheOnInit`)
To improve address lookup performance during high-speed key generation, you can enable the `loadToMemoryCacheOnInit` option.  
This feature loads all LMDB entries into a Java `HashSet` at startup, allowing ultra-fast `O(1)` address checks.

This is especially useful in OpenCL or batch scenarios where thousands or millions of addresses are checked per second and LMDB access becomes a bottleneck.

> ✅ Recommended when system RAM is sufficient to hold all known addresses  
> ❌ Avoid on memory-constrained systems or with extremely large databases

### 🔐 Public Key Hashing on GPU (SHA-256 + RIPEMD-160)
The OpenCL kernel performs **blazing fast public key hashing** directly on the GPU using:

- `SHA-256(pubkey.x || pubkey.y)` followed by  
- `RIPEMD-160(SHA-256(pubkey))`

This allows each GPU thread to independently generate full Bitcoin-style public key hashes (`hash160`) **without CPU involvement**.

Benefits:  
- No host-side post-processing needed  
- Fully parallelized and memory-efficient  
- Ideal for massive batch generation and filtering

> ✅ Output is already in hash160 format, ready for address comparison

## Address Database
The addresses are stored in a high-performance database: [LMDB](https://github.com/LMDB).
The database can be used to check whether a generated address has ever been used.

### Address Import Support

The importer supports reading multiple `.txt` or `.text` files, each containing one address per line in arbitrary order. Lines may vary in address type and format. Each line may also optionally include an associated coin amount.

⚠️ **Unsupported or malformed lines are silently skipped during import.**

#### Supported Address Types

* **P2PKH** – Pay to Public Key Hash  
  Encoded using Base58. Most commonly used format for legacy Bitcoin addresses.

* **P2SH** – Pay to Script Hash  
  Base58-encoded address type used for multisig and other script-based spending conditions.

* **P2MS** – Pay to Multisig  
  Custom format prefixed with `d-`, `m-`, or `s-` (e.g. `m-<script>`).

* **P2WPKH** – Pay to Witness Public Key Hash  
  Native SegWit v0 address, encoded in **Bech32**.

* **P2WSH** – Pay to Witness Script Hash  
  Native SegWit v0 address for scripts, encoded in **Bech32**.

* **P2TR** – Pay to Taproot  
  Native SegWit v1 (Taproot) address, encoded using **Bech32m**.

> **Note:**  
> Bech32 was introduced in [BIP-173](https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki) for SegWit v0 (P2WPKH, P2WSH).  
> Bech32m, defined in [BIP-350](https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki), is used for SegWit v1 (P2TR).

#### Special Formats

In addition to standard script types, the importer also recognizes and supports several special address encodings:

* **Bitcoin Cash (Base58 prefix `q`)**  
  Legacy Bitcoin Cash addresses using the `q`-prefix are automatically converted to legacy Bitcoin format.

* **BitCore WKH**  
  Custom format prefixed with `wkh_` and encoded in **Base36**.

* **Riecoin P2SH as ScriptPubKey**  
  Riecoin P2SH addresses are provided as raw ScriptPubKey hex (starting with `76a914...`).

---

> **Legend:**  
> ✅ = Supported and tested  
> ❌ = Explicitly not supported (format known but intentionally excluded)  
> _empty_ = Unknown or unverified — either not implemented by the altcoin project, or no valid address example was found (thus no implementation or test exists)

| Coin              | P2PKH | P2SH | P2WPKH | P2WSH  |  P2MS  |  P2TR  |
|-------------------|:-----:|:----:|:------:|:------:|:------:|:------:|
| 42-coin           |  ✅   | ✅   |        |        |        |       |
| Artbyte           |  ✅   |      |        |        |        |       |
| Alias             |  ✅   |      |        |        |        |       |
| Auroracoin        |  ✅   |      |        |        |        |       |
| BitBlocks         |  ✅   |      |        |        |        |       |
| Bitcoin           |  ✅   | ✅   | ✅    | ❌     |  ❌    | ❌    |
| Bitcoin Cash      |  ✅   | ❌   |        |        |  ❌    |       |
| Bitcoin Gold      |  ✅   |      |        |        |        |       |
| Bitcoin Oil       |  ✅   |      | ✅     | ❌     |        |       |
| Bitcoin Plus      |  ✅   |      |        |        |        |       |
| BitCore           |  ✅   |      | ✅     | ❌     |        |       |
| Bitmark           |  ✅   |      |        |        |        |       |
| Blackcoin         |  ✅   |      |        |        |        |       |
| Blocknet          |  ✅   |      |        |        |        |       |
| BolivarCoin       |  ✅   |      |        |        |        |       |
| BYTZ              |  ✅   |      |        |        |        |       |
| Canada-eCoin      |  ✅   |      | ✅     | ❌      |        |         |
| Catcoin           |  ✅   |      |        |        |        |         |
| ChessCoin         |  ✅   |      |        |        |        |         |
| Clam              |  ✅   |      |        |        |        |         |
| CloakCoin         |  ✅   | ✅   |        |        |        |         |
| CROWN             |  ✅   |      |        |        |         |       |
| Coino             |  ✅   |      |        |        |         |       |
| ColossusXT        |  ✅   |      |        |        |         |       |
| Curecoin          |  ✅   |      |        |        |         |       |
| Dash              |  ✅   | ✅   |        |        |        |         |
| DeFiChain         |  ✅   | ✅   | ✅     | ❌      |        |         |
| Deutsche eMark    |  ✅   |      |        |        |        |         |
| Diamond           |  ✅   |      |        |        |        |         |
| DigiByte          |  ✅   |      | ✅     | ❌     |        |         |
| DigitalCoin       |  ✅   |      |        |        |         |       |
| Dimecoin          |  ✅   |      |        |        |         |       |
| Divicoin          |  ✅   |      |        |        |         |       |
| Dogecoin          |  ✅   | ✅   |        |        |        |         |
| Dogecoin multisig |  ✅   | ✅   |        |        |        |         |
| Doichain          |  ✅   |      | ✅     | ❌     |        |         |
| e-Gulden          |  ✅   |      |        |        |         |       |
| Electron          |  ✅   |      |        |        |         |       |
| Element           |  ✅   |      |        |        |         |       |
| Elite             |  ✅   |      |        |        |         |       |
| Emerald           |  ✅   |      |        |        |         |       |
| Feathercoin       |  ✅   |      | ✅     | ❌     |         |         |
| Firo              |  ✅   |      |        |       |         |         |
| Goldcash          |  ✅   |      |        |        |         |       |
| Groestlcoin       |  ✅   |      | ✅     | ❌     |         |       |
| Herencia          |  ✅   |      |        |        |         |       |
| I/O Coin          |  ✅   |      |        |        |         |       |
| Innova            |  ✅   |      |        |        |         |       |
| InfiniLooP        |  ✅   |      |        |        |         |       |
| Infinitecoin      |  ✅   |      |        |        |         |       |
| iXcoin            |  ✅   |      |        |        |         |       |
| Komodo            |  ✅   |      |        |        |         |       |
| Lanacoin          |  ✅   |      |        |        |         |       |
| Litecoin          |  ✅   | ✅   | ✅     | ❌     |        |         |
| Litecoin Cash     |  ✅   |      | ✅     | ❌     |         |         |
| LiteDoge          |  ✅   |      |        |        |         |       |
| Luckycoin         |  ✅   |      |        |        |         |       |
| Lynx              |  ✅   |      |        |        |         |       |
| MasterNoder2      |  ✅   |      |        |        |         |       |
| Mooncoin          |  ✅   | ✅   | ✅     | ❌      |         |       |
| Myriad            |  ✅   | ✅   | ✅     | ❌     |        |         |
| Namecoin          |  ✅   |      | ✅     | ❌     |         |         |
| NewYorkCoin       |  ✅   |      |        |        |         |       |
| Novacoin          |  ✅   |      |        |        |         |       |
| PAC Protocol      |  ✅   |      |        |        |         |       |
| PakCoin           |  ✅   |      |        |        |         |       |
| PandaCoin         |  ✅   |      |        |        |         |       |
| Particl           |  ✅   | ✅   |        |        |         |       |
| Peercoin          |  ✅   |      |        |        |         |       |
| Pinkcoin          |  ✅   |      |        |        |         |       |
| Primecoin         |  ✅   |      |        |        |         |       |
| PotCoin           |  ✅   |      |        |        |         |       |
| PutinCoin v2      |  ✅   |      |        |        |         |       |
| PIVX              |  ✅   |      |        |        |         |       |
| Quark             |  ✅   |      |        |        |         |       |
| Raptoreum         |  ✅   |      |        |        |        |         |
| Reddcoin          |  ✅   |      |        |        |        |         |
| Riecoin           |       | ✅   | ✅     | ❌    |         |       |
| SaluS             |  ✅   | ✅   |        |        |         |       |
| Smileycoin        |  ✅   |      |        |        |         |       |
| SpaceXpanse       |  ✅   |      | ✅     | ❌     |         |       |
| Sterlingcoin      |  ✅   |      |        |        |         |       |
| Syscoin           |  ✅   |      | ✅     | ❌     |         |       |
| Terracoin         |  ✅   |      |        |        |         |       |
| TheHolyRogerCoin  | ✅    |      | ✅     | ❌     |         |       |
| Trezarcoin        |  ✅   |      |        |        |         |       |
| UFO               |  ✅   | ✅   | ✅     | ❌      |        |         |
| Unobtanium        |  ✅   |      |        |        |        |         |
| Validity          |  ✅   |      |        |        |         |       |
| Vanillacash       |  ✅   |      |        |        |         |       |
| VeriCoin          |  ✅   |      |        |        |         |       |
| Versacoin         |  ✅   |      |        |        |         |       |
| Vertcoin          |  ✅   |      | ✅     | ❌     |         |       |
| WorldCoin         |  ✅   |      |        |        |         |       |
| ZCash             |  ✅   | ✅   |        |        |        |         |

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
* Light (5.23 GiB), Last update: June 1, 2025
  * Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 127367117
  * Mapsize: 5368 MiB
  * Time to create the database: ~9 hours
  * Link (3.53 GiB zip archive): http://ladenthin.net/lmdb_light.zip
  * Link extracted addresses as txt (4.98 GiB) (2.20 GiB zip archive); open with HxD, set 42 bytes each line: http://ladenthin.net/LMDBToAddressFile_Light_HexHash.zip

> 💡 **Hint:** When using the light database, it is **strongly recommended** to enable the following setting in your configuration:
> ```json
> "loadToMemoryCacheOnInit" : true
> ```  
> Although LMDB is very fast, a Java `HashSet` provides true **O(1)** lookups compared to **O(log n)** (or worse) with disk-backed access.
> Enabling this flag loads all addresses into memory at startup, resulting in significantly higher throughput during key scanning — especially for OpenCL or high-frequency batch operations.

<details>
<summary>Checksums lmdb_light.zip</summary>

* CRC32: D2AF2164
* MD5: 91F13598C131BEC3457CD0BC0FFACC6E
* RipeMD160: 7958D3303D010FF12F0D13E431A5BADEE8E0462B
* SHA-1: 50596349A20644514A47098B296B72315B644FE3
* SHA-256: 60F40818E1F5437DBA901DE4F07D738CBF777F97E13717E7EFCB78D68EAA8FF4
* SHA-512: CB2ACC74EFC9E7D72E7ABA0BA5972989D15D59048D8C4E0D6BAD1B675A12A9036D73AEF457283C3F55FC227D630C68762A3803133BEB4C2069AAC06AB149B534
* SHA3-224: 19434D79D59B6EB89341B6035C074A54AB26C0647D39962FF754474E
* SHA3-256: 987F19BE7EA27EA764485E67B165ACEDD01B4AE534190FA7F9DC35CF9F8F3239
* SHA3-384: DD9BAC7346D18C90B862086675A55015CB1B4D0FA7B1637114CAD9338DAAF98F2A4027EDD4A9AA0FA876CA0AC79D40F3
* SHA3-512: B68ABD037C599F4A884E85B0FE255A57D1037641D9552F7C63D604C63403DBF2A9881B5A1BA0907FEE54808672EF6FDDD0B6EFEB71E929FD87B69E29CFFFAAC6

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
* Full (57.0 GiB), Last update: June 4, 2025
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
> 💡 **Hint:** To enable the built-in self-test, make sure the following configuration flag is set to `true`:  
> ```json
> "runtimePublicKeyCalculationCheck": true
> ```  
> This option is disabled by default. Enabling it is especially useful during development, debugging, or when validating new hardware setups.

#### Performance Benchmarks

> **Note:** OpenCL generates uncompressed keys. Compressed keys can be derived from uncompressed ones with minimal overhead.

| GPU Model                   | CPU                 | Key Range (Bits) | Grid Size (Bits) | Effective Keys/s (~)   |
|-----------------------------|---------------------|------------------|------------------|------------------------|
| AMD Radeon RX 7900 XTX      | AMD Ryzen 7 9800X3D | 160              | 19               | 15,000,000 keys/s      |
| AMD Radeon RX 7900 XTX      | AMD Ryzen 7 9800X3D | 256              | 19               | 11,000,000 keys/s      |
| NVIDIA RTX 3070 Laptop      | AMD Ryzen 7 5800H   | 160              | 19               |  6,000,000 keys/s      |
| NVIDIA RTX 3070 Laptop      | AMD Ryzen 7 5800H   | 256              | 19               |  4,000,000 keys/s      |
| NVIDIA RTX 3090             | AMD Ryzen 9 3950X   | 160              | 19               | 11,000,000 keys/s      |
| NVIDIA RTX 3090             | AMD Ryzen 9 3950X   | 256              | 19               |  8,000,000 keys/s      |
| NVIDIA RTX A3000            | Intel i7-11850H     | 256              | 19               |  3,000,000 keys/s      |
| NVIDIA RTX A3000            | Intel i7-11850H     | 160              | 19               |  5,000,000 keys/s      |


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
- [New Records in Collision Attacks on RIPEMD-160 and SHA-256 (ePrint 2023/285)](https://eprint.iacr.org/2023/285) – Li et al. present new records in collision attacks: 40-step RIPEMD-160 and 39-step semi-free-start SHA-256. Both hash functions are fundamental to Bitcoin address generation.


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

## Learn more
* https://learnmeabitcoin.com/technical/keys/public-key/

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
