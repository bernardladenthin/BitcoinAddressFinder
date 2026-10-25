# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""collect-third-party-licenses.py: embedded legal files, supplemental notices, completeness."""

import importlib.util
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from buildcheck.tests.helpers import REPO

_SPEC = importlib.util.spec_from_file_location(
    "collect_third_party_licenses", Path(REPO) / ".github/collect-third-party-licenses.py")
licenses = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(licenses)


class CollectTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        quiet = patch("builtins.print")
        quiet.start()
        self.addCleanup(quiet.stop)

    def jar(self, name, entries):
        path = self.root / "repo" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            for entry, data in entries.items():
                archive.writestr(entry, data)
        return path

    def classpath(self, *jars):
        path = self.root / "classpath.txt"
        path.write_text(os.pathsep.join(str(jar) for jar in jars), encoding="utf-8")
        return path

    def notices(self, notices):
        folder = self.root / "notices"
        for notice in notices:
            (folder / notice["directory"]).mkdir(parents=True, exist_ok=True)
            (folder / notice["directory"] / "LICENSE").write_text("license of " + notice["directory"])
        (folder / "notices.json").write_text(json.dumps({"format": 1, "notices": notices}))
        return folder

    def test_embedded_files_are_kept_and_code_named_license_is_not(self):
        jar = self.jar("lib-1.0.jar", {"META-INF/LICENSE": "MIT", "a/LICENSE.class": b"\xca\xfe",
                                        "META-INF/NOTICE.txt": "notice"})
        entries = licenses.collect(self.classpath(jar), self.root / "out")
        self.assertEqual(entries[0]["legal_files"], ["META-INF/LICENSE", "META-INF/NOTICE.txt"])
        self.assertNotIn("supplemental_files", entries[0])

    def test_a_jar_without_legal_files_gets_the_supplied_notice_by_base_name(self):
        bare = self.jar("lwjgl-opencl-3.3.6-natives-linux.jar", {"x.class": b"\xca\xfe"})
        folder = self.notices([{"directory": "lwjgl-3.3.6", "license": "BSD-3-Clause",
                                "artifacts": ["lwjgl", "lwjgl-opencl"]}])
        entries = licenses.collect(self.classpath(bare), self.root / "out", folder, require_complete=True)
        self.assertEqual(entries[0]["supplemental_files"], ["supplemental/LICENSE"])
        copied = self.root / "out" / entries[0]["directory"] / "supplemental/LICENSE"
        self.assertEqual(copied.read_text(), "license of lwjgl-3.3.6")

    def test_embedded_files_win_over_a_supplied_notice(self):
        jar = self.jar("lib-1.0.jar", {"META-INF/LICENSE": "MIT"})
        folder = self.notices([{"directory": "lib", "license": "MIT", "artifacts": ["lib"]}])
        entries = licenses.collect(self.classpath(jar), self.root / "out", folder)
        self.assertNotIn("supplemental_files", entries[0])

    def test_require_complete_fails_on_a_jar_without_any_legal_files(self):
        bare = self.jar("bare-2.0.jar", {"x.class": b"\xca\xfe"})
        with self.assertRaisesRegex(ValueError, "bare-2.0.jar"):
            licenses.collect(self.classpath(bare), self.root / "out", None, require_complete=True)
        entries = licenses.collect(self.classpath(bare), self.root / "out2")
        self.assertEqual(entries[0]["legal_files"], [])

    def test_malformed_or_ambiguous_notices_fail(self):
        folder = self.notices([{"directory": "a", "license": "MIT", "artifacts": ["x"]},
                               {"directory": "b", "license": "MIT", "artifacts": ["x"]}])
        with self.assertRaisesRegex(ValueError, "listed twice"):
            licenses.supplemental_notices(folder)
        (folder / "notices.json").write_text(json.dumps({"format": 1, "notices": [
            {"directory": "missing", "license": "MIT", "artifacts": ["y"]}]}))
        with self.assertRaisesRegex(ValueError, "missing or empty"):
            licenses.supplemental_notices(folder)

    def test_base_name_strips_version_and_classifier(self):
        self.assertEqual(licenses.base_name("lwjgl-opencl-3.3.6-natives-linux.jar"), "lwjgl-opencl")
        self.assertEqual(licenses.base_name("jcip-annotations-1.0.jar"), "jcip-annotations")
        self.assertEqual(licenses.base_name("plain.jar"), "plain")


if __name__ == "__main__":
    unittest.main()
