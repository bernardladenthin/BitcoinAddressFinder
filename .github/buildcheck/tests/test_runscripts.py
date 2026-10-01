# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

import io
import shutil
import unittest

from buildcheck import runscripts
from buildcheck.tests.helpers import REPO, workflow_text

BASH = shutil.which("bash")


def shells(text):
    return [(script.line, script.shell) for script in runscripts.scripts(text)]


def texts(text):
    return [script.text for script in runscripts.scripts(text)]


class ValueTest(unittest.TestCase):

    def test_literal_block_keeps_its_lines_and_drops_the_indentation(self):
        text = workflow_text([("a", None, ["      - run: |", "          echo a \\", "            b", "",
                                           "          echo c", "", "      - run: echo d"])])
        self.assertEqual(texts(text), ["echo a \\\n  b\n\necho c", "echo d"])

    def test_folded_block_joins_lines_and_keeps_the_more_indented_ones(self):
        text = workflow_text([("a", None, ["      - run: >-", "          mvn -B", "          -DskipTests",
                                           "            verify", "", "          echo done"])])
        self.assertEqual(texts(text), ["mvn -B -DskipTests\n  verify\necho done"])

    def test_plain_scalar_drops_its_comment_and_folds_its_continuation(self):
        text = workflow_text([("a", None, ["      - run: mvn -B test  # the suite", "      - run: mvn -B",
                                           "          -Dx=1", "      - name: n", "        run: echo '$#'"])])
        self.assertEqual(texts(text), ["mvn -B test", "mvn -B -Dx=1", "echo '$#'"])

    def test_single_quoted_is_unquoted_and_double_quoted_skipped(self):
        text = workflow_text([("a", None, ["      - run: 'echo ''x'''", '      - run: "echo \\"x\\""'])])
        self.assertEqual(texts(text), ["echo 'x'"])

    def test_a_run_key_that_is_not_a_step_key_is_not_mistaken_for_one(self):
        text = "name: t\non:\n  push:\n# run: | in a comment\njobs:\n  a:\n    runs-on: ubuntu-latest\n" \
               "    steps:\n      - run: echo a\n"
        self.assertEqual(texts(text), ["echo a"])


class ShellTest(unittest.TestCase):

    def test_step_shell_before_or_after_run(self):
        text = workflow_text([("a", None, ["      - shell: pwsh", "        run: Get-Item x",
                                           "      - run: |", "          dir", "        shell: cmd",
                                           "      - run: echo a", "        shell: bash -el {0}",
                                           "      - run: echo b", "        shell: C:\\msys64\\usr\\bin\\bash.exe -e {0}"])])
        self.assertEqual([shell for _, shell in shells(text)], ["pwsh", "cmd", "bash", "bash"])

    def test_windows_runner_defaults_to_powershell_others_to_bash(self):
        text = workflow_text([("w", None, ["      - run: Get-Item x"]), ("u", None, ["      - run: ls"])])
        text = text.replace("runs-on: ubuntu-latest", "runs-on: windows-2025-vs2026", 1)
        self.assertEqual([shell for _, shell in shells(text)], ["pwsh", "bash"])

    def test_a_runner_from_an_expression_is_windows_only_by_the_step_condition(self):
        text = workflow_text([("m", None, ["      - if: runner.os == 'Windows'", "        run: Get-Item x",
                                           "      - run: ls"])])
        text = text.replace("ubuntu-latest", "${{ matrix.windows-runner }}")
        self.assertEqual([shell for _, shell in shells(text)], ["pwsh", "bash"])

    def test_job_defaults_win_over_workflow_defaults_and_both_over_the_runner(self):
        text = workflow_text([("j", None, ["      - run: dir"]), ("k", None, ["      - run: Get-Item x"])])
        text = text.replace("jobs:\n", "defaults:\n  run:\n    shell: pwsh\njobs:\n")
        text = text.replace("  j:\n", "  j:\n    defaults:\n      run:\n        working-directory: x\n"
                                      "        shell: cmd\n")
        self.assertEqual([shell for _, shell in shells(text)], ["cmd", "pwsh"])

    def test_composite_action_steps(self):
        text = ("name: a\nruns:\n  using: composite\n  steps:\n    - name: x\n      shell: pwsh\n"
                "      run: Get-Item x\n    - shell: bash\n      run: ls\n")
        self.assertEqual(shells(text), [(7, "pwsh"), (9, "bash")])


class CheckTest(unittest.TestCase):

    def test_only_bash_scripts_reach_the_parser_and_an_unknown_shell_is_an_error(self):
        seen = []
        text = workflow_text([("a", None, ["      - run: ls", "      - run: Get-Item x", "        shell: pwsh",
                                           "      - run: print(1)", "        shell: python",
                                           "      - run: x", "        shell: fish {0}"])])
        errors, checked = runscripts.check("w.yml", text, lambda s: seen.append(s))
        self.assertEqual((seen, checked), (["ls"], 1))
        self.assertEqual(errors, ["w.yml:13: unknown shell 'fish' -- add it to runscripts.py"])

    @unittest.skipUnless(BASH, "bash not installed")
    def test_a_lost_line_continuation_is_reported_at_its_line(self):
        text = workflow_text([("a", None, ["      - run: |", "          echo ${{ github.sha }}",
                                           "          grep -q x file", "            || exit 1"])])
        errors, checked = runscripts.check("w.yml", text)
        self.assertEqual(checked, 1)
        self.assertEqual(len(errors), 1)
        self.assertTrue(errors[0].startswith("w.yml:11: "), errors[0])
        self.assertIn("`||'", errors[0])

    @unittest.skipUnless(BASH, "bash not installed")
    def test_continued_lines_expressions_and_heredocs_parse(self):
        text = workflow_text([("a", None, ["      - run: |", "          grep -q x file \\",
                                           "            || exit 1", "          cat <<EOF",
                                           "          ${{ toJSON(matrix) }}", "          EOF",
                                           "      - run: >", "          echo a", "          && echo b"])])
        self.assertEqual(runscripts.check("w.yml", text), ([], 2))

    @unittest.skipUnless(BASH, "bash not installed")
    def test_main_refuses_arguments(self):
        self.assertEqual(runscripts.main(["x", "-v"], REPO, err=io.StringIO()), 2)


class RepositoryTest(unittest.TestCase):

    @unittest.skipUnless(BASH, "bash not installed")
    def test_every_bash_script_of_this_repository_parses(self):
        out, err = io.StringIO(), io.StringIO()
        self.assertEqual(runscripts.main(["x"], REPO, out, err), 0, err.getvalue())
        self.assertNotIn(" 0 bash run scripts", out.getvalue())


if __name__ == "__main__":
    unittest.main()
