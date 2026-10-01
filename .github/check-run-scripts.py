#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Fail when a bash run script of a workflow or composite action does not parse (bash -n).
See buildcheck/runscripts.py. Usage: check-run-scripts.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from buildcheck import runscripts  # noqa: E402

if __name__ == "__main__":
    sys.exit(runscripts.main(sys.argv, os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
