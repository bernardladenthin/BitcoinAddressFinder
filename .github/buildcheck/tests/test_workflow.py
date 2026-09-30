# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

import os
import unittest

from buildcheck import workflow
from buildcheck.tests.helpers import REPO, upload, workflow_text


class ParseTest(unittest.TestCase):

    def test_reads_every_needs_shape(self):
        text = workflow_text([
            ("a", None, []),
            ("b", "a", []),
            ("c", "[a, b]", []),
            ("d", "", []),
        ]).replace("    needs: \n", "    needs:\n      - a\n      - c  # why\n")
        jobs = workflow.parse(text)
        self.assertEqual(list(jobs), ["a", "b", "c", "d"])
        self.assertEqual(jobs["a"].needs, [])
        self.assertEqual(jobs["b"].needs, ["a"])
        self.assertEqual(jobs["c"].needs, ["a", "b"])
        self.assertEqual(jobs["d"].needs, ["a", "c"])

    def test_keys_outside_jobs_are_not_jobs(self):
        jobs = workflow.parse(workflow_text([("a", None, [])]) + "env:\n  X: 1\n")
        self.assertEqual(list(jobs), ["a"])

    def test_unknown_need_is_an_error(self):
        with self.assertRaisesRegex(ValueError, "needs nope"):
            workflow.parse(workflow_text([("a", "nope", [])]))

    def test_unreadable_needs_is_an_error_not_a_guess(self):
        with self.assertRaisesRegex(ValueError, "cannot read needs"):
            workflow.parse(workflow_text([("a", None, []), ("b", "${{ fromJSON(x) }}", [])]))

    def test_uploads_reads_the_artifact_name_not_the_step_name(self):
        body = ["      - name: Upload something", "        if: always()",
                "        uses: actions/upload-artifact@v7"] + upload("natives-x")[1:] \
            + ["      - run: echo name: fake"] + upload("second")
        job = workflow.parse(workflow_text([("a", None, body)]))["a"]
        self.assertEqual(job.uploads(), ["natives-x", "second"])

    def test_closure_is_transitive_and_excludes_the_job(self):
        jobs = workflow.parse(workflow_text([("a", None, []), ("b", "a", []), ("c", "[b]", []), ("x", None, [])]))
        self.assertEqual(workflow.closure(jobs, "c"), {"a", "b"})
        self.assertEqual(workflow.closure(jobs, "a"), set())


class RepositoryWorkflowsTest(unittest.TestCase):

    def test_every_workflow_of_the_repository_parses(self):
        folder = os.path.join(REPO, ".github", "workflows")
        for name in sorted(os.listdir(folder)):
            with self.subTest(name):
                with open(os.path.join(folder, name), encoding="utf-8") as f:
                    self.assertTrue(workflow.parse(f.read()))

    def test_publish_has_both_publish_jobs_and_they_wait_for_something(self):
        with open(os.path.join(REPO, ".github", "workflows", "publish.yml"), encoding="utf-8") as f:
            jobs = workflow.parse(f.read())
        for gate in ("publish-snapshot", "publish-release"):
            self.assertGreater(len(workflow.closure(jobs, gate)), 3, gate)


if __name__ == "__main__":
    unittest.main()
