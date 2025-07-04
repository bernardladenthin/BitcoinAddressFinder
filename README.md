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

| Coin                                                                                     |  TAG  | P2PKH | P2SH | P2WPKH | P2WSH  |  P2MS  |  P2TR  |
|------------------------------------------------------------------------------------------|:-----:|:-----:|:----:|:------:|:------:|:------:|:------:|
| [42-coin](https://github.com/42-coin/42)                                                 | 42    |  ✅   | ✅   |        |        |        |       |
| [Alias](https://github.com/aliascash)                                                    | ALIAS |  ✅   |      |        |        |        |       |
| [Argoneum](https://github.com/Argoneum/argoneum)                                         | AGM   |  ✅   |      |        |        |        |       |
| [Artbyte](https://github.com/WikiMin3R/ArtBytE)                                          | ABY   |  ✅   |      |        |        |        |       |
| [Auroracoin](https://github.com/aurarad/Auroracoin)                                      | AUR   |  ✅   |      |        |        |        |       |
| [B3Coin](https://github.com/B3-Coin/B3-CoinV2)                                           | B3    |  ✅   |      |        |        |        |       |
| [BBQCoin](https://github.com/BBQCoinCommunity/BBQCoin)                                   | BQC   |  ✅   |      |        |        |        |       |
| [Bean Cash](https://github.com/teambean/BeanCash)                                        | BEAN  |  ✅   |      |        |        |        |       |
| [Biblepay](https://github.com/biblepay/biblepay)                                         | BBP   |  ✅   |      |        |        |        |       |
| [BitBay](https://github.com/bitbaymarket/bitbay-core)                                    | BAY   |  ✅   |      |        |        |        |       |
| [BitBlocks](https://github.com/BitBlocksProject/BitBlocks)                               | BBK   |  ✅   |      |        |        |        |       |
| [Bitcoin](https://github.com/bitcoin/bitcoin)                                            | BTC   |  ✅   | ✅   | ✅    | ❌     |  ❌    | ❌    |
| [Bitcoin Cash](https://github.com/bitcoincashbch/bitcoin-cash)                           | BCH   |  ✅   | ❌   |        |        |  ❌    |       |
| [Bitcoin Gold](https://github.com/btcgpu)                                                | BTG   |  ✅   |      |        |        |        |       |
| [Bitcoin Oil](https://github.com/escapeneo/bitcoinoil/)                                  | BTCO  |  ✅   |      | ✅     | ❌     |        |       |
| [Bitcoin Plus](https://github.com/bitcoinplusorg/xbcwalletsource)                        | XBC   |  ✅   |      |        |        |        |       |
| [BitCore](https://github.com/bitcore-btx/bitcore)                                        | BTX   |  ✅   |      | ✅     | ❌     |        |       |
| [Bitmark](https://github.com/project-bitmark/bitmark)                                    | BTMK  |  ✅   |      |        |        |        |       |
| [BlackCoin](https://gitlab.com/blackcoin/blackcoin-more)                                 | BLK   |  ✅   |      |        |        |        |       |
| [BlakeBitcoin](https://github.com/BlakeBitcoin/BlakeBitcoin)                             | BBTC  |  ✅   |      |        |        |        |       |
| [Blakecoin](https://github.com/BlueDragon747/Blakecoin)                                  | BLC   |  ✅   |      |        |        |        |       |
| [Blocknet](https://github.com/BlocknetDX/BlockDX)                                        | BLOCK |  ✅   |      |        |        |        |       |
| [BolivarCoin](https://github.com/BOLI-Project/BolivarCoin)                               | BOLI  |  ✅   |      |        |        |        |       |
| [BYTZ](https://github.com/bytzcurrency)                                                  | BYTZ  |  ✅   | ???  |        |        |        |       |
| [Canada-eCoin](https://github.com/Canada-eCoin/eCoinCore)                                | CDN   |  ✅   |      | ✅     | ❌      |        |         |
| [Catcoin](https://github.com/CatcoinCore/catcoincore)                                    | CAT   |  ✅   |      |        |        |        |         |
| [ChessCoin 0.32%](https://github.com/AKKPP/ChessCoin032/)                                | CHESS |  ✅   |      |        |        |        |         |
| [Clam](https://github.com/nochowderforyou/clams)                                         | CLAM  |  ✅   |      |        |        |        |         |
| [CloakCoin](https://github.com/CloakProject/CloakCoin)                                   | CLOAK |  ✅   | ✅   |        |        |        |         |
| [Coino](https://github.com/scriptRack003756/coino)                                       | CNO   |  ✅   |      |        |        |         |       |
| [ColossusXT](https://github.com/ColossusCoinXT/ColossusCoinXT)                           | COLX  |  ✅   |      |        |        |         |       |
| [Compound](https://github.com/compounddev/Compound-Coin)                                 | COMP |  ✅   |      |        |        |         |       |
| [CROWN](https://github.com/Crowndev/crowncoin)                                           | CRW   |  ✅   |      |        |        |         |       |
| [Cryptoshares](https://github.com/Cryptosharescoin/shares)                               | SHARES|  ✅   |      |        |        |         |       |
| [Curecoin](https://github.com/cygnusxi/CurecoinSource)                                   | CURE  |  ✅   |      |        |        |         |       |
| [Dash](https://github.com/dashpay/dash)                                                  | DASH  |  ✅   | ✅   |        |        |        |         |
| [DeFiChain](https://github.com/DeFiCh/ain)                                               | DFI   |  ✅   | ✅   | ✅     | ❌      |        |         |
| [Deutsche eMark](https://github.com/emarkproject/eMark)                                  | DEM   |  ✅   |      |        |        |        |         |
| [Diamond](https://github.com/DMDcoin/Diamond)                                            | DMD   |  ✅   |      |        |        |        |         |
| [DigiByte](https://github.com/digibyte-core/digibyte)                                    | DGB   |  ✅   |      | ✅     | ❌     |        |         |
| [DigitalCoin](https://github.com/lomtax/digitalcoin)                                     | DGC   |  ✅   |      |        |        |         |       |
| [Dimecoin](https://github.com/dime-coin/dimecoin)                                        | DIME  |  ✅   |      |        |        |         |       |
| [Divicoin](https://github.com/DiviProject/Divi)                                          | DIVI  |  ✅   |      |        |        |         |       |
| [Dogecoin](https://github.com/dogecoin/dogecoin)                                         | DOGE  |  ✅   | ✅   |        |        |        |         |
| [Dogmcoin](https://github.com/dogmcoin/dogmcoin)                                         | DOGM  |  ✅   |      |        |        |         |       |
| [Doichain](https://github.com/Doichain/doichain-core)                                    | DOI   |  ✅   |      | ✅     | ❌     |        |         |
| [e-Gulden](https://github.com/Electronic-Gulden-Foundation/egulden)                      | EFL   |  ✅   |      |        |        |         |       |
| [Electron](https://github.com/Electron-Coin2014/Electron-ELT)                            | ELT   |  ✅   |      |        |        |         |       |
| [Element (HYP)](https://github.com/Crypto-city/Element-HYP)                              | HYP   |  ✅   |      |        |        |         |       |
| [Elite](https://github.com/ironsniper1/Elite-Source-2.0.1.2)                             | 1337  |  ✅   |      |        |        |         |       |
| [Emerald](https://github.com/crypto-currency/Emerald)                                    | EMD   |  ✅   |      |        |        |         |       |
| [EverGreenCoin](https://github.com/EverGreenCoinDev/EverGreenCoin)                       | EGC   |  ✅   |      |        |        |         |       |
| [Feathercoin](https://github.com/FeatherCoin/Feathercoin)                                | FTC   |  ✅   |      | ✅     | ❌     |         |         |
| [Firo](https://github.com/firoorg/firo/)                                                 | FIRO  |  ✅   |      |        |       |         |         |
| [Freedomcoin](https://github.com/FreedomCoin-Project/FreedomCoin-Core/)                  | FREED |  ✅   |      |        |        |         |       |
| [GapCoin](https://github.com/gapcoin/gapcoin)                                            | GAP   |  ✅   |      |        |        |         |       |
| [Goldcash](https://github.com/iUNeIV/GoldCash)                                           | GOLD  |  ✅   |      |        |        |         |       |
| [GoldCoin](https://github.com/goldcoin/goldcoin)                                         | GLC   |  ✅   |      |        |        |         |       |
| [Groestlcoin](https://github.com/GroestlCoin/GroestlCoin/)                               | GRS   |  ✅   |      | ✅     | ❌     |         |       |
| [Hemis](https://github.com/Hemis-Blockchain/Hemis)                                       | HMS   |  ✅   |      |        |        |         |       |
| [Herencia](https://github.com/herenciacoin/HEIRS)                                        | HEIRS |  ✅   |      |        |        |         |       |
| [HoboNickels](https://github.com/Tranz5/HoboNickels)                                     | HBN   |  ✅   |      |        |        |         |       |
| [HTMLCOIN](https://github.com/HTMLCOIN)                                                  | HTML  |  ✅   |      |        |        |         |       |
| [I/O Coin](https://github.com/IOCoin/DIONS)                                              | IOC   |  ✅   |      |        |        |         |       |
| [IDChain](https://github.com/idchaincoin/idchaincoin)                                    | DCT   |  ✅   |      |        |        |         |       |
| [Innova](https://github.com/innova-foundation/innova)                                    | INN   |  ✅   |      |        |        |         |       |
| [InfiniLooP](https://github.com/WikiMin3R/InfiniLooP/)                                   | IL8P  |  ✅   |      |        |        |         |       |
| [Infinitecoin](https://github.com/infinitecoin-project/infinitecoin)                     | IFC   |  ✅   |      |        |        |         |       |
| [iXcoin](https://github.com/IXCore/IXCoin)                                               | IXC   |  ✅   |      |        |        |         |       |
| [Kobocoin](https://github.com/kobocoin/Kobocoin/)                                        | KOBO  |  ✅   |      |        |        |         |       |
| [Komodo](https://github.com/KomodoPlatform/komodo)                                       | KMD   |  ✅   |      |        |        |         |       |
| [Lanacoin](https://github.com/LanaCoin/lanacoin)                                         | LANA  |  ✅   |      |        |        |         |       |
| [Litecoin](https://github.com/litecoin-project/litecoin)                                 | LTC   |  ✅   | ✅   | ✅     | ❌     |        |         |
| [Litecoin Cash](https://github.com/litecoincash-project/litecoincash)                    | LCC   |  ✅   |      | ✅     | ❌     |         |         |
| [LiteDoge](https://github.com/ldoge/LDOGE)                                               | LDOGE |  ✅   |      |        |        |         |       |
| [Lithium](https://github.com/lithiumcoin/lithium)                                        | LIT   |  ✅   |      |        |        |         |       |
| [Luckycoin](https://github.com/LuckycoinFoundation/Luckycoin)                            | LKY   |  ✅   |      |        |        |         |       |
| [Lynx](https://github.com/getlynx/Lynx)                                                  | LYNX  |  ✅   |      |        |        |         |       |
| [MasterNoder2](https://github.com/jonK341/MasterNoder2)                                  | MN2   |  ✅   |      |        |        |         |       |
| [Mooncoin](https://github.com/%20mooncoincore/wallet/releases)                           | MOON  |  ✅   | ✅   | ✅     | ❌      |         |       |
| [MotaCoin](https://github.com/Jahvinci/MotaCoin)                                         | MOTA  |  ✅   |      |        |        |         |       |
| [Myriad](https://github.com/myriadteam/myriadcoin)                                       | XMY   |  ✅   | ✅   | ✅     | ❌     |        |         |
| [Namecoin](https://github.com/namecoin/namecoin-core)                                    | NMC   |  ✅   |      | ✅     | ❌     |         |         |
| [NewYorkCoin](https://github.com/NewYorkCoinNYC)                                         | NYC   |  ✅   |      |        |        |         |       |
| [Novacoin](https://github.com/novacoin-project/novacoin)                                 | NVC   |  ✅   |      |        |        |         |       |
| [PAC Protocol](https://github.com/pacprotocol/pacprotocol)                               | PAC   |  ✅   |      |        |        |         |       |
| [PakCoin](https://github.com/Pakcoin-project/pakcoin)                                    | PAK   |  ✅   |      |        |        |         |       |
| [PandaCoin](https://github.com/DigitalPandacoin/pandacoin)                               | PND   |  ✅   |      |        |        |         |       |
| [Particl](https://github.com/particl/particl-core)                                       | PART  |  ✅   | ✅   |        |        |         |       |
| [PeepCoin](https://github.com/PXN-Foundation/Peepcoin/)                                  | PCN   |  ✅   |      |        |        |         |       |
| [Peercoin](https://github.com/peercoin/peercoin)                                         | PPC   |  ✅   |      |        |        |         |       |
| [Photon](https://github.com/photonproject/photon)                                        | PHO   |  ✅   |      |        |        |         |       |
| [Pinkcoin](https://github.com/Pink2Dev/Pink2)                                            | PINK  |  ✅   |      |        |        |         |       |
| [PIVX](https://github.com/PIVX-Project/PIVX/)                                            | PIVX  |  ✅   |      |        |        |         |       |
| [PotCoin](https://github.com/potcoin/potcoin)                                            | POT   |  ✅   |      |        |        |         |       |
| [Primecoin](https://github.com/primecoin/primecoin)                                      | XPM   |  ✅   |      |        |        |         |       |
| [PutinCoin v2](https://github.com/PutinCoinPUT/PutinCoin)                                | PUT   |  ✅   |      |        |        |         |       |
| [Quark](https://github.com/quark-project-evolution/quark/)                               | QRK   |  ✅   |      |        |        |         |       |
| [Raptoreum Core](https://github.com/Raptor3um/raptoreum)                                 | RTM   |  ✅   |      |        |        |        |         |
| [Reddcoin](https://github.com/reddcoin-project/reddcoin)                                 | RDD   |  ✅   |      |        |        |        |         |
| [Riecoin](https://github.com/riecointeam/riecoin)                                        | RIC   |       | ✅   | ✅     | ❌    |         |       |
| [SaluS](https://github.com/saluscoin/salus)                                              | SLS   |  ✅   | ✅   |        |        |         |       |
| [SexCoin](https://github.com/sexcoin-project/sexcoin)                                    | SXC   |  ✅   |      |        |        |         |       |
| [Slimcoin](https://github.com/slimcoin-project/Slimcoin)                                 | SLM   |  ✅   |      |        |        |         |       |
| [Smileycoin](https://github.com/tutor-web/smileyCoin)                                    | SMLY  |  ✅   |      |        |        |         |       |
| [Smoke](https://github.com/cannacoin-official/cannacoin-smoke)                           | SMOKE |  ✅   |      |        |        |         |       |
| [SpaceXpanse](https://github.com/SpaceXpanse/rod-core-wallet)                            | ROD   |  ✅   |      | ✅     | ❌     |         |       |
| [Sparks](https://github.com/sparkspay/sparks/)                                           | SPK   |  ✅   |      |        |        |         |       |
| [Stakecoin](https://github.com/sleewis/stakecoin)                                        | STK   |  ✅   |      |        |        |         |       |
| [Sterlingcoin](https://github.com/Sterlingcoin/Sterlingcoin-1.6.2.0-Release)             | SLG   |  ✅   |      |        |        |         |       |
| [Stronghands](https://bitbucket.org/bumbacoin/stronghands-new)                           | SHND  |  ✅   |      |        |        |         |       |
| [Syscoin](https://github.com/syscoin/syscoin)                                            | SYS   |  ✅   |      | ✅     | ❌     |         |       |
| [TajCoin](https://github.com/Taj-Coin/tajcoin)                                           | TAJ   |  ✅   |      |        |        |         |       |
| [Terracoin](https://github.com/terracoin/terracoin)                                      | TRC   |  ✅   |      |        |        |         |       |
| [TheHolyRogerCoin](https://github.com/TheHolyRoger/TheHolyRogerCoin)                     | ROGER |  ✅   |      | ✅     | ❌     |         |       |
| [Trezarcoin](https://github.com/TrezarCoin/TrezarCoin)                                   | TZC   |  ✅   |      |        |        |         |       |
| [Trollcoin](https://github.com/TrollCoin2/TrollCoin-2.0)                                 | TROLL |  ✅   |      |        |        |         |       |
| [UFO](https://github.com/fiscalobject/ufo)                                               | UFO   |  ✅   | ✅   | ✅     | ❌      |        |         |
| [UFOhub](https://github.com/MyGCoin/UFOHub-Coin)                                         | UFOHUB|  ✅   |      |        |        |         |       |
| [Unitus](https://github.com/unitusdev/unitus)                                            | UIS   |  ✅   |      |        |        |         |       |
| [UniversalMolecule](https://github.com/universalmol/universalmol)                        | UMO   |  ✅   |      |        |        |         |       |
| [Unobtanium](https://github.com/unobtanium-official/Unobtanium)                          | UNO   |  ✅   |      |        |        |        |         |
| [Validity](https://github.com/RadiumCore/Validity)                                       | VAL   |  ✅   |      |        |        |         |       |
| [Vanillacash](https://github.com/WikiMin3R/Vanillacash)                                  | XVC   |  ✅   |      |        |        |         |       |
| [VeriCoin](https://github.com/vericoin/vericoin)                                         | VRC   |  ✅   |      |        |        |         |       |
| [Versacoin](https://github.com/versacoin/versacoin)                                      | VCN   |  ✅   |      |        |        |         |       |
| [Vertcoin](https://github.com/vertcoin/vertcoin)                                         | VTC   |  ✅   |      | ✅     | ❌     |         |       |
| [VirtaCoin](https://github.com/virtacoin/VirtaCoinProject)                               | VTA   |  ✅   |      |        |        |         |       |
| [VirtacoinPlus](https://github.com/virtacoinplus/virtacoinplus)                          | XVP   |  ✅   |      |        |        |         |       |
| [WorldCoin](https://github.com/OfficialWorldcoinGlobal/Worldcoin)                        | WDC   |  ✅   |      |        |        |         |       |
| [Zetacoin](https://github.com/WikiMin3R/ZetacoinE)                                       | ZET   |  ✅   |      |        |        |         |       |
| [ZCash](https://github.com/zcash/zcash)                                                  | ZEC   |  ✅   | ✅   |        |        |        |         |

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
* Light (5.4 GiB), Last update: July 1, 2025
  * Contains Bitcoin addresses whith amount and many altcoin addresses with amount.
  * Static amount of 0 is used to allow best compression.
  * Unique entries: 132288304
  * Mapsize: 5576 MiB
  * Time to create the database: ~9 hours
  * Link (3.66 GiB zip archive): http://ladenthin.net/lmdb_light.zip
  * Link extracted addresses as txt (5.17 GiB) (2.29 GiB zip archive); open with HxD, set 42 bytes each line: http://ladenthin.net/LMDBToAddressFile_Light_HexHash.zip

> 💡 **Hint:** When using the light database, it is **strongly recommended** to enable the following setting in your configuration:
> ```json
> "loadToMemoryCacheOnInit" : true
> ```  
> Although LMDB is very fast, a Java `HashSet` provides true **O(1)** lookups compared to **O(log n)** (or worse) with disk-backed access.
> Enabling this flag loads all addresses into memory at startup, resulting in significantly higher throughput during key scanning — especially for OpenCL or high-frequency batch operations.

<details>
<summary>Checksums lmdb_light.zip</summary>

```txt
lmdb_light.zip	CRC32	DF77E5CF
lmdb_light.zip	MD5	4B733EAFF200C8044F80BE68538BB164
lmdb_light.zip	RipeMD160	A82C31F511C9BF3CDCA705E63860B5B597227EB3
lmdb_light.zip	SHA-1	C2E9FA56578C9F73942943C7861B226C6B44AC18
lmdb_light.zip	SHA-256	3B8AB59232D9CBB67118A79143E788E0B08A957E9C3D877F8B41303005170DC0
lmdb_light.zip	SHA-512	108D88E7EE1E90BEE975ADB711438C41971B23A65751EFB33EA6287A9697DC563ECCE40452DFA2DD1E0F66637F5AA14CC02D9A621AED953E0DA6FCEDD9D9C440
lmdb_light.zip	SHA3-224	BDF86A26D072BF521AE4A909AC912AC1C0F4EEAB33D12AE4D554327F
lmdb_light.zip	SHA3-256	55206B88314AD7F2907740FBD49C462BC0B1C2F075C23807E71E095EE9712345
lmdb_light.zip	SHA3-384	D23681FC1722253F2B51BBCF9CDA22FDD2B79C33657D38C938B64392E8CD4957AAD1FD4E6D54E7B9FF1A98C3D110C52F
lmdb_light.zip	SHA3-512	096B12FE4CB1A095287F678C10B30418091FCBF1FABB77003969C6D9846F1344A0C8918B3D2D047E94363B6E9BC5324801EA0E14331A012E8123FDE44672B394
```

</details>

<details>
<summary>Checksums LMDBToAddressFile_Light_HexHash.zip</summary>

```txt
LMDBToAddressFile_Light_HexHash.zip	CRC32	AA42A096
LMDBToAddressFile_Light_HexHash.zip	MD5	EB2E19F7AFE545A1A98FAE134D5394D4
LMDBToAddressFile_Light_HexHash.zip	RipeMD160	A011232D5069884ACFBE76C8DF4069DF684768DB
LMDBToAddressFile_Light_HexHash.zip	SHA-1	C8899544133C605EAB9FE2C83227854737B8D3FC
LMDBToAddressFile_Light_HexHash.zip	SHA-256	A8B67B606C574A4F895FA7E9B5E0AA9830C5DDD7E615EB59D77C2324E388DB78
LMDBToAddressFile_Light_HexHash.zip	SHA-512	4FD1B0905016292169A23E166C0D1C4D448DADFE4EC8FE410C1991D37B6AB5FE34596FD6EDFC62C0DE8EC8AD7142212F105ACD137B9A71F5F71E1BB1FB3B9278
LMDBToAddressFile_Light_HexHash.zip	SHA3-224	858550C2805FF1A20F3728392E70CAB9E4952792F31FA0881B0031D2
LMDBToAddressFile_Light_HexHash.zip	SHA3-256	4B522E2F4225D6FB238228033CC8AAE843DC24BA00B32BCBCA0D23B9A4C1AC46
LMDBToAddressFile_Light_HexHash.zip	SHA3-384	C29158FCB83F2D4F25BC610CE4D143964AC984ACA9D2064C9B8D40F33679830B3704A99BF408DAC75702658E55F58A7D
LMDBToAddressFile_Light_HexHash.zip	SHA3-512	29CA44CD666D7B8CF9EAD4B340620FBB7EDC30F47DBC2582A57E3DFF5E537A2A2F2CE7A7CEC2C6EF21775F7B839E0D1F23BE720CE0FC64F304B142AFAFA1437E
```

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
### 🔢 Key Generation Configuration  
BitcoinAddressFinder supports multiple pseudorandom number generators (PRNGs) for private-key creation.
Pick one in your JSON via the `keyProducerJavaRandomInstance` field, e.g. `"keyProducerJavaRandomInstance": "SECURE_RANDOM"`.
This flexibility lets you switch between **production-grade entropy** and **deterministic or deliberately weak sources** for audits and research.

#### Supported PRNG Modes

| Value | Description |
|-------|-------------|
| `SECURE_RANDOM` | ✅ Cryptographically secure system CSPRNG (`/dev/urandom`, Windows CNG). **Recommended for real wallet generation.** |
| `RANDOM_CURRENT_TIME_MILLIS_SEED` | ⚠️ `java.util.Random` seeded with the current timestamp. **Insecure**; handy for replaying time-window RNG flaws. |
| `RANDOM_CUSTOM_SEED` | ⚠️ `java.util.Random` with user-supplied seed. Fully deterministic; useful for reproducible fuzzing or fixed keyspaces. |
| `SHA1_PRNG` | ⚠️ Legacy “SHA1PRNG” engine (Android pre-2013). Lets you reproduce the historic SecureRandom bug. |
| `BIP39_SEED` | ✅ HD-wallet-style derivation: mnemonic + passphrase → BIP32/BIP44 keys. Powered by bitcoinj and our `BIP39KeyProducer`. |

#### Extra fields (for `BIP39_SEED` only)

| JSON field | Type | Default | Purpose |
|------------|------|---------|---------|
| `mnemonic` | string | — | 12/24-word BIP39 sentence |
| `passphrase` | string | `""` | Optional BIP39 salt (“wallet password”) |
| `bip32Path` | string | `"M/44H/0H/0H/0"` (constant `DEFAULT_BIP32_PATH`) | Base derivation path; must start with `M/` |
| `creationTimeSeconds` | number | `0` | Epoch-seconds creation timestamp; fed to `DeterministicSeed.ofMnemonic` |

#### Examples
##### 🔐 `SECURE_RANDOM`  
Best choice for real wallet generation – uses system CSPRNG (e.g. `/dev/urandom`, Windows CNG).
```json
{
  "keyProducerJavaRandomInstance": "SECURE_RANDOM"
}
```

##### 🕰️ `RANDOM_CURRENT_TIME_MILLIS_SEED`  
Recreates time-based vulnerabilities using `java.util.Random` seeded with the current system time.
```json
{
  "keyProducerJavaRandomInstance": "RANDOM_CURRENT_TIME_MILLIS_SEED"
}
```

##### 🧪 `RANDOM_CUSTOM_SEED`
Fully deterministic PRNG using `java.util.Random` with a user-defined seed. Useful for reproducible scans and testing.

Fully deterministic output. Useful for reproducible tests or fixed keyspace scans.
Without explicit seed:
```json
{
  "keyProducerJavaRandomInstance": "RANDOM_CUSTOM_SEED"
}
```

With custom deterministic seed:
```json
{
  "keyProducerJavaRandomInstance": "RANDOM_CUSTOM_SEED",
  "customSeed": 123456789
}
```

##### ⚠️ `SHA1_PRNG`
Legacy deterministic PRNG using `"SHA1PRNG"`. Used to reproduce the 2013 Android SecureRandom vulnerability. Can be used with or without an explicit seed.

Simulates old Android bug. May produce the same keys if seeded poorly or not at all.
Without seed:
```json
{
  "keyProducerJavaRandomInstance": "SHA1_PRNG"
}
```

With custom seed:
```json
{
  "keyProducerJavaRandomInstance": "SHA1_PRNG",
  "customSeed": 987654321
}
```

##### 🔐 `BIP39_SEED`  
Hierarchical deterministic key generator using a BIP39 mnemonic and optional passphrase. Allows full BIP32/BIP44 path derivation and reproducible HD wallets.

Minimal:
```json
{
  "keyProducerJavaRandomInstance": "BIP39_SEED",
  "mnemonic": "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
}
```

Full:
```json
{
  "keyProducerJavaRandomInstance": "BIP39_SEED",
  "mnemonic": "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
  "passphrase": "correct horse battery staple",
  "bip32Path": "M/44H/0H/0H/0",
  "creationTimeSeconds": 1650000000
}
```

#### Use Cases
| Use Case                        | Recommended PRNG                                |
|---------------------------------|-------------------------------------------------|
| 🔐 Secure wallet generation     | `SECURE_RANDOM`, `BIP39_SEED`                   |
| 🧪 Testing deterministic output | `RANDOM_CUSTOM_SEED`                            |
| 🕵️ Simulating vulnerabilities   | `RANDOM_CURRENT_TIME_MILLIS_SEED`               |
| 🔄 Reproducible scans           | `SHA1_PRNG`, `RANDOM_CUSTOM_SEED`, `BIP39_SEED` |

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

### ⚠️ Security Advisory: Android RNG Vulnerability (2013) and Simulation via BitcoinAddressFinder
In 2013, a serious vulnerability was discovered in Android’s `SecureRandom` implementation. It caused Bitcoin private keys to be exposed due to reused or predictable random values during ECDSA signature creation. This problem affected many wallet apps that generated keys directly on Android devices.

BitcoinAddressFinder can be used to simulate and analyze this type of attack. With small changes, it can reproduce faulty random number generators by:
* using fixed or repeating `k` values
* limiting entropy to 16, 32, or 64 bits
* replacing the secure RNG with a weak or deterministic version

This makes it possible to test and study:
* how r-collisions happen in ECDSA signatures
* how easy it is to find reused `k` values
* how quickly a private key can be recovered
* how secure different RNG implementations really are

BitcoinAddressFinder can generate millions of key pairs quickly. This allows researchers to create and scan large keyspaces under controlled RNG conditions. All results can be verified using the built-in self-test feature or compared against known addresses in the LMDB database.

This kind of simulation is useful for:
* learning about signature security
* building training examples for audits or courses
* checking RNG quality in real wallets or custom apps

References:
* [Wikipedia: RNG Attack](https://en.wikipedia.org/wiki/Random_number_generator_attack)
* [Crypto.SE: Android SecureRandom + r-Collisions](https://crypto.stackexchange.com/questions/9694/technical-details-of-attack-on-android-bitcoin-usage-of-securerandom)
* [The Register: Android Bug Batters Wallets](https://www.theregister.com/2013/08/12/android_bug_batters_bitcoin_wallets/)
* [Google Blog: SecureRandom Fixes](https://android-developers.googleblog.com/2013/08/some-securerandom-thoughts.html)
* [bitcoin.org Alert (2013-08-11)](https://bitcoin.org/en/alert/2013-08-11-android)

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
