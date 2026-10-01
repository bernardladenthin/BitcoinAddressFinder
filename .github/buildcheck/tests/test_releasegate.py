# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

import os
import unittest

from buildcheck import releasegate, workflow
from buildcheck.tests.helpers import REPO, workflow_text

GATES = ("pub-a", "pub-b")


def jobs(*extra):
    return workflow.parse(workflow_text([("test", None, []), ("build", "test", []), *extra,
                                         ("pub-a", "[build]", []), ("pub-b", "[build]", [])]))


class ReleaseGateTest(unittest.TestCase):

    def check(self, js, non_gating):
        return releasegate.check(js, dict.fromkeys(non_gating, "reason"), GATES)

    def test_transitively_gating_jobs_pass(self):
        self.assertEqual(self.check(jobs(), ["pub-a", "pub-b"]), [])

    def test_a_job_nothing_waits_for_fails(self):
        self.assertEqual(self.check(jobs(("lint", None, [])), ["pub-a", "pub-b"]),
                         ["publish.yml: pub-a, pub-b does not wait for lint -- add it to the needs, or to "
                          "release-gate-exemptions.txt with the reason it may stay red"])

    def test_gating_only_one_publish_job_fails(self):
        js = workflow.parse(workflow_text([("lint", None, []), ("pub-a", "[lint]", []), ("pub-b", None, [])]))
        self.assertEqual(len(self.check(js, ["pub-a", "pub-b"])), 1)

    def test_a_listed_job_may_stay_outside(self):
        self.assertEqual(self.check(jobs(("lint", None, [])), ["pub-a", "pub-b", "lint"]), [])

    def test_a_stale_exemption_fails(self):
        self.assertIn("remove the stale exemption", self.check(jobs(), ["pub-a", "pub-b", "build"])[0])
        self.assertEqual(self.check(jobs(), ["pub-a", "pub-b", "gone"]),
                         ["release-gate-exemptions.txt lists gone, which is no job of publish.yml"])

    def test_reads_the_exemptions_file(self):
        self.assertEqual(releasegate.read_exemptions("# c\n\na: why: because\n b :x\n"),
                         {"a": "why: because", "b": "x"})

    def test_an_exemption_without_a_reason_is_an_error(self):
        for text in ("a\n", "a:\n", "a:  \n", ": x\n", "a: x\na: y\n"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                releasegate.read_exemptions(text)

    def test_the_repository_passes(self):
        with open(os.path.join(REPO, ".github", "workflows", "publish.yml"), encoding="utf-8") as f:
            jobs = workflow.parse(f.read())
        with open(os.path.join(REPO, releasegate.EXEMPTIONS_FILE), encoding="utf-8") as f:
            self.assertEqual(releasegate.check(jobs, releasegate.read_exemptions(f.read())), [])


if __name__ == "__main__":
    unittest.main()
