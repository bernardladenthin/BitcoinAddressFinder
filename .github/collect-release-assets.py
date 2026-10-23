#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Collect assets using the repository's explicit release-assets.json contract."""

import argparse
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from buildcheck.releaseassets import collect, file_classifiers


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", nargs="?")
    parser.add_argument("--config", help="JSON contract (default: .github/release-assets.json)")
    parser.add_argument("--maven-only", action="store_true", help="stage signed Maven files before a rebuild")
    parser.add_argument("--maven-assets", help="read Maven files from a previous staging directory")
    parser.add_argument("--list-classifiers", type=int, metavar="RULE", help="print classifiers of a file rule (zero based)")
    args = parser.parse_args()
    if args.list_classifiers is None and args.destination is None:
        parser.error("destination is required unless --list-classifiers is used")
    errors = []
    try:
        root = Path(__file__).resolve().parent.parent
        if args.list_classifiers is not None:
            if args.list_classifiers < 0:
                raise ValueError("file rule index must not be negative")
            print("\n".join(file_classifiers(root, args.list_classifiers, args.config)))
            return 0
        errors = collect(root, args.destination, args.config,
                         maven_only=args.maven_only, maven_assets=args.maven_assets)
    except (ValueError, OSError, ET.ParseError) as error:
        errors.append(str(error))
    for error in errors:
        print(f"::error::{error}")
    if not errors:
        print(f"Collected {len(list(Path(args.destination).iterdir()))} release files including signatures.")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write(f"complete={'false' if errors else 'true'}\n")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
