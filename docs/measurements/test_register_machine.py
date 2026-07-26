#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
"""Tests for ``register_machine.py``.

Run from the repository root::

    python -m unittest discover -s docs/measurements -p 'test_*.py'

**Standard library only, on purpose.** The script under test has no dependencies, is meant to be
downloadable as a single file and run by a contributor on their own machine, and is the only Python
in the repository that anybody executes. Adding pytest to make its tests run would put a dependency
in front of exactly the people the script exists to make things easy for.

**Why these tests exist.** ``detect_gpu`` shipped a bug that hid every non-NVIDIA GPU: it returned as
soon as ``nvidia-smi`` named anything, so the platform-wide enumeration below it never ran. A
contributor's laptop recorded its RTX 500 and silently dropped its Intel Arc — a device this project
runs on, whose measurements were the point of the exercise. That bug is invisible to any machine
with one GPU or no NVIDIA card, which is to say invisible on most developer machines. It needs a
test that fakes the detectors rather than a machine that happens to reproduce it.
"""

from __future__ import annotations

import io
import json
import pathlib
import sys
import tempfile
import unittest
from contextlib import contextmanager, redirect_stdout
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import register_machine  # noqa: E402


@contextmanager
def _stubbed_detection():
    """Replaces the hardware probes for tests that are about the registry, not about detection.

    Each ``main()`` call otherwise shells out to wmic/PowerShell/java, which costs seconds per test
    and makes the result depend on the machine running the suite — neither of which these tests are
    trying to measure. Detection itself is covered by :class:`DetectGpuTest`, which fakes the same
    seams one level down.
    """
    with mock.patch.object(
        register_machine,
        "detect_cpu",
        return_value={"model": "Test CPU", "cores_physical": 1, "cores_logical": 1, "l3_mb": 1},
    ), mock.patch.object(register_machine, "detect_ram_gb", return_value=1.0), mock.patch.object(
        register_machine, "detect_os", return_value={"name": "Test OS", "version": "1.0"}
    ), mock.patch.object(
        register_machine, "detect_jdk", return_value="21.0.0"
    ), mock.patch.object(
        register_machine, "detect_gpu", return_value=[]
    ):
        yield


class DetectGpuTest(unittest.TestCase):
    """``detect_gpu`` must report every GPU the operating system enumerates."""

    def test_nvidia_and_platform_sources_are_merged_not_short_circuited(self):
        """The regression that started this: nvidia-smi answering must not silence the rest.

        The old implementation returned here and never asked Windows, so a laptop with a discrete
        NVIDIA card plus an integrated GPU recorded only the NVIDIA.
        """
        with mock.patch.object(register_machine, "_run", return_value="NVIDIA RTX 500 Ada\n"), mock.patch.object(
            register_machine, "_powershell", return_value="NVIDIA RTX 500 Ada, Intel(R) Arc(TM) Pro Graphics"
        ), mock.patch.object(register_machine.platform, "system", return_value="Windows"):
            gpus = register_machine.detect_gpu()

        self.assertEqual(gpus, ["NVIDIA RTX 500 Ada", "Intel(R) Arc(TM) Pro Graphics"])

    def test_returns_a_list_so_a_second_gpu_needs_no_separator_parsing(self):
        with mock.patch.object(register_machine, "_run", return_value=""), mock.patch.object(
            register_machine, "_powershell", return_value="GPU A, GPU B"
        ), mock.patch.object(register_machine.platform, "system", return_value="Windows"):
            gpus = register_machine.detect_gpu()

        self.assertIsInstance(gpus, list)
        self.assertEqual(gpus, ["GPU A", "GPU B"])

    def test_a_name_contained_in_another_is_not_recorded_twice(self):
        """nvidia-smi and the OS list name the same card differently often enough to matter."""
        with mock.patch.object(register_machine, "_run", return_value="NVIDIA RTX 500 Ada\n"), mock.patch.object(
            register_machine, "_powershell", return_value="NVIDIA RTX 500 Ada Generation Laptop GPU, Intel Arc"
        ), mock.patch.object(register_machine.platform, "system", return_value="Windows"):
            gpus = register_machine.detect_gpu()

        self.assertEqual(len(gpus), 2, f"expected the duplicate to be collapsed, got {gpus}")
        self.assertIn("Intel Arc", gpus)

    def test_nothing_detected_yields_an_empty_list_not_none(self):
        """An empty list is what the "keep the previous value" path checks for."""
        with mock.patch.object(register_machine, "_run", return_value=""), mock.patch.object(
            register_machine, "_powershell", return_value=""
        ), mock.patch.object(register_machine.platform, "system", return_value="Windows"):
            self.assertEqual(register_machine.detect_gpu(), [])

    def test_linux_enumeration_is_reached_when_nvidia_smi_is_absent(self):
        lspci = "01:00.0 VGA compatible controller: Advanced Micro Devices RX 7900\n00:02.0 Audio device: Foo\n"

        def fake_run(cmd):
            return lspci if cmd[:1] == ["lspci"] else ""

        with mock.patch.object(register_machine, "_run", side_effect=fake_run), mock.patch.object(
            register_machine.platform, "system", return_value="Linux"
        ):
            gpus = register_machine.detect_gpu()

        self.assertEqual(len(gpus), 1)
        self.assertIn("Advanced Micro Devices RX 7900", gpus[0])


class ListFieldOverrideTest(unittest.TestCase):
    """``--set`` on a list field takes a comma-separated value, so one flag supplies every value."""

    def _run_main(self, argv, registry_path):
        with _stubbed_detection(), mock.patch.object(
            register_machine, "REGISTRY", registry_path
        ), mock.patch.object(sys, "argv", ["register_machine.py", *argv]):
            buffer = io.StringIO()
            with redirect_stdout(buffer):
                exit_code = register_machine.main()
            return exit_code, buffer.getvalue()

    def test_set_gpu_with_two_values_is_stored_as_two_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            registry = pathlib.Path(directory) / "machines.json"
            self._run_main(
                ["--id", "testbox", "--set", "gpu=RTX 500 Ada,Arc Pro Graphics", "--set", "storage=NVMe X"],
                registry,
            )
            entry = json.loads(registry.read_text(encoding="utf-8"))["machines"]["testbox"]

        self.assertEqual(entry["gpu"], ["RTX 500 Ada", "Arc Pro Graphics"])
        self.assertEqual(entry["storage"], "NVMe X", "a non-list field must still be stored verbatim")

    def test_a_non_list_field_containing_a_comma_is_not_split(self):
        with tempfile.TemporaryDirectory() as directory:
            registry = pathlib.Path(directory) / "machines.json"
            self._run_main(["--id", "testbox", "--set", "storage=Samsung 990 PRO, 4TB"], registry)
            entry = json.loads(registry.read_text(encoding="utf-8"))["machines"]["testbox"]

        self.assertEqual(entry["storage"], "Samsung 990 PRO, 4TB")

    def test_dry_run_writes_nothing(self):
        """Contributors are asked to run this on their own machine; it must be honest about that."""
        with tempfile.TemporaryDirectory() as directory:
            registry = pathlib.Path(directory) / "machines.json"
            _, output = self._run_main(["--id", "testbox", "--dry-run"], registry)

        self.assertFalse(registry.exists(), "--dry-run created a file")
        self.assertIn("dry run", output)


class RegistryRoundTripTest(unittest.TestCase):
    """A re-run must update the machine in place and must not lose hand-curated values."""

    def _write(self, registry_path, argv):
        with _stubbed_detection(), mock.patch.object(
            register_machine, "REGISTRY", registry_path
        ), mock.patch.object(sys, "argv", ["register_machine.py", *argv]):
            with redirect_stdout(io.StringIO()):
                register_machine.main()

    def test_a_hand_curated_gpu_list_survives_a_rerun_that_detects_nothing(self):
        """An empty list means "nothing detected", not "no GPUs".

        This is the trap the ``is None`` check used to fall into: a list is not ``None``, so an empty
        detection would have overwritten a list somebody typed in by hand — on the very machines
        where detection is known to fail.
        """
        with tempfile.TemporaryDirectory() as directory:
            registry = pathlib.Path(directory) / "machines.json"
            self._write(registry, ["--id", "testbox", "--set", "gpu=GPU A,GPU B", "--set", "storage=NVMe X"])
            self._write(registry, ["--id", "testbox", "--set", "gpu="])
            entry = json.loads(registry.read_text(encoding="utf-8"))["machines"]["testbox"]

        self.assertEqual(entry["gpu"], ["GPU A", "GPU B"])
        self.assertEqual(entry["storage"], "NVMe X")

    def test_rerunning_updates_the_same_entry_instead_of_duplicating_it(self):
        with tempfile.TemporaryDirectory() as directory:
            registry = pathlib.Path(directory) / "machines.json"
            self._write(registry, ["--id", "testbox", "--set", "gpu=GPU A"])
            self._write(registry, ["--id", "testbox", "--set", "gpu=GPU B"])
            machines = json.loads(registry.read_text(encoding="utf-8"))["machines"]

        self.assertEqual(list(machines), ["testbox"])
        self.assertEqual(machines["testbox"]["gpu"], ["GPU B"])

    def test_the_registry_stays_sorted_and_reloadable(self):
        with tempfile.TemporaryDirectory() as directory:
            registry = pathlib.Path(directory) / "machines.json"
            self._write(registry, ["--id", "zulu", "--set", "gpu=GPU Z"])
            self._write(registry, ["--id", "alpha", "--set", "gpu=GPU A"])
            machines = json.loads(registry.read_text(encoding="utf-8"))["machines"]

        self.assertEqual(list(machines), ["alpha", "zulu"])


class MachineIdTest(unittest.TestCase):
    """The id keys every measurement row, so the same hardware must always produce the same one."""

    def test_same_hardware_yields_the_same_id(self):
        cpu = {"model": "Intel(R) Core(TM) Ultra 7 155H"}
        os_info = {"name": "Microsoft Windows 11 Pro", "version": "10.0.26200"}

        first = register_machine.make_machine_id(cpu, 95.5, os_info)
        second = register_machine.make_machine_id(dict(cpu), 95.5, dict(os_info))

        self.assertEqual(first, second)
        self.assertEqual(first, "coreultra7155h-95g-win11")

    def test_vendor_noise_is_stripped_but_the_family_is_kept(self):
        machine_id = register_machine.make_machine_id(
            {"model": "AMD Ryzen 7 5800H with Radeon Graphics"},
            63.3,
            {"name": "Microsoft Windows 11 Pro"},
        )

        self.assertEqual(machine_id, "ryzen75800h-63g-win11")


class ShippedRegistryTest(unittest.TestCase):
    """The registry checked into the repository must match the schema the script writes."""

    def test_every_shipped_entry_records_gpu_as_a_list(self):
        registry = json.loads((pathlib.Path(__file__).resolve().parent / "machines.json").read_text(encoding="utf-8"))

        for machine_id, entry in registry["machines"].items():
            with self.subTest(machine_id=machine_id):
                self.assertIsInstance(entry["gpu"], list, f"{machine_id} still stores gpu as {type(entry['gpu'])}")
                self.assertTrue(entry["gpu"], f"{machine_id} has an empty gpu list")


if __name__ == "__main__":
    unittest.main()
