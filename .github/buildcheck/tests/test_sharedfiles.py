# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

import hashlib
import io
import os
import tempfile
import unittest

from buildcheck import sharedfiles
from buildcheck.tests.helpers import REPO

A = hashlib.sha256(b"a").hexdigest()
B = hashlib.sha256(b"b").hexdigest()


class ParseTest(unittest.TestCase):

    def test_reads_sha256sum_lines_and_skips_comments(self):
        text = f"# header\n\n{A}  x/one.sh\n{B} *two.py\n"
        self.assertEqual(sharedfiles.parse(text), {"x/one.sh": A, "two.py": B})

    def test_rejects_malformed_and_duplicate_lines(self):
        for text in ("nonsense\n", f"{A[:-1]}  x\n", f"{A}  x\n{B}  x\n"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                sharedfiles.parse(text)


class TreeTest(unittest.TestCase):

    def setUp(self):
        self.root = tempfile.mkdtemp()
        os.makedirs(os.path.join(self.root, ".github"))
        with open(os.path.join(self.root, "one.sh"), "wb") as f:
            f.write(b"a")

    def manifest(self, text):
        with open(os.path.join(self.root, sharedfiles.MANIFEST), "w", encoding="utf-8") as f:
            f.write(text)

    def run_main(self, *args, others=None):
        out, err = io.StringIO(), io.StringIO()
        others = others or {}

        def fetcher(repo):
            if repo not in others:
                raise OSError("unreachable")
            return others[repo]
        code = sharedfiles.main(["x", *args], self.root, fetcher, out, err)
        return code, err.getvalue()

    def test_an_unchanged_file_passes(self):
        self.manifest(f"# h\n{A}  one.sh\n")
        self.assertEqual(self.run_main("--offline"), (0, ""))

    def test_a_file_changed_alone_or_missing_fails(self):
        self.manifest(f"{B}  one.sh\n{A}  gone.sh\n")
        code, err = self.run_main("--offline")
        self.assertEqual(code, 1)
        self.assertIn("one.sh differs", err)
        self.assertIn("gone.sh is listed", err)

    def test_write_recomputes_the_hashes_and_keeps_the_comments(self):
        self.manifest(f"# keep me\n{B}  one.sh\n")
        self.assertEqual(self.run_main("--write")[0], 0)
        with open(os.path.join(self.root, sharedfiles.MANIFEST), encoding="utf-8") as f:
            self.assertEqual(f.read(), f"# keep me\n{A}  one.sh\n")

    def test_sibling_differences_are_warnings_not_failures(self):
        self.manifest(f"{A}  one.sh\n")
        others = {"srcmorph": f"{B}  one.sh\n", "streambuffer": f"{A}  one.sh\n"}
        code, err = self.run_main(others=others)
        self.assertEqual(code, 0)
        self.assertIn("::warning::one.sh differs from srcmorph's one.sh", err)
        self.assertNotIn("streambuffer's", err)
        self.assertIn("could not read BitcoinAddressFinder", err)

    def test_unknown_arguments(self):
        self.manifest("")
        self.assertEqual(self.run_main("--nope")[0], 2)


class CompareTest(unittest.TestCase):

    def test_matches_by_path_then_by_a_unique_file_name(self):
        own = {"llama/lombok.config": A, "a/__init__.py": A}
        self.assertEqual(sharedfiles.compare(own, {"r": {"lombok.config": B}}),
                         ["llama/lombok.config differs from r's lombok.config: finish or redo the sync"])
        # two candidates of the same name: no guess
        self.assertEqual(sharedfiles.compare(own, {"r": {"b/__init__.py": B, "c/__init__.py": B}}), [])
        # a file the other repository does not share is no difference
        self.assertEqual(sharedfiles.compare(own, {"r": {}}), [])


class RepositoryTest(unittest.TestCase):

    def test_this_repository_matches_its_manifest(self):
        with open(os.path.join(REPO, sharedfiles.MANIFEST), encoding="utf-8") as f:
            entries = sharedfiles.parse(f.read())
        self.assertEqual(sharedfiles.verify(REPO, entries), [])

    def test_the_shared_build_checks_list_themselves(self):
        with open(os.path.join(REPO, sharedfiles.MANIFEST), encoding="utf-8") as f:
            entries = sharedfiles.parse(f.read())
        for path in (".github/buildcheck/sharedfiles.py", ".github/check-shared-files.py",
                     ".github/buildcheck/tests/test_sharedfiles.py"):
            self.assertIn(path, entries)


if __name__ == "__main__":
    unittest.main()
