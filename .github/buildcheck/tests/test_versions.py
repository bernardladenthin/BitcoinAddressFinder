# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

import io
import os
import tempfile
import unittest
from unittest import mock

from buildcheck import versions
from buildcheck.tests.helpers import REPO


def pom(body, properties=""):
    return (f'<project xmlns="http://maven.apache.org/POM/4.0.0"><properties>{properties}</properties>'
            f'{body}</project>')


PARENT = pom("", "<junit.version>6.1.3</junit.version><nested>${junit.version}</nested><palantir>2.99.0</palantir>")
CHILD = pom("""<dependencies>
  <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>
    <version>${junit.version}</version></dependency>
  <dependency><groupId>a</groupId><artifactId>nested</artifactId><version>${nested}</version></dependency>
  <dependency><groupId>a</groupId><artifactId>unresolved</artifactId><version>${nowhere}</version></dependency>
  <dependency><groupId>a</groupId><artifactId>managed</artifactId></dependency>
  <dependency><groupId>net.ladenthin</groupId><artifactId>llama</artifactId><version>5.2.0</version></dependency>
</dependencies>
<build><plugins><plugin><artifactId>maven-jar-plugin</artifactId><version>3.5.0</version>
  <configuration><annotationProcessorPaths><path><groupId>com.google.errorprone</groupId>
    <artifactId>error_prone_core</artifactId><version>2.50.0</version></path></annotationProcessorPaths>
  </configuration></plugin>
  <plugin><groupId>com.diffplug.spotless</groupId><artifactId>spotless-maven-plugin</artifactId><version>3.10.3</version>
    <configuration><java><palantirJavaFormat><version>${palantir}</version></palantirJavaFormat></java></configuration>
  </plugin></plugins></build>""")


class CoordinatesTest(unittest.TestCase):

    def test_reads_plugins_dependencies_and_processor_paths_with_properties_resolved(self):
        self.assertEqual(versions.coordinates([PARENT, CHILD]), {
            "org.junit.jupiter:junit-jupiter": {"6.1.3"},
            "a:nested": {"6.1.3"},
            "org.apache.maven.plugins:maven-jar-plugin": {"3.5.0"},
            "com.google.errorprone:error_prone_core": {"2.50.0"},
            "com.diffplug.spotless:spotless-maven-plugin": {"3.10.3"},
            "com.palantir.javaformat:palantir-java-format": {"2.99.0"},
        })

    def test_a_repository_using_two_versions_of_one_coordinate_keeps_both(self):
        other = pom('<dependencies><dependency><groupId>org.junit.jupiter</groupId>'
                    '<artifactId>junit-jupiter</artifactId><version>6.0.0</version></dependency></dependencies>')
        self.assertEqual(versions.coordinates([PARENT, CHILD, other])["org.junit.jupiter:junit-jupiter"],
                         {"6.0.0", "6.1.3"})


class CompareTest(unittest.TestCase):

    def test_only_coordinates_both_use_and_only_differences(self):
        own = {"j:u": {"1"}, "s:p": {"2"}, "only:here": {"3"}}
        others = {"r1": {"j:u": {"1"}, "s:p": {"9"}}, "r2": {"only:there": {"4"}}}
        self.assertEqual(versions.compare(own, others),
                         ["s:p is 2 here and 9 in r1: bump it in every repository"])


class GradleKotlinTest(unittest.TestCase):

    def test_plugins_are_read_from_every_kts_file_outside_build_output(self):
        with tempfile.TemporaryDirectory() as root:
            os.makedirs(os.path.join(root, "app", "build"))
            with open(os.path.join(root, "app", "build.gradle.kts"), "w", encoding="utf-8") as f:
                f.write('plugins {\n    id("com.android.application") version "9.4.0"\n'
                        '    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"\n'
                        '    kotlin("jvm") version "2.4.21"\n}\n')
            with open(os.path.join(root, "app", "build", "stale.gradle.kts"), "w", encoding="utf-8") as f:
                f.write('kotlin("jvm") version "1.0.0"\n')
            self.assertEqual(versions.gradle_kotlin_plugins(root), [
                ("app/build.gradle.kts", "org.jetbrains.kotlin.plugin.compose", "2.4.20"),
                ("app/build.gradle.kts", "org.jetbrains.kotlin.jvm", "2.4.21"),
            ])

    def test_a_plugin_behind_the_poms_kotlin_is_a_warning_and_nothing_else_is(self):
        own = {"org.jetbrains.kotlin:kotlin-stdlib": {"2.4.21"}, "org.jetbrains.kotlinx:kotlinx-coroutines-core": {"1.11.0"}}
        plugins = [("app/build.gradle.kts", "org.jetbrains.kotlin.plugin.compose", "2.4.20"),
                   ("app/build.gradle.kts", "org.jetbrains.kotlin.jvm", "2.4.21")]
        self.assertEqual(versions.compare_gradle_kotlin(own, plugins), [
            "org.jetbrains.kotlin.plugin.compose is 2.4.20 in app/build.gradle.kts and the POMs use Kotlin 2.4.21: "
            "Dependabot bumps only the POMs, bump the Gradle plugin with them"])
        self.assertEqual(versions.compare_gradle_kotlin({"a:b": {"1"}}, plugins), [])
        self.assertEqual(versions.compare_gradle_kotlin(own, []), [])


class MainTest(unittest.TestCase):

    def run_main(self, siblings):
        out, err = io.StringIO(), io.StringIO()

        def fetcher(repo, path):
            if repo not in siblings:
                raise OSError("unreachable")
            return siblings[repo]
        with mock.patch.object(versions, "current_repo", return_value="streambuffer"), \
                mock.patch.object(versions, "read_local", return_value=[PARENT, CHILD]):
            code = versions.main(["x"], "/nowhere", fetcher, out, err)
        return code, out.getvalue(), err.getvalue()

    def test_differences_are_warnings_and_never_fail(self):
        bumped = pom('<dependencies><dependency><groupId>org.junit.jupiter</groupId>'
                     '<artifactId>junit-jupiter</artifactId><version>6.1.4</version></dependency></dependencies>')
        code, out, err = self.run_main({"srcmorph": bumped, "BitcoinAddressFinder": CHILD.replace(
            "${junit.version}", "6.1.3").replace("${nested}", "6.1.3")})
        self.assertEqual(code, 0)
        self.assertIn("::warning::org.junit.jupiter:junit-jupiter is 6.1.3 here and 6.1.4 in srcmorph", err)
        self.assertIn("could not read java-llama.cpp's pom.xml", err)
        self.assertNotIn("BitcoinAddressFinder:", err)
        self.assertIn("1 version differences", out)

    def test_a_gradle_kotlin_plugin_behind_the_poms_is_reported_with_the_other_warnings(self):
        kotlin = pom('<dependencies><dependency><groupId>org.jetbrains.kotlin</groupId>'
                     '<artifactId>kotlin-stdlib</artifactId><version>2.4.21</version></dependency></dependencies>')
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(versions, "current_repo", return_value="java-llama.cpp"), \
                mock.patch.object(versions, "read_local", return_value=[kotlin]), \
                mock.patch.object(versions, "gradle_kotlin_plugins", return_value=[
                    ("android-llmservice/app/build.gradle.kts", "org.jetbrains.kotlin.plugin.compose", "2.4.20")]):
            self.assertEqual(versions.main(["x"], "/nowhere", lambda repo, path: kotlin, out, err), 0)
        self.assertIn("::warning::org.jetbrains.kotlin.plugin.compose is 2.4.20 in android-llmservice/app/build.gradle.kts "
                      "and the POMs use Kotlin 2.4.21", err.getvalue())
        self.assertIn("1 version differences", out.getvalue())

    def test_a_missing_pom_skips_that_file_not_the_repository(self):
        out, err = io.StringIO(), io.StringIO()
        bumped = pom('<dependencies><dependency><groupId>org.junit.jupiter</groupId>'
                     '<artifactId>junit-jupiter</artifactId><version>6.1.4</version></dependency></dependencies>')

        def fetcher(repo, path):
            if repo == "srcmorph" and path == "pom.xml":
                return bumped
            raise OSError("404")
        with mock.patch.object(versions, "current_repo", return_value="streambuffer"), \
                mock.patch.object(versions, "read_local", return_value=[PARENT, CHILD]):
            self.assertEqual(versions.main(["x"], "/nowhere", fetcher, out, err), 0)
        self.assertIn("could not read srcmorph's srcmorph/pom.xml", err.getvalue())
        self.assertIn("6.1.4 in srcmorph", err.getvalue())

    def test_arguments_are_refused(self):
        self.assertEqual(versions.main(["x", "--offline"], REPO, err=io.StringIO()), 2)


class RepositoryTest(unittest.TestCase):

    def test_this_repository_s_poms_are_listed_and_readable(self):
        repo = versions.current_repo(REPO)
        if repo not in versions.POMS:
            self.skipTest(f"checked out as {repo}, not under its repository name")
        found = versions.coordinates(versions.read_local(REPO, repo))
        self.assertTrue(found, "no versioned coordinate found -- the POM list in versions.POMS is stale")


if __name__ == "__main__":
    unittest.main()
