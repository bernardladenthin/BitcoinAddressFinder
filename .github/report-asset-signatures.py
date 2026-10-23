#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Report signature gaps without blocking uploads; assert the missing output afterwards."""

import argparse
import os

from buildcheck.releaseassets import signature_gaps


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory")
    args = parser.parse_args()
    try:
        missing, errors = signature_gaps(args.directory)
    except OSError as error:
        missing, errors = -1, [str(error)]
    for error in errors:
        print(f"::error::{error}")
    if not missing:
        print("All collected artifacts have nonempty detached signatures.")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write(f"missing={missing}\n")
    # The workflow uploads first and fails on the output afterwards.
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
