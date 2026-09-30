# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Build checks as a library, so buildcheck/tests can test them. Standard library only.

Two kinds of module live here. workflow.py, releasegate.py and sharedfiles.py (with their tests
and the check-*.py entry points next to this package) are kept BYTE-IDENTICAL in java-llama.cpp,
BitcoinAddressFinder, srcmorph and streambuffer: each repository lists them in
.github/shared-files.sha256, which its `shared-files` job checks (see sharedfiles.py). Every other
module is the repository's own.

Run the tests from the repository root:
  python3 -m unittest discover -s .github/buildcheck/tests -t .github
"""
