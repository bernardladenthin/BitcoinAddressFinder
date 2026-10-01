#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Warn about Maven dependencies and plugins whose version differs between this repository and its
siblings. See buildcheck/versions.py. Usage: check-versions.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from buildcheck import versions  # noqa: E402

if __name__ == "__main__":
    sys.exit(versions.main(sys.argv, os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
