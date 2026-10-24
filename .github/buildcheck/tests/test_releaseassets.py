# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from buildcheck.releaseassets import collect, configuration, file_classifiers, projects, signature_gaps
from buildcheck import workflow
from buildcheck.tests.helpers import REPO


VERSION = "2.0.0-SNAPSHOT"


class ReleaseAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / "assets"
        self.config = {"schema": 1, "maven": {"standalone": ["tool"], "cyclonedx": True}, "files": [
            {"source": "app/target/app-{version}-{classifier}.jar",
             "classifiers": {"csv": ".github/classifiers.csv", "column": "classifier"}},
            {"source": "mobile/publication/pom.xml", "name": "mobile-{version}.pom"},
            {"source": "mobile/build/mobile-{version}.aar"},
            {"source": "archive/upstream-*-{version}.tar.gz"}]}
        self.save_config()
        self.write("pom.xml", f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
<artifactId>parent</artifactId><version>{VERSION}</version><packaging>pom</packaging>
<modules><module>app</module><module>plugin</module></modules></project>""")
        self.outputs(".", "parent", "pom")
        self.module("app", "jar")
        self.module("plugin", "maven-plugin")
        self.module("tool", "jar", reactor=False)
        self.write(".github/classifiers.csv", "# comment\nclassifier\ncpu-linux\ngpu-linux\n")
        for classifier in ("cpu-linux", "gpu-linux"):
            self.asset(f"app/target/app-{VERSION}-{classifier}.jar")
        self.asset("mobile/publication/pom.xml")
        self.asset(f"mobile/build/mobile-{VERSION}.aar")
        self.asset(f"archive/upstream-build1-{VERSION}.tar.gz")

    def write(self, name, content="artifact"):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def save_config(self):
        self.write(".github/release-assets.json", json.dumps(self.config))

    def asset(self, name):
        self.write(name)
        self.write(name + ".asc", "signature of " + name)

    def outputs(self, folder, artifact, packaging, reactor=True):
        self.asset(f"{folder}/target/{artifact}-{VERSION}.pom")
        if packaging != "pom":
            for classifier in ("", "-sources", "-javadoc"):
                self.asset(f"{folder}/target/{artifact}-{VERSION}{classifier}.jar")
        if reactor:
            self.asset(f"{folder}/target/bom.json")

    def module(self, name, packaging, reactor=True):
        self.write(f"{name}/pom.xml", f"""<project>
<parent><version>{VERSION}</version></parent><artifactId>{name}</artifactId>
<packaging>{packaging}</packaging></project>""")
        self.outputs(name, name, packaging, reactor)

    def test_reactor_plugin_standalone_and_bom_names_match_central(self):
        self.assertEqual(collect(self.root, self.output), [])
        for artifact in ("app", "plugin", "tool"):
            for classifier in ("", "-sources", "-javadoc"):
                self.assertTrue((self.output / f"{artifact}-{VERSION}{classifier}.jar.asc").is_file())
        for artifact in ("parent", "app", "plugin"):
            self.assertTrue((self.output / f"{artifact}-{VERSION}-cyclonedx.json.asc").is_file())
        self.assertFalse((self.output / "bom.json").exists())
        self.assertFalse((self.output / f"tool-{VERSION}-cyclonedx.json").exists())
        self.assertTrue((self.output / f"mobile-{VERSION}.pom.asc").is_file())

    def test_new_reactor_module_is_automatic(self):
        pom = self.root / "pom.xml"
        pom.write_text(pom.read_text().replace("</modules>", "<module>new</module></modules>"))
        self.module("new", "jar")
        self.assertEqual(collect(self.root, self.output), [])
        self.assertTrue((self.output / f"new-{VERSION}.jar").is_file())

    def test_single_library_needs_no_native_android_or_bom_configuration(self):
        self.config = {"schema": 1}
        self.save_config()
        self.write("pom.xml", f"<project><artifactId>library</artifactId><version>{VERSION}</version></project>")
        self.outputs(".", "library", "jar")
        self.assertEqual(collect(self.root, self.output), [])
        self.assertEqual(len(list(self.output.iterdir())), 8)

    def test_missing_artifacts_signatures_boms_and_extras_preserve_available_files(self):
        missing = ["app/target/bom.json", f"plugin/target/plugin-{VERSION}.pom.asc",
                   f"app/target/app-{VERSION}-gpu-linux.jar", f"mobile/build/mobile-{VERSION}.aar"]
        for name in missing:
            (self.root / name).unlink()
        errors = collect(self.root, self.output)
        for name in missing:
            self.assertTrue(any(name in error for error in errors), errors)
        self.assertTrue((self.output / f"app-{VERSION}.jar").is_file())

    def test_empty_signature_fails(self):
        self.write(f"app/target/app-{VERSION}.jar.asc", "")
        self.assertTrue(any("missing or empty" in e for e in collect(self.root, self.output)))

    def test_stale_versions_unconfigured_fat_jars_and_plugin_dependencies_are_ignored(self):
        for name in ("app/target/app-1.0.0.jar", f"app/target/app-{VERSION}-jar-with-dependencies.jar",
                     "app/target/spotbugs/third-party.jar"):
            self.asset(name)
        self.assertEqual(collect(self.root, self.output), [])
        self.assertFalse(any("1.0.0" in p.name or "dependencies" in p.name or "third-party" in p.name
                             for p in self.output.iterdir()))

    def test_nonempty_destination_and_version_mismatch_fail(self):
        self.write("assets/stale.jar")
        with self.assertRaisesRegex(ValueError, "must be empty"):
            collect(self.root, self.output)
        (self.output / "stale.jar").unlink()
        pom = self.root / "plugin/pom.xml"
        pom.write_text(pom.read_text().replace(VERSION, "1.0.0"))
        with self.assertRaisesRegex(ValueError, "version mismatch"):
            collect(self.root, self.output)

    def test_ambiguous_archive_is_reported(self):
        self.asset(f"archive/upstream-build2-{VERSION}.tar.gz")
        self.assertTrue(any("found 2" in error for error in collect(self.root, self.output)))

    def test_staged_maven_files_survive_rebuild_and_missing_stage_file_fails_final_collection(self):
        stage = self.root / "central-assets"
        self.assertEqual(collect(self.root, stage, maven_only=True), [])
        original = (stage / f"app-{VERSION}.jar").read_bytes()
        self.write(f"app/target/app-{VERSION}.jar", "rebuilt unsigned jar")
        self.assertEqual(collect(self.root, self.output, maven_assets=stage), [])
        self.assertEqual((self.output / f"app-{VERSION}.jar").read_bytes(), original)
        (stage / f"app-{VERSION}-javadoc.jar").unlink()
        errors = collect(self.root, self.root / "partial", maven_assets=stage)
        self.assertTrue(any("javadoc.jar" in e for e in errors))

    def test_typo_or_wrong_schema_fails(self):
        for config in ({"schema": 2}, {"schema": 1, "filez": []},
                       {"schema": 1, "maven": {"cyclonedx": "yes"}}):
            with self.subTest(config=config):
                self.config = config
                self.save_config()
                with self.assertRaises(ValueError):
                    collect(self.root, self.output)

    def test_duplicate_names_traversal_bad_templates_and_empty_classifier_list_fail(self):
        for rule in ({"source": "mobile/publication/pom.xml", "name": f"app-{VERSION}.jar"},
                     {"source": "../outside.jar"}, {"source": "mobile/{unknown}.jar"},
                     {"source": "mobile/build/*.aar", "classifiers": []},
                     {"source": "mobile/build/*.aar", "name": "../outside.jar"}):
            with self.subTest(rule=rule):
                self.config["files"] = [rule]
                self.save_config()
                with self.assertRaises(ValueError):
                    collect(self.root, self.output)
                self.assertFalse(self.output.exists())

    def test_repeated_or_outside_module_fails(self):
        for module in ("app", "../../outside"):
            self.write("pom.xml", f"""<project><artifactId>parent</artifactId><version>{VERSION}</version>
<modules><module>app</module><module>{module}</module></modules></project>""")
            with self.assertRaises(ValueError):
                projects(self.root)

    def test_classifier_list_is_shared_with_packaging_loop(self):
        self.assertEqual(file_classifiers(self.root, 0), ["cpu-linux", "gpu-linux"])
        self.config["files"][0]["classifiers"] = ["gpu-one", "gpu-two"]
        self.save_config()
        self.assertEqual(file_classifiers(self.root, 0), ["gpu-one", "gpu-two"])
        with self.assertRaisesRegex(ValueError, "no file rule"):
            file_classifiers(self.root, 99)
        with self.assertRaisesRegex(ValueError, "must not be negative"):
            file_classifiers(self.root, -1)

    def test_current_repository_workflow_passes_collection_result_and_checks_both_uploads(self):
        jobs = workflow.parse((Path(REPO) / ".github/workflows/publish.yml").read_text())
        for kind in ("snapshot", "release"):
            job = jobs[f"publish-{kind}"].text
            self.assertIn(f"python3 .github/collect-release-assets.py signed-{kind}-assets", job)
            self.assertIn("assets_complete: $" + "{{ steps.preflight.outputs.complete }}", job)
            self.assertIn("        id: collect", job)
            attach = jobs["github-snapshot" if kind == "snapshot" else
                          "github-release" if "github-release" in jobs else "github-release-signed"].text
            self.assertIn(f"python3 .github/publication.py verify publication-package --export-github {kind}-assets", attach)
            self.assertNotIn("Fail if anything", attach)
        if configuration(REPO).get("maven", {}).get("cyclonedx", False):
            for kind in ("snapshot", "release"):
                self.assertIn(f"signed-{kind}-assets/*.json", jobs[f"publish-{kind}"].text)

    def test_current_repository_contract_collects_exact_complete_and_partial_fixture(self):
        # Exercise this checkout's real POMs and JSON, not just a hand-written generic fixture.
        repo = Path(REPO)
        config = configuration(repo)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            expected = set()

            def copy(relative):
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(repo / relative, target)

            def artifact(relative, name):
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                for suffix in ("", ".asc"):
                    Path(str(path) + suffix).write_text("fixture " + str(relative) + suffix)
                    expected.add(name + suffix)

            copy(".github/release-assets.json")
            modules = projects(repo, config.get("maven", {}).get("standalone", []))
            version = modules[0][2]
            for folder, name, _, packaging, reactor in modules:
                relative = folder.relative_to(repo)
                copy(relative / "pom.xml")
                base = f"{name}-{version}"
                artifact(relative / "target" / f"{base}.pom", f"{base}.pom")
                if packaging != "pom":
                    for classifier in ("", "-sources", "-javadoc"):
                        artifact(relative / "target" / f"{base}{classifier}.jar", f"{base}{classifier}.jar")
                if reactor and config.get("maven", {}).get("cyclonedx", False):
                    artifact(relative / "target/bom.json", f"{base}-cyclonedx.json")
            for index, rule in enumerate(config.get("files", [])):
                if isinstance(rule.get("classifiers"), dict):
                    copy(rule["classifiers"]["csv"])
                for classifier in file_classifiers(repo, index):
                    source = rule["source"].format(version=version, classifier=classifier).replace("*", "fixture")
                    name = rule.get("name", Path(source).name).format(version=version, classifier=classifier)
                    artifact(source, name)
            destination = root / "collected"
            self.assertEqual(collect(root, destination), [])
            self.assertEqual({p.name for p in destination.iterdir()}, expected)
            (destination / next(n for n in expected if n.endswith(".asc"))).unlink()
            self.assertNotEqual(signature_gaps(destination)[0], 0)
            # An incomplete reactor must still produce an upload directory with available files.
            first = modules[0][1] + "-" + version + ".pom"
            (root / "target" / first).unlink()
            self.assertTrue(collect(root, root / "partial"))
            self.assertTrue(any((root / "partial").iterdir()))

    def test_cli_reports_completeness_even_on_failure_and_accepts_parameters(self):
        scripts = self.root / ".github"
        (scripts / "buildcheck").mkdir()
        for name in ("collect-release-assets.py", "buildcheck/releaseassets.py", "buildcheck/__init__.py"):
            shutil.copyfile(Path(REPO) / ".github" / name, scripts / name)
        cli = [sys.executable, str(scripts / "collect-release-assets.py")]
        listing = subprocess.run(cli + ["--list-classifiers", "0"], capture_output=True, text=True)
        self.assertEqual(listing.returncode, 0, listing.stderr)
        self.assertEqual(listing.stdout.splitlines(), ["cpu-linux", "gpu-linux"])
        for complete in (True, False):
            if not complete:
                (self.root / f"plugin/target/plugin-{VERSION}.jar").unlink()
            output = self.root / f"github-output-{complete}"
            destination = self.root / f"collected-{complete}"
            result = subprocess.run(cli + [str(destination), "--config", str(scripts / "release-assets.json")],
                                    env={**os.environ, "GITHUB_OUTPUT": str(output)},
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0 if complete else 1, result.stdout + result.stderr)
            self.assertEqual(output.read_text(), f"complete={str(complete).lower()}\n")
            self.assertTrue((destination / f"app-{VERSION}.jar").is_file())


class SignatureReportTest(unittest.TestCase):
    def test_all_file_types_spaces_empty_orphan_and_missing_signatures(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.assertEqual(signature_gaps(root)[0], -1)
            for name in ("file.jar", "mobile.aar", "parent.pom", "bom.json", "source.tar.gz", "with spaces.zip"):
                (root / name).write_text("artifact")
                (root / (name + ".asc")).write_text("signature")
            self.assertEqual(signature_gaps(root), (0, []))
            (root / "bom.json.asc").write_text("")
            (root / "source.tar.gz.asc").unlink()
            (root / "orphan.jar.asc").write_text("signature")
            (root / "file.jar").write_text("")
            self.assertEqual(signature_gaps(root)[0], 4)

    def test_cli_always_returns_zero_and_writes_missing_for_post_upload_assertion(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            assets = root / "assets"
            assets.mkdir()
            (assets / "unsigned.json").write_text("bom")
            output = root / "github-output"
            result = subprocess.run([sys.executable, str(Path(REPO) / ".github/report-asset-signatures.py"),
                                     str(assets)], env={**os.environ, "GITHUB_OUTPUT": str(output)},
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("::error::unsigned", result.stdout)
            self.assertEqual(output.read_text(), "missing=1\n")


if __name__ == "__main__":
    unittest.main()
