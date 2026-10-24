#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Usage: update-snapshot.py <assets-directory>; requires the serialized github-snapshot job."""

import argparse
import os
import subprocess
import sys

from buildcheck.snapshot import publish


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory")
    parser.add_argument("--tag", default="snapshot")
    parser.add_argument("--title", default="Snapshot (latest)")
    args = parser.parse_args()
    try:
        publish(os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"],
                os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"], args.directory,
                os.environ.get("ASSETS_COMPLETE") == "true", tag=args.tag, title=args.title)
        return 0
    except (ValueError, OSError, RuntimeError, KeyError, subprocess.CalledProcessError) as error:
        print(f"::error::{error}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
