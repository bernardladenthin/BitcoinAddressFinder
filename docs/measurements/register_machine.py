#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
"""Detect this machine's hardware and register it in ``machines.json``.

Every measurement row in this directory carries a ``machine_id`` referencing that registry, so
results from different hardware can coexist instead of overwriting each other. Machine metadata is
nested (CPU, caches, storage, GPU), which is why it lives in JSON while the measurements stay flat
CSV.

Run this once per machine before benchmarking::

    python docs/measurements/register_machine.py                 # detect and write
    python docs/measurements/register_machine.py --dry-run       # show what would be written
    python docs/measurements/register_machine.py --id my-box     # override the generated id
    python docs/measurements/register_machine.py --set gpu="RTX 4090" --set storage="WD SN850X"

Re-running is idempotent: the same machine updates its own entry rather than creating a duplicate.
Fields that cannot be detected are left as ``null`` and can be filled with ``--set``.

**``l3_mb`` is the field that matters most.** The lookup-latency curves are shaped by L3 cache size —
the crossover between BINARY_FUSE_8 and BLOCKED_BLOOM sits where the fuse array outgrows L3 — so the
plots annotate that boundary per machine. If detection fails, set it manually.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import platform
import re
import shutil
import subprocess
import sys
from datetime import date, timezone, datetime

HERE = pathlib.Path(__file__).resolve().parent
REGISTRY = HERE / "machines.json"


def _run(cmd: list[str]) -> str:
    """Best-effort command execution; returns '' rather than raising, detection is optional."""
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=25, check=False)
        return (out.stdout or "") + (out.stderr or "")
    except (OSError, subprocess.SubprocessError):
        return ""


def _powershell(script: str) -> str:
    exe = shutil.which("powershell") or shutil.which("pwsh")
    return _run([exe, "-NoProfile", "-Command", script]) if exe else ""


def detect_cpu() -> dict:
    system = platform.system()
    info = {"model": None, "cores_physical": None, "cores_logical": None, "l3_mb": None}

    if system == "Windows":
        raw = _powershell(
            "$c = Get-CimInstance Win32_Processor | Select-Object -First 1;"
            "'{0}|{1}|{2}|{3}' -f $c.Name.Trim(), $c.NumberOfCores,"
            " $c.NumberOfLogicalProcessors, $c.L3CacheSize"
        )
        parts = raw.strip().splitlines()[-1].split("|") if raw.strip() else []
        if len(parts) == 4:
            info["model"] = parts[0].strip() or None
            info["cores_physical"] = _int(parts[1])
            info["cores_logical"] = _int(parts[2])
            l3_kb = _int(parts[3])  # Win32_Processor reports KB
            info["l3_mb"] = round(l3_kb / 1024) if l3_kb else None

    elif system == "Linux":
        cpuinfo = pathlib.Path("/proc/cpuinfo")
        if cpuinfo.exists():
            text = cpuinfo.read_text(errors="ignore")
            match = re.search(r"^model name\s*:\s*(.+)$", text, re.MULTILINE)
            if match:
                info["model"] = match.group(1).strip()
        lscpu = _run(["lscpu"])
        # L3 appears as e.g. "L3 cache: 32 MiB" (or KiB on older util-linux).
        match = re.search(r"^L3 cache:\s*([\d.]+)\s*([KMG])iB", lscpu, re.MULTILINE)
        if match:
            value, unit = float(match.group(1)), match.group(2)
            info["l3_mb"] = round(value / 1024) if unit == "K" else round(value * (1024 if unit == "G" else 1))
        match = re.search(r"^Core\(s\) per socket:\s*(\d+)", lscpu, re.MULTILINE)
        sockets = re.search(r"^Socket\(s\):\s*(\d+)", lscpu, re.MULTILINE)
        if match:
            info["cores_physical"] = int(match.group(1)) * (int(sockets.group(1)) if sockets else 1)

    elif system == "Darwin":
        info["model"] = _run(["sysctl", "-n", "machdep.cpu.brand_string"]).strip() or None
        info["cores_physical"] = _int(_run(["sysctl", "-n", "hw.physicalcpu"]))
        l3_bytes = _int(_run(["sysctl", "-n", "hw.l3cachesize"]))
        info["l3_mb"] = round(l3_bytes / 1024 / 1024) if l3_bytes else None

    if not info["cores_logical"]:
        import os

        info["cores_logical"] = os.cpu_count()
    if not info["model"]:
        info["model"] = platform.processor() or None
    return info


def detect_ram_gb() -> float | None:
    system = platform.system()
    if system == "Windows":
        raw = _powershell("(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory")
        total = _int(raw)
        return round(total / 1024**3, 1) if total else None
    if system == "Linux":
        meminfo = pathlib.Path("/proc/meminfo")
        if meminfo.exists():
            match = re.search(r"^MemTotal:\s*(\d+)\s*kB", meminfo.read_text(errors="ignore"), re.MULTILINE)
            if match:
                return round(int(match.group(1)) / 1024**2, 1)
    if system == "Darwin":
        total = _int(_run(["sysctl", "-n", "hw.memsize"]))
        return round(total / 1024**3, 1) if total else None
    return None


def detect_gpu() -> str | None:
    system = platform.system()
    nvidia = _run(["nvidia-smi", "--query-gpu=name", "--format=csv,noheader"])
    names = [line.strip() for line in nvidia.splitlines() if line.strip() and "not found" not in line.lower()]
    if names:
        return ", ".join(dict.fromkeys(names))
    if system == "Windows":
        raw = _powershell(
            "(Get-CimInstance Win32_VideoController | Where-Object { $_.Name -notmatch 'Basic|Remote' }"
            " | Select-Object -ExpandProperty Name) -join ', '"
        )
        return raw.strip() or None
    if system == "Linux":
        lspci = _run(["lspci"])
        gpus = [ln.split(":", 2)[-1].strip() for ln in lspci.splitlines() if re.search(r"VGA|3D controller", ln)]
        return ", ".join(gpus) or None
    return None


def detect_os() -> dict:
    system = platform.system()
    if system == "Windows":
        raw = _powershell("$o=Get-CimInstance Win32_OperatingSystem; '{0}|{1}' -f $o.Caption, $o.Version")
        parts = raw.strip().splitlines()[-1].split("|") if raw.strip() else []
        if len(parts) == 2:
            return {"name": parts[0].strip(), "version": parts[1].strip()}
    if system == "Linux":
        release = pathlib.Path("/etc/os-release")
        if release.exists():
            fields = dict(
                line.split("=", 1) for line in release.read_text(errors="ignore").splitlines() if "=" in line
            )
            return {"name": fields.get("NAME", "Linux").strip('"'), "version": platform.release()}
    return {"name": system, "version": platform.release()}


def detect_jdk() -> str | None:
    raw = _run(["java", "-version"])
    match = re.search(r'version "([^"]+)"', raw)
    return match.group(1) if match else None


def _int(text: str) -> int | None:
    match = re.search(r"\d+", text or "")
    return int(match.group(0)) if match else None


def make_machine_id(cpu: dict, ram_gb: float | None, os_info: dict) -> str:
    """Readable, deterministic id: same hardware always yields the same value."""
    model = (cpu.get("model") or "unknown").lower()
    # Drop vendor noise but keep the family ("ryzen", "core"), which is what makes the id readable.
    model = re.sub(r"\bwith\b.*$", "", model)  # "... with Radeon Graphics"
    model = re.sub(r"\b(amd|intel|cpu|processor)\b|\(r\)|\(tm\)", " ", model)
    model = re.sub(r"@.*$", "", model)
    model = re.sub(r"[^a-z0-9]+", "", model) or "cpu"
    ram = f"{int(ram_gb)}g" if ram_gb else "xg"
    os_name = (os_info.get("name") or "os").lower()
    if "windows" in os_name:
        os_slug = "win" + (re.search(r"\b(\d+)\b", os_name).group(1) if re.search(r"\b(\d+)\b", os_name) else "")
    elif "darwin" in os_name or "mac" in os_name:
        os_slug = "macos"
    else:
        os_slug = re.sub(r"[^a-z0-9]+", "", os_name)[:8] or "linux"
    return f"{model}-{ram}-{os_slug}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--id", help="override the generated machine_id")
    parser.add_argument("--notes", default="", help="free-text note stored with the entry")
    parser.add_argument(
        "--set",
        action="append",
        default=[],
        metavar="KEY=VALUE",
        help="override a detected field, e.g. --set storage='Samsung 990 PRO' or --set cpu.l3_mb=32",
    )
    parser.add_argument("--dry-run", action="store_true", help="print the entry without writing")
    args = parser.parse_args()

    cpu = detect_cpu()
    ram_gb = detect_ram_gb()
    os_info = detect_os()
    entry = {
        "cpu": cpu,
        "ram_gb": ram_gb,
        "gpu": detect_gpu(),
        "storage": None,  # not reliably detectable; set with --set storage=...
        "os": os_info,
        "jdk": detect_jdk(),
        "registered": date.today().isoformat(),
        "notes": args.notes,
    }

    for override in args.set:
        if "=" not in override:
            print(f"ignoring malformed --set {override!r} (expected KEY=VALUE)", file=sys.stderr)
            continue
        key, value = override.split("=", 1)
        target, _, leaf = key.rpartition(".")
        holder = entry.setdefault(target, {}) if target else entry
        if not isinstance(holder, dict):
            print(f"ignoring --set {key}: {target!r} is not a section", file=sys.stderr)
            continue
        holder[leaf] = int(value) if value.isdigit() else value

    machine_id = args.id or make_machine_id(cpu, ram_gb, os_info)

    registry = {"machines": {}}
    if REGISTRY.exists():
        registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
        registry.setdefault("machines", {})

    existing = registry["machines"].get(machine_id)
    if existing:
        entry["registered"] = existing.get("registered", entry["registered"])
        entry["notes"] = args.notes or existing.get("notes", "")
        # Keep manually supplied values that detection cannot recover.
        for field in ("storage", "gpu"):
            if entry.get(field) is None:
                entry[field] = existing.get(field)
    entry["updated"] = datetime.now(timezone.utc).date().isoformat()

    print(f"machine_id: {machine_id}")
    print(json.dumps(entry, indent=2))
    if entry["cpu"].get("l3_mb") is None:
        print(
            "\n!! l3_mb could not be detected. The latency plots annotate the L3 boundary from it,\n"
            "   so please set it explicitly:  --set cpu.l3_mb=<megabytes>",
            file=sys.stderr,
        )

    if args.dry_run:
        print("\n(dry run — nothing written)")
        return 0

    registry["machines"][machine_id] = entry
    REGISTRY.write_text(json.dumps(registry, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    action = "updated" if existing else "added"
    print(f"\n{action} {machine_id} in {REGISTRY.relative_to(HERE.parent.parent)}")
    print(f"Use machine_id={machine_id} in every measurement row you append.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
