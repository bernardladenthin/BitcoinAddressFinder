# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Publication gate and transport invariants; no signing key or remote writes."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

from buildcheck.publication import (CENTRAL_SNAPSHOT_JOB, central_name, check_ref, prepare, snapshot_pom,
                                    superseded, upload, verify)
from buildcheck import workflow
from buildcheck.tests.helpers import REPO


class PublicationFixture(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / '.github').mkdir()
        (self.root / '.github/release-assets.json').write_text('{"schema":1}')
        self.pom = '<project><modelVersion>4.0.0</modelVersion><groupId>local.test</groupId><artifactId>demo</artifactId><version>1-SNAPSHOT</version></project>'
        (self.root / 'pom.xml').write_text(self.pom)
        self.assets = self.root / 'assets'
        self.assets.mkdir()
        self.stage = self.root / 'target/central-deferred/local/test/demo/1-SNAPSHOT'
        self.stage.mkdir(parents=True)
        for suffix in ['.pom', '.jar', '-sources.jar', '-javadoc.jar']:
            name = 'demo-1-SNAPSHOT' + suffix
            for extra in ['', '.asc']:
                data = self.pom if suffix == '.pom' and not extra else name + extra
                (self.assets / (name + extra)).write_text(data)
                (self.stage / (name + extra)).write_text(data)
        self.package = self.root / 'package'
        self.signatures = patch('buildcheck.publication.subprocess.run')
        self.run = self.signatures.start()
        self.addCleanup(self.signatures.stop)
        quiet = patch('builtins.print')
        quiet.start()
        self.addCleanup(quiet.stop)

    def freeze(self, **kwargs):
        # ref="" keeps the runner's GITHUB_REF (a tag on a release run) out of the snapshot fixture.
        prepare(self.root, self.assets, self.package, **{"ref": "", **kwargs})

    def add(self, directory, name, data=None):
        for extra in ("", ".asc"):
            (directory / (name + extra)).write_text((data or name) + extra)

    def release(self):
        """Turn the fixture into version 1.2.3, staged as Maven stages a release."""
        pom = self.pom.replace("1-SNAPSHOT", "1.2.3")
        (self.root / "pom.xml").write_text(pom)
        stage = self.root / "target/central-staging/local/test/demo/1.2.3"
        stage.mkdir(parents=True)
        for path in list(self.assets.iterdir()):
            path.unlink()
        for suffix in [".pom", ".jar", "-sources.jar", "-javadoc.jar"]:
            for directory in (self.assets, stage):
                self.add(directory, "demo-1.2.3" + suffix, pom if suffix == ".pom" else None)


class PublicationTest(PublicationFixture):
    def test_freezes_once_and_verifies_every_signature(self):
        self.freeze()
        self.assertEqual(self.run.call_count, 4)
        self.assertEqual(verify(self.package)['channel'], 'snapshot')
        (self.assets / 'demo-1-SNAPSHOT.jar').write_text('later rebuild')
        self.assertNotEqual((self.package / 'github/demo-1-SNAPSHOT.jar').read_text(), 'later rebuild')
        verify(self.package)

    def test_missing_signature_or_wrong_central_bytes_block_both_publishers(self):
        sig = self.assets / 'demo-1-SNAPSHOT.jar.asc'
        sig.unlink()
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            self.freeze()
        self.assertFalse(self.package.exists())
        sig.write_text('signature')
        with self.assertRaisesRegex(ValueError, 'bytes differ'):
            self.freeze()
        self.assertFalse(self.package.exists())

    def test_invalid_signature_blocks_package_creation(self):
        import subprocess
        self.run.side_effect = subprocess.CalledProcessError(1, 'gpg')
        with self.assertRaises(subprocess.CalledProcessError):
            self.freeze()
        self.assertFalse(self.package.exists())

    def test_transport_tampering_and_extra_files_are_rejected(self):
        self.freeze()
        unexpected = self.package / 'unexpected.txt'
        unexpected.write_text('extra')
        with self.assertRaisesRegex(ValueError, 'membership'):
            verify(self.package)
        unexpected.unlink()
        (self.package / 'github/demo-1-SNAPSHOT.jar').write_text('tampered')
        with self.assertRaisesRegex(ValueError, 'bytes changed'):
            verify(self.package)

    def test_snapshot_transfer_retains_signature_extensions(self):
        self.freeze()
        target = self.root / 'transfer.xml'
        snapshot_pom(self.package, target, 'file:///unused-local-diagnostic')
        xml = ET.parse(target).getroot()
        config = xml.find('.//{*}configuration')
        self.assertIn('jar.asc', config.findtext('{*}types'))
        self.assertIn('pom.asc', config.findtext('{*}types'))
        self.assertEqual(xml.findtext('.//{*}phase'), 'validate')
        self.assertEqual(config.findtext('{*}generatePom'), 'false')

    def test_pom_only_component_transfers_signature_and_pom_together(self):
        pom = self.pom.replace('</project>', '<packaging>pom</packaging></project>')
        (self.root / 'pom.xml').write_text(pom)
        for directory in (self.assets, self.stage):
            for path in directory.iterdir():
                if '.jar' in path.name:
                    path.unlink()
            (directory / 'demo-1-SNAPSHOT.pom').write_text(pom)
        self.freeze()
        target = self.root / 'pom-transfer.xml'
        snapshot_pom(self.package, target)
        config = ET.parse(target).getroot().find('.//{*}configuration')
        self.assertEqual(config.findtext('{*}packaging'), 'pom.asc')
        self.assertTrue(config.findtext('{*}file').endswith('.pom.asc'))
        self.assertTrue(config.findtext('{*}pomFile').endswith('.pom'))
        self.assertIsNone(config.find('{*}files'))

    def test_publishers_depend_only_on_the_same_checked_handoff(self):
        jobs = workflow.parse((Path(REPO) / '.github/workflows/publish.yml').read_text())
        action = (Path(REPO) / '.github/actions/finalize-publication/action.yml').read_text()
        self.assertIn('steps.ready.outputs.complete', action)
        order = ['id: collect', 'id: preflight', 'id: handoff', 'id: ready']
        self.assertEqual([action.index(item) for item in order], sorted(action.index(item) for item in order))
        self.assertIn('if-no-files-found: error', action)
        self.assertNotIn('always()', action)
        self.assertNotIn('continue-on-error', action)
        for channel in ['snapshot', 'release']:
            prep = jobs['publish-' + channel]
            self.assertIn('steps.finalize.outputs.complete', prep.text)
            self.assertIn('uses: ./.github/actions/finalize-publication', prep.text)
            self.assertIn('channel: ' + channel, prep.text)
            self.assertNotIn('publication.py prepare', prep.text)
            self.assertNotIn('attestations: write', prep.text)
            self.assertNotIn('id-token: write', prep.text)
            self.assertIn('-DskipUpload=true deploy', prep.text)
            self.assertNotIn('publishAllPublicationsToCentralSnapshotsRepository', prep.text)
            for consumer in ['central', 'github', 'attest']:
                job = jobs[consumer + '-' + channel]
                self.assertEqual(job.needs, ['publish-' + channel])
                self.assertIn("needs.publish-" + channel + ".outputs.assets_complete == 'true'", job.text)
                self.assertIn('digest-mismatch: error', job.text)
            self.assertIn('publication.py upload publication-package', jobs['central-' + channel].text)
            self.assertIn('publication.py verify publication-package', jobs['github-' + channel].text)
            attest = jobs['attest-' + channel].text
            self.assertLess(attest.index('publication.py verify'), attest.index('uses: actions/attest-build-provenance@'))
            self.assertIn('subject-path: publication-package/github/*', attest)
            self.assertIn('id-token: write', attest)
            self.assertIn('attestations: write', attest)

    def test_snapshot_channel_and_ref_must_match_the_version(self):
        with self.assertRaisesRegex(ValueError, "release publication of snapshot version"):
            self.freeze(channel="release")
        with self.assertRaisesRegex(ValueError, "tag v1 names SNAPSHOT version"):
            self.freeze(ref="refs/tags/v1")
        self.assertFalse(self.package.exists())
        self.freeze(channel="snapshot", ref="refs/heads/main")
        self.assertEqual(verify(self.package)["channel"], "snapshot")

    def test_release_publishes_only_from_the_tag_of_its_version(self):
        self.release()
        for ref in ("refs/heads/main", "refs/tags/v1.2.4", "refs/tags/1.2.3"):
            with self.subTest(ref=ref):
                with self.assertRaisesRegex(ValueError, "publishes only from tag v1.2.3"):
                    self.freeze(ref=ref)
        with self.assertRaisesRegex(ValueError, "snapshot publication of release version"):
            self.freeze(channel="snapshot", ref="refs/tags/v1.2.3")
        self.freeze(channel="release", ref="refs/tags/v1.2.3")
        self.assertEqual(verify(self.package)["channel"], "release")

    def test_check_ref_without_a_ref_checks_only_the_channel(self):
        self.assertEqual(check_ref("1-SNAPSHOT", None), "snapshot")
        self.assertEqual(check_ref("1.0", None, "release"), "release")
        self.assertEqual(check_ref("1-SNAPSHOT", "refs/pull/7/merge", "snapshot"), "snapshot")
        with self.assertRaises(ValueError):
            check_ref("1.0", None, "snapshot")

    def test_gradle_timestamped_snapshot_in_a_second_root_matches_the_github_names(self):
        (self.root / ".github/release-assets.json").write_text(json.dumps({
            "schema": 1,
            "files": [{"source": "mobile/build/mobile-{version}.aar", "central": True},
                      {"source": "mobile/build/pom-default.xml", "name": "mobile-{version}.pom", "central": True},
                      {"source": "mobile/build/notes-{version}.txt"}],
            "publication": {"central_roots": ["target/{staging}", "mobile/build/staging-repo"]}}))
        build = self.root / "mobile/build"
        build.mkdir(parents=True)
        self.add(build, "mobile-1-SNAPSHOT.aar")
        self.add(build, "pom-default.xml", "mobile pom")
        self.add(build, "notes-1-SNAPSHOT.txt")
        for name in ("mobile-1-SNAPSHOT.aar", "mobile-1-SNAPSHOT.pom", "notes-1-SNAPSHOT.txt"):
            source = build / ("pom-default.xml" if name.endswith(".pom") else name)
            for extra in ("", ".asc"):
                (self.assets / (name + extra)).write_bytes(Path(str(source) + extra).read_bytes())
        # What Gradle's maven-publish writes for a SNAPSHOT, even into a file repository.
        gradle = self.root / "mobile/build/staging-repo/local/test/mobile/1-SNAPSHOT"
        gradle.mkdir(parents=True)
        stamp = "mobile-1-20261025.085646-1"
        for name, source in ((stamp + ".aar", "mobile-1-SNAPSHOT.aar"), (stamp + ".pom", "pom-default.xml")):
            for extra in ("", ".asc"):
                (gradle / (name + extra)).write_bytes((build / (source + extra)).read_bytes())
            for checksum in (".md5", ".sha1", ".sha256", ".sha512"):
                (gradle / (name + checksum)).write_text("regenerated")
        (gradle / "maven-metadata.xml").write_text("<metadata/>")
        (gradle.parent / "maven-metadata.xml.sha1").write_text("regenerated")
        (self.root / "target/central-deferred/.index").write_text("plugin bookkeeping")
        self.freeze()
        files = verify(self.package)["files"]
        self.assertIn("central/local/test/mobile/1-SNAPSHOT/mobile-1-SNAPSHOT.aar.asc", files)
        self.assertIn("central/local/test/mobile/1-SNAPSHOT/mobile-1-SNAPSHOT.pom", files)
        self.assertIn("github/notes-1-SNAPSHOT.txt.asc", files)
        self.assertFalse(any("notes" in name for name in files if name.startswith("central/")))
        self.assertFalse(any("2026" in name or "metadata" in name or name.endswith(".md5") for name in files))

    def test_github_only_file_in_central_staging_and_unknown_staging_files_fail(self):
        self.add(self.stage, "demo-1-SNAPSHOT-cyclonedx.json")
        self.add(self.assets, "demo-1-SNAPSHOT-cyclonedx.json")
        with self.assertRaisesRegex(ValueError, "unexpected=.*cyclonedx"):
            self.freeze()
        for name in ("demo-1-SNAPSHOT-cyclonedx.json", "demo-1-SNAPSHOT-cyclonedx.json.asc"):
            (self.stage / name).unlink()
            (self.assets / name).unlink()
        for name in ("demo-1-SNAPSHOT.module", "demo-1-SNAPSHOT-bundle.zip"):
            with self.subTest(name=name):
                (self.stage / name).write_text("payload the transfer would not carry")
                with self.assertRaisesRegex(ValueError, "unexpected file in Central staging: .*" + name):
                    self.freeze()
                (self.stage / name).unlink()
        self.assertFalse(self.package.exists())

    def test_central_signatures_pair_by_name_not_by_count(self):
        # One signature missing and one orphan keep the count; the gate names both.
        (self.stage / "demo-1-SNAPSHOT.jar.asc").unlink()
        (self.stage / "demo-1-SNAPSHOT-tests.jar.asc").write_text("orphan")
        with self.assertRaisesRegex(ValueError, r"missing=\['demo-1-SNAPSHOT.jar.asc'\]; "
                                                r"unexpected=\['demo-1-SNAPSHOT-tests.jar.asc'\]"):
            self.freeze()

    def test_github_asset_names_github_would_rename_fail(self):
        (self.root / ".github/release-assets.json").write_text(json.dumps(
            {"schema": 1, "files": [{"source": "dist/demo {version}.zip"}]}))
        (self.root / "dist").mkdir()
        self.add(self.root / "dist", "demo 1-SNAPSHOT.zip")
        self.add(self.assets, "demo 1-SNAPSHOT.zip")
        with self.assertRaisesRegex(ValueError, "GitHub would rename release asset: demo 1-SNAPSHOT.zip"):
            self.freeze()

    def test_prepare_reads_the_given_contract(self):
        other = self.root / "other.json"
        other.write_text(json.dumps({"schema": 1, "files": [{"source": "extra-{version}.txt"}]}))
        self.add(self.root, "extra-1-SNAPSHOT.txt")
        with self.assertRaisesRegex(ValueError, r"GitHub membership.*missing=\['extra-1-SNAPSHOT.txt'\]"):
            self.freeze(config_path=other)
        self.add(self.assets, "extra-1-SNAPSHOT.txt")
        self.freeze(config_path=other)
        self.assertIn("github/extra-1-SNAPSHOT.txt", verify(self.package)["files"])

    def test_timestamp_names_are_normalized_only_for_their_own_artifact_and_snapshot(self):
        self.assertEqual(central_name("app-1.0-20261025.085646-12-sources.jar", "app", "1.0-SNAPSHOT"),
                         "app-1.0-SNAPSHOT-sources.jar")
        self.assertEqual(central_name("app-1.0-SNAPSHOT.jar", "app", "1.0-SNAPSHOT"), "app-1.0-SNAPSHOT.jar")
        self.assertEqual(central_name("app-tool-1.0-20261025.085646-1.jar", "app", "1.0-SNAPSHOT"),
                         "app-tool-1.0-20261025.085646-1.jar")
        self.assertEqual(central_name("app-1.0-20261025.085646-1.jar", "app", "1.0"),
                         "app-1.0-20261025.085646-1.jar")


class UploadTest(PublicationFixture):
    """The two transfers with the network and Maven replaced; the bytes are the frozen ones."""

    def setUp(self):
        super().setUp()
        self.signatures.stop()  # upload() runs curl/mvn, each test patches subprocess.run itself
        env = patch.dict(os.environ, {"MAVEN_USERNAME": "user", "MAVEN_PASSWORD": "secret",
                                      "GITHUB_REPOSITORY": "owner/repo", "GITHUB_SHA": "",
                                      "GITHUB_RUN_ID": "10"})
        env.start()
        self.addCleanup(env.stop)

    def frozen(self, release=False):
        with patch("buildcheck.publication.subprocess.run"):
            if release:
                self.release()
                self.freeze(ref="refs/tags/v1.2.3")
            else:
                self.freeze()
        return self.package

    def test_release_bundle_holds_the_frozen_central_files_with_checksums_and_no_credentials_in_argv(self):
        package = self.frozen(release=True)
        seen = {}

        def curl(args, **kwargs):
            seen["config"] = Path(args[args.index("--config") + 1]).read_text()
            bundle = next(a for a in args if a.startswith("bundle=@"))[len("bundle=@"):]
            with zipfile.ZipFile(bundle) as archive:
                seen["names"] = set(archive.namelist())
                seen["jar"] = archive.read("local/test/demo/1.2.3/demo-1.2.3.jar")
            seen["args"] = args
            return subprocess.CompletedProcess(args, 0, '"deployment-id_1"', "")

        with patch("buildcheck.publication.subprocess.run", side_effect=curl):
            upload(package)
        self.assertIn("local/test/demo/1.2.3/demo-1.2.3.jar.asc", seen["names"])
        self.assertIn("local/test/demo/1.2.3/demo-1.2.3.jar.sha512", seen["names"])
        self.assertNotIn("local/test/demo/1.2.3/demo-1.2.3.jar.asc.md5", seen["names"])
        self.assertFalse(any(name.startswith("github/") or name == "manifest.json" for name in seen["names"]))
        self.assertEqual(seen["jar"], (package / "github/demo-1.2.3.jar").read_bytes())
        self.assertIn("Authorization: Bearer dXNlcjpzZWNyZXQ=", seen["config"])
        self.assertFalse(any("secret" in arg or "dXNlcjpzZWNyZXQ" in arg for arg in seen["args"]))
        self.assertTrue(seen["args"][-1].endswith("?publishingType=AUTOMATIC"))

    def test_release_upload_without_a_valid_receipt_fails(self):
        package = self.frozen(release=True)
        for code, out in ((22, "rejected"), (0, "not a receipt!")):
            with self.subTest(code=code):
                result = subprocess.CompletedProcess([], code, out, "")
                with patch("buildcheck.publication.subprocess.run", return_value=result):
                    with self.assertRaises(RuntimeError):
                        upload(package)

    def test_snapshot_transfer_runs_maven_on_the_generated_pom(self):
        package = self.frozen()
        with patch("buildcheck.publication.subprocess.run") as run:
            upload(package)
        args = run.call_args.args[0]
        self.assertEqual((args[0], args[-1]), ("mvn", "validate"))
        self.assertTrue(args[args.index("-f") + 1].endswith("pom.xml"))

    def test_snapshot_transfer_skips_only_when_asked_and_superseded(self):
        os.environ["GITHUB_SHA"] = "a" * 40
        package = self.frozen()
        gh = FakeRuns(newer_published=True)
        with patch("buildcheck.publication.subprocess.run") as run:
            upload(package, skip_superseded=True, github=gh)
            self.assertFalse(run.called)
            upload(package, github=gh)
            self.assertTrue(run.called)


class FakeRuns:
    def __init__(self, newer_published=False, status="ahead", job=CENTRAL_SNAPSHOT_JOB, conclusion="success"):
        self.newer_published, self.status, self.job, self.conclusion = newer_published, status, job, conclusion
        self.calls = []

    def api(self, method, endpoint, payload=None, optional=False):
        self.calls.append(endpoint)
        if endpoint.endswith("/actions/runs/10"):
            return {"workflow_id": 99}
        if "/actions/workflows/99/runs?" in endpoint:
            return {"workflow_runs": [{"id": 10, "head_sha": "a" * 40}, {"id": 11, "head_sha": "a" * 40},
                                      {"id": 12, "head_sha": "b" * 40}]}
        if "/actions/runs/12/jobs?" in endpoint:
            if endpoint.endswith("page=1"):
                return {"jobs": [{"name": f"job {i}", "conclusion": "success"} for i in range(100)]}
            return {"jobs": [{"name": self.job, "conclusion": self.conclusion}] if self.newer_published else []}
        if "/compare/" in endpoint:
            return {"status": self.status}
        raise AssertionError(endpoint)


class SupersededTest(unittest.TestCase):
    def setUp(self):
        quiet = patch("builtins.print")
        quiet.start()
        self.addCleanup(quiet.stop)

    def test_a_newer_commit_transferred_by_another_run_supersedes_this_one(self):
        gh = FakeRuns(newer_published=True)
        self.assertTrue(superseded("owner/repo", "a" * 40, "10", gh))
        # Its own run and other runs of the same commit are never asked about their jobs.
        self.assertFalse(any("/runs/10/jobs" in c or "/runs/11/jobs" in c for c in gh.calls))
        self.assertTrue(any(c.endswith("page=2") for c in gh.calls))
        self.assertTrue(any(c.endswith("/compare/" + "a" * 40 + "..." + "b" * 40) for c in gh.calls))

    def test_older_divergent_unfinished_or_failed_runs_do_not_supersede(self):
        for kwargs in ({"newer_published": True, "status": "behind"},
                       {"newer_published": True, "status": "diverged"},
                       {"newer_published": False},
                       {"newer_published": True, "conclusion": "failure"},
                       {"newer_published": True, "job": "Some other job"}):
            with self.subTest(**kwargs):
                self.assertFalse(superseded("owner/repo", "a" * 40, "10", FakeRuns(**kwargs)))


class WorkflowContractTest(unittest.TestCase):
    """The shared jobs call the gate the way the code above expects."""

    def setUp(self):
        self.jobs = workflow.parse((Path(REPO) / ".github/workflows/publish.yml").read_text())

    def test_ref_checks_run_before_anything_is_prepared(self):
        for job, channel in (("check-snapshot", "snapshot"), ("check-tag", "release")):
            self.assertIn("python3 .github/publication.py check-ref --channel " + channel, self.jobs[job].text)
            self.assertIn("persist-credentials: false", self.jobs[job].text)
        action = (Path(REPO) / ".github/actions/finalize-publication/action.yml").read_text()
        self.assertIn('publication.py prepare "signed-${CHANNEL}-assets" publication-package --channel "$CHANNEL"',
                      action)

    def test_snapshot_transfers_are_serialized_and_skip_superseded_commits(self):
        job = self.jobs["central-snapshot"].text
        self.assertIn("name: " + CENTRAL_SNAPSHOT_JOB, job)
        self.assertIn("group: central-snapshot-${{ github.repository }}", job)
        self.assertIn("cancel-in-progress: false", job)
        self.assertIn("actions: read", job)
        self.assertIn("python3 .github/publication.py upload publication-package --skip-superseded", job)
        self.assertNotIn("--skip-superseded", self.jobs["central-release"].text)
