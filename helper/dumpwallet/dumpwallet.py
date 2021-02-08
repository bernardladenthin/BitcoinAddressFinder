#!/usr/bin/python3
#
# Author......: Bernard Ladenthin, 2020
# License.....: MIT
#
import sys
import struct
from bsddb3.db import *
from bitcoinaddress import Wallet

# Dumps the private keys from an unencrypted wallet.dat file.
# Idea from https://gist.github.com/SopaXorzTaker/e5256e9ecdce740f182093f72f05b8d2
# and https://github.com/darkwallet/python-obelisk/blob/master/obelisk/bitcoin.py
# download bsddb3 from https://www.lfd.uci.edu/~gohlke/pythonlibs/#bsddb3
# pip install bsddb3-6.2.9-cp39-cp39-win_amd64.whl
# pip install bitcoinaddress
# Run with Python 3.9.1

# asn.1
# see https://brainwalletx.github.io/
part1Compressed = "3081d30201010420"
part1Uncompressed = "308201130201010420"
part3 = "a08185308182020101302c06072a8648ce3d0101022100"

if not len(sys.argv) == 2:
    print("Usage: %s <wallet_file>" % sys.argv[0])
    sys.exit(1)

db = DB()
db.open(sys.argv[1], "main", DB_BTREE, DB_RDONLY)

items = db.items()

def getBetween(start, end, s):
    return (s.split(start))[1].split(end)[0]

for item in items:
    k, v = item
    
    vAsHexStr = v.hex()
    if (part1Compressed in vAsHexStr):
        between = getBetween(part1Compressed, part3, vAsHexStr)
        wallet = Wallet(between)
        print(wallet.key.mainnet.wif)
        print(wallet.key.mainnet.wifc)
        
    if (part1Uncompressed in vAsHexStr):
        between = getBetween(part1Uncompressed, part3, vAsHexStr)
        wallet = Wallet(between)
        print(wallet.key.mainnet.wif)
        print(wallet.key.mainnet.wifc)

db.close()
