# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from buildcheck.snapshot import GitHub, publish
from buildcheck import workflow
from buildcheck.tests.helpers import REPO


OLD, NEW = "a" * 40, "b" * 40


class FakeGitHub:
    def __init__(self):
        self.ref = {"object": {"type": "commit", "sha": OLD}}
        self.release = {"id": 7, "body": "Snapshot from old commit"}
        self.comparison = "ahead"
        self.pages = {1: [{"id": 1, "name": "old.jar"}, {"id": 2, "name": "new.json"}]}
        self.calls = []
        self.fail_upload = False
        self.fail_deletes = set()
        self.tag = "snapshot"

    def api(self, method, endpoint, payload=None, optional=False):
        self.calls.append((method, endpoint, payload))
        if method == "DELETE" and int(endpoint.rsplit("/", 1)[1]) in self.fail_deletes:
            raise RuntimeError("delete failed")
        if method == "GET":
            if endpoint.endswith(f"git/ref/tags/{self.tag}"):
                return self.ref
            if endpoint.endswith(f"releases/tags/{self.tag}"):
                return self.release
            if "/compare/" in endpoint:
                return {"status": self.comparison}
            if "/git/tags/" in endpoint:
                return {"object": {"type": "commit", "sha": OLD}}
            if "/assets?" in endpoint:
                return self.pages[int(endpoint.split("&page=")[1])]
        if method == "POST" and endpoint.endswith("/releases"):
            return {"id": 7}
        if method == "POST" and endpoint.endswith("/git/refs"):
            return {"object": {"type": "commit", "sha": payload["sha"]}}
        return {}

    def command(self, *args):
        self.calls.append(("upload", args, None))
        if self.fail_upload:
            raise subprocess.CalledProcessError(1, "gh release upload")


class SnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.assets = Path(self.temp.name)
        for name in ("new.json", "new.json.asc"):
            (self.assets / name).write_text("artifact")
        self.gh = FakeGitHub()
        quiet = patch("builtins.print")
        quiet.start()
        self.addCleanup(quiet.stop)

    def publish(self, **kwargs):
        return publish("owner/repo", NEW, kwargs.pop("run_id", 10), kwargs.pop("attempt", 1),
                       self.assets, kwargs.pop("complete", True), self.gh, **kwargs)

    def test_upload_precedes_tag_notes_and_prune_and_release_itself_survives(self):
        self.assertTrue(self.publish())
        calls = self.gh.calls
        upload = next(i for i, c in enumerate(calls) if c[0] == "upload")
        tag = next(i for i, c in enumerate(calls) if c[0] == "PATCH" and "/git/refs/" in c[1])
        notes = next(c[2] for c in calls if c[0] == "PATCH" and c[1].endswith("/releases/7"))
        self.assertLess(upload, tag)
        self.assertIn(NEW, notes["body"])
        self.assertIn("/actions/runs/10/attempts/1", notes["body"])
        self.assertEqual(notes["target_commitish"], NEW)
        deletes = [c[1] for c in calls if c[0] == "DELETE"]
        self.assertEqual(deletes, ["repos/owner/repo/releases/assets/1"])

    def test_incomplete_collection_uploads_available_files_without_promoting_or_pruning(self):
        with self.assertRaisesRegex(ValueError, "collection is incomplete"):
            self.publish(complete=False)
        self.assertTrue(any(c[0] == "upload" for c in self.gh.calls))
        self.assertFalse(any(c[0] in ("PATCH", "DELETE") for c in self.gh.calls))

    def test_missing_signature_also_prevents_promotion(self):
        (self.assets / "new.json.asc").unlink()
        with self.assertRaisesRegex(ValueError, "collection is incomplete"):
            self.publish()
        self.assertFalse(any(c[0] == "PATCH" for c in self.gh.calls))

    def test_late_finishing_older_commit_cannot_overwrite_newer_snapshot(self):
        self.gh.comparison = "behind"
        self.assertFalse(self.publish())
        self.assertTrue(all(c[0] == "GET" for c in self.gh.calls))

    def test_empty_signature_or_orphan_also_prevents_promotion(self):
        for name in ("new.json.asc", "orphan.jar.asc"):
            with self.subTest(name=name):
                (self.assets / name).write_text("")
                with self.assertRaisesRegex(ValueError, "collection is incomplete"):
                    self.publish()
                self.assertFalse(any(c[0] in ("PATCH", "DELETE") for c in self.gh.calls))

    def test_rolling_tag_and_title_are_parameters(self):
        self.gh.tag = "nightly"
        self.assertTrue(self.publish(tag="nightly", title="Nightly build"))
        upload = next(c for c in self.gh.calls if c[0] == "upload")
        self.assertEqual(upload[1][2], "nightly")
        notes = next(c[2] for c in self.gh.calls if c[0] == "PATCH" and c[1].endswith("/releases/7"))
        self.assertEqual(notes["name"], "Nightly build")

    def test_same_commit_older_run_or_attempt_is_skipped(self):
        self.gh.ref["object"]["sha"] = NEW
        for run, attempt in ((11, 1), (10, 2)):
            self.gh.release["body"] = f"<!-- snapshot-publish {run} {attempt} {NEW} -->"
            self.gh.calls.clear()
            self.assertFalse(self.publish())
            self.assertTrue(all(c[0] == "GET" for c in self.gh.calls))

    def test_new_attempt_of_same_commit_can_replace_old_artifacts(self):
        self.gh.ref["object"]["sha"] = NEW
        self.gh.release["body"] = f"<!-- snapshot-publish 10 1 {NEW} -->"
        self.assertTrue(self.publish(attempt=2))

    def test_annotated_tags_are_resolved_before_comparing_commits(self):
        self.gh.ref["object"]["type"] = "tag"
        self.assertTrue(self.publish())
        self.assertTrue(any("/git/tags/" in c[1] for c in self.gh.calls if c[0] == "GET"))

    def test_creates_missing_tag_and_release_once(self):
        self.gh.ref, self.gh.release = None, None
        self.assertTrue(self.publish())
        self.assertEqual(sum(c[0] == "POST" and c[1].endswith("/git/refs") for c in self.gh.calls), 1)
        self.assertTrue(any(c[0] == "POST" and c[1].endswith("/releases") for c in self.gh.calls))

    def test_reads_all_asset_pages_before_deleting(self):
        self.gh.pages = {1: [{"id": i, "name": f"old-{i}.jar"} for i in range(100)],
                         2: [{"id": 101, "name": "last-old.jar"}, {"id": 102, "name": "new.json"}]}
        self.assertTrue(self.publish())
        calls = self.gh.calls
        page2 = next(i for i, c in enumerate(calls) if c[0] == "GET" and "&page=2" in c[1])
        deletion = next(i for i, c in enumerate(calls) if c[0] == "DELETE")
        self.assertLess(page2, deletion)
        self.assertEqual(sum(c[0] == "DELETE" for c in calls), 101)

    def test_divergent_commit_and_empty_upload_are_refused(self):
        self.gh.comparison = "diverged"
        with self.assertRaisesRegex(ValueError, "divergent"):
            self.publish()
        self.assertTrue(all(c[0] == "GET" for c in self.gh.calls))
        for path in self.assets.iterdir():
            path.unlink()
        self.gh.calls.clear()
        with self.assertRaisesRegex(ValueError, "nonempty"):
            self.publish()
        self.assertEqual(self.gh.calls, [])

    def test_upload_failure_does_not_move_tag_or_prune(self):
        self.gh.fail_upload = True
        with self.assertRaises(subprocess.CalledProcessError):
            self.publish()
        self.assertFalse(any(c[0] in ("PATCH", "DELETE") for c in self.gh.calls))

    def test_prune_tries_remaining_assets_after_a_deletion_failure(self):
        self.gh.pages[1].append({"id": 3, "name": "another-old.jar"})
        self.gh.fail_deletes.add(1)
        with self.assertRaisesRegex(RuntimeError, "delete failed"):
            self.publish()
        self.assertTrue(any(c[0] == "DELETE" and c[1].endswith("/assets/3") for c in self.gh.calls))

    def test_signature_only_directory_does_not_mutate_snapshot(self):
        (self.assets / "new.json").unlink()
        with self.assertRaisesRegex(ValueError, "no artifacts"):
            self.publish()
        self.assertEqual(self.gh.calls, [])

    def test_workflow_serializes_entire_update_and_passes_collection_result(self):
        jobs = workflow.parse((Path(REPO) / ".github/workflows/publish.yml").read_text())
        job = jobs["github-snapshot"].text
        self.assertIn("    concurrency:\n      group: snapshot-release-${{ github.repository }}", job)
        self.assertIn("      cancel-in-progress: false", job)
        self.assertIn("ASSETS_COMPLETE: ${{ needs.publish-snapshot.outputs.assets_complete }}", job)
        self.assertIn("python3 .github/update-snapshot.py snapshot-assets", job)

    def test_optional_api_failure_accepts_only_404_not_authentication_failure(self):
        gh = GitHub()
        for status in (403, 404):
            response = subprocess.CompletedProcess([], 1, "", f"gh: failure (HTTP {status})")
            with patch("buildcheck.snapshot.subprocess.run", return_value=response):
                if status == 404:
                    self.assertIsNone(gh.api("GET", "repos/owner/repo/releases/tags/snapshot", optional=True))
                else:
                    with self.assertRaisesRegex(RuntimeError, "403"):
                        gh.api("GET", "repos/owner/repo/releases/tags/snapshot", optional=True)

    def test_release_upload_jobs_do_not_persist_checkout_credentials(self):
        jobs = workflow.parse((Path(REPO) / ".github/workflows/publish.yml").read_text())
        versioned = "github-release" if "github-release" in jobs else "github-release-signed"
        for name in ("github-snapshot", versioned):
            checkouts = [step for step in jobs[name].steps()
                         if any("uses: actions/checkout@" in line for line in step)]
            self.assertTrue(checkouts, name)
            for step in checkouts:
                self.assertIn("          persist-credentials: false", step, name)


if __name__ == "__main__":
    unittest.main()
