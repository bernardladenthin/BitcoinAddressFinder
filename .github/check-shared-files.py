#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Check the files this repository shares byte-identically with its sibling repositories.

See buildcheck/sharedfiles.py; the list is .github/shared-files.sha256.
Usage: check-shared-files.py            verify the local copies, compare with the siblings (warnings)
       check-shared-files.py --offline  verify the local copies only
       check-shared-files.py --write    recompute the hashes of the listed files after a sync
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from buildcheck import sharedfiles  # noqa: E402

if __name__ == "__main__":
    sys.exit(sharedfiles.main(sys.argv, os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
