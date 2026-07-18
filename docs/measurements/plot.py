#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
"""Render the measurement CSVs into plots and into the generated tables in docs/performance.md.

The CSVs in this directory are the single source of truth. Markdown cannot import data, so the
tables in performance.md are *generated* from the same CSVs and rewritten in place between
``<!-- BEGIN GENERATED:name -->`` / ``<!-- END GENERATED:name -->`` markers. Never hand-edit inside
those markers; edit the CSV and re-run this script.

Usage::

    python docs/measurements/plot.py                 # all machines
    python docs/measurements/plot.py --machine-id <id>   # restrict to one machine

Adding your own machine: run ``register_machine.py`` (writes ``machines.json``), run the benchmarks
(see README.md here), append the result rows carrying that ``machine_id``, and re-run this script. Plots draw
one line per machine, so results from different hardware can be compared directly.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys

import matplotlib

matplotlib.use("Agg")  # headless: never try to open a window
import matplotlib.pyplot as plt  # noqa: E402
import pandas as pd  # noqa: E402

HERE = pathlib.Path(__file__).resolve().parent
PLOTS = HERE / "plots"
PERFORMANCE_MD = HERE.parent / "performance.md"
REGISTRY = HERE / "machines.json"

# Stable colour per backend so every plot reads the same way.
COLOURS = {
    "BINARY_FUSE_8": "#1f77b4",
    "BINARY_FUSE_16": "#17becf",
    "BLOCKED_BLOOM": "#d62728",
    "BLOOM": "#7f7f7f",
    "HASHSET": "#2ca02c",
    "TRUNCATED_LONG_64": "#ff7f0e",
}
# Slowest last, so the legend reads best-to-worst.
ORDER = ["BINARY_FUSE_8", "BINARY_FUSE_16", "BLOCKED_BLOOM", "HASHSET", "BLOOM", "TRUNCATED_LONG_64"]


def _style(ax, title: str, xlabel: str, ylabel: str) -> None:
    ax.set_title(title, fontsize=12, pad=12)
    ax.set_xlabel(xlabel, fontsize=10)
    ax.set_ylabel(ylabel, fontsize=10)
    ax.grid(True, which="both", alpha=0.25, linewidth=0.6)
    ax.set_axisbelow(True)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)


def plot_lookup_latency(lookup: pd.DataFrame, machines: pd.DataFrame) -> pathlib.Path:
    """Latency vs database size, log-log. The crossover is the point of this figure."""
    fig, ax = plt.subplots(figsize=(9, 5.5), dpi=150)
    # Colour identifies the backend; line style identifies the machine. Without the second channel
    # the same backend on two machines draws in one colour and the figure becomes unreadable.
    styles = ["-", "--", ":", "-."]
    order_of_machines = sorted(lookup["machine_id"].unique())
    multi = len(order_of_machines) > 1
    for machine_id, per_machine in lookup.groupby("machine_id"):
        style = styles[order_of_machines.index(machine_id) % len(styles)]
        for backend in ORDER:
            rows = per_machine[per_machine["backend"] == backend].sort_values("entries")
            if rows.empty:
                continue
            label = f"{backend} ({machine_id})" if multi else backend
            ax.errorbar(
                rows["entries"],
                rows["ns_per_op"],
                yerr=rows["error_ns"],
                marker="o",
                markersize=4,
                linewidth=1.8,
                capsize=3,
                color=COLOURS.get(backend),
                linestyle=style,
                label=label,
            )
    ax.set_xscale("log")
    ax.set_yscale("log")
    _style(ax, "Lookup latency vs database size (lower is better)", "entries in filter", "ns per containsAddress")

    # Mark the L3 boundary that explains the crossover, once per machine that has data.
    # Binary Fuse 8 stores ~1.14 bytes/entry, so it leaves L3 at roughly this entry count.
    present = set(lookup["machine_id"].unique())
    for machine_id in sorted(present):
        machine = machines.get(machine_id, {})
        l3_mb = (machine.get("cpu") or {}).get("l3_mb")
        if not l3_mb:
            continue  # nothing to annotate without a cache size
        l3_mb = float(l3_mb)
        fuse_leaves_l3 = l3_mb * 1024 * 1024 / 1.14
        ax.axvline(fuse_leaves_l3, color="#444444", linestyle="--", linewidth=1.0, alpha=0.7)
        label = f"Fuse-8 outgrows L3 ({l3_mb:.0f} MB)"
        if len(present) > 1:
            label += f" — {machine_id}"
        ax.annotate(
            label,
            xy=(fuse_leaves_l3, ax.get_ylim()[1] * 0.55),
            xytext=(6, 0),
            textcoords="offset points",
            rotation=90,
            fontsize=8,
            color="#444444",
            va="top",
        )

    ax.legend(fontsize=8, frameon=False, ncol=2)
    fig.tight_layout()
    out = PLOTS / "filter_lookup_latency.png"
    fig.savefig(out)
    plt.close(fig)
    return out


def plot_k_sweep(k_sweep: pd.DataFrame) -> pathlib.Path:
    """False-positive rate vs k, one line per bit density. Shows the non-monotonic optimum."""
    fig, ax = plt.subplots(figsize=(9, 5.0), dpi=150)
    # FPR is hardware-independent, so curves from different machines at the same density overlap
    # exactly. Name the machine in the legend anyway, otherwise the duplicate labels read as a bug.
    multi = k_sweep["machine_id"].nunique() > 1
    for (machine_id, density), rows in k_sweep.groupby(["machine_id", "bits_per_entry_effective"]):
        rows = rows.sort_values("k")
        label = f"{density:.2f} bits/entry" + (f" — {machine_id}" if multi else "")
        line = ax.plot(
            rows["k"],
            rows["fpr"] * 100.0,
            marker="o",
            markersize=5,
            linewidth=1.8,
            label=label,
        )[0]
        best = rows.loc[rows["fpr"].idxmin()]
        ax.annotate(
            f"optimum k={int(best['k'])}",
            xy=(best["k"], best["fpr"] * 100.0),
            xytext=(0, -20),
            textcoords="offset points",
            ha="center",
            fontsize=8,
            color=line.get_color(),
            arrowprops={"arrowstyle": "->", "color": line.get_color(), "lw": 0.9},
        )
    ax.set_yscale("log")
    _style(ax, "Blocked Bloom: false-positive rate vs k (lower is better)", "k (bits set per key)", "measured FPR [%]")
    ax.legend(fontsize=9, frameon=False, title="bit density")
    fig.tight_layout()
    out = PLOTS / "blocked_bloom_k_sweep.png"
    fig.savefig(out)
    plt.close(fig)
    return out


def plot_sizing(sizing: pd.DataFrame) -> pathlib.Path:
    """FPR and lookup speed vs filter size — the size/accuracy trade-off on one axis pair."""
    fig, ax = plt.subplots(figsize=(9, 5.0), dpi=150)
    rows = sizing.sort_values("bits_per_entry_effective")
    ax.plot(rows["bits_per_entry_effective"], rows["fpr"] * 100.0, marker="o", color="#d62728", linewidth=1.8)
    ax.set_yscale("log")
    _style(
        ax,
        "Blocked Bloom: accuracy and speed vs filter size (k = 8)",
        "effective bits per entry",
        "measured FPR [%]",
    )
    twin = twin_axis(ax)
    twin.plot(
        rows["bits_per_entry_effective"],
        rows["lookups_per_sec"] / 1e6,
        marker="s",
        linestyle="--",
        color="#1f77b4",
        linewidth=1.6,
    )
    twin.set_ylabel("M lookups/s", fontsize=10, color="#1f77b4")
    twin.tick_params(axis="y", colors="#1f77b4")
    ax.plot([], [], marker="s", linestyle="--", color="#1f77b4", label="lookups/s (right axis)")
    ax.plot([], [], marker="o", color="#d62728", label="FPR (left axis)")
    ax.legend(fontsize=9, frameon=False)
    fig.tight_layout()
    out = PLOTS / "blocked_bloom_sizing.png"
    fig.savefig(out)
    plt.close(fig)
    return out


def plot_full_db(build: pd.DataFrame) -> pathlib.Path:
    """Full DB tier: the same-machine BINARY_FUSE_8 vs BLOCKED_BLOOM comparison, per machine.

    Deliberately shows build time and lookup throughput side by side, because the two disagree about
    which backend wins and that disagreement is the finding. Only the comparable arms are plotted:
    rows carrying an explicit note about a differing heap or cache state are excluded, since a
    -Xmx6g run is not comparable to a -Xmx48g one on this path.
    """
    full = build[(build["entries"] == 1_377_478_516) & (build["backend"].isin(["BINARY_FUSE_8", "BLOCKED_BLOOM"]))]
    # Keep only machines where BOTH backends were measured, i.e. a genuine same-machine control.
    paired = full.groupby("machine_id")["backend"].nunique()
    full = full[full["machine_id"].isin(paired[paired == 2].index)]
    full = full.sort_values(["machine_id", "backend"]).groupby(["machine_id", "backend"], as_index=False).last()

    machine_ids = sorted(full["machine_id"].unique())
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.6), dpi=150)
    width = 0.36
    for ax, (column, ylabel, title, scale) in zip(
        axes,
        [
            ("build_seconds", "build time [s]", "Build time (lower is better)", 1.0),
            ("lookups_per_sec", "M lookups/s", "Lookup throughput (higher is better)", 1e-6),
        ],
    ):
        for offset, backend in zip((-width / 2, width / 2), ("BINARY_FUSE_8", "BLOCKED_BLOOM")):
            values = [
                full[(full["machine_id"] == m) & (full["backend"] == backend)][column].iloc[0] * scale
                for m in machine_ids
            ]
            positions = [i + offset for i in range(len(machine_ids))]
            ax.bar(positions, values, width, label=backend, color=COLOURS[backend])
            for x, v in zip(positions, values):
                ax.text(x, v, f"{v:,.0f}" if scale == 1.0 else f"{v:.2f}", ha="center", va="bottom", fontsize=8)
        ax.set_xticks(range(len(machine_ids)))
        ax.set_xticklabels([m.replace("-win11", "") for m in machine_ids], fontsize=8)
        _style(ax, title, "", ylabel)
    axes[0].legend(fontsize=8, frameon=False)
    fig.suptitle(
        "Full DB tier (1.377 B entries): build agrees across machines, lookup does not",
        fontsize=11,
    )
    fig.tight_layout()
    out = PLOTS / "full_db_backends.png"
    fig.savefig(out)
    plt.close(fig)
    return out


def twin_axis(ax):
    """Second y-axis sharing the x-axis (kept separate so styling stays in one place)."""
    twin = ax.twinx()
    twin.spines["top"].set_visible(False)
    return twin


def latency_table(lookup: pd.DataFrame) -> str:
    """Markdown latency tables generated from the CSV, one per machine.

    Machines are never merged: cache sizes differ, and the whole point of the figure is that the
    crossover moves with L3. Aggregating across hardware would average that away.
    """

    def human(n: int) -> str:
        return f"{n // 1_000_000} M" if n >= 1_000_000 else f"{n // 1_000} K"

    multi = lookup["machine_id"].nunique() > 1
    blocks = []
    for machine_id, rows in lookup.groupby("machine_id"):
        pivot = rows.pivot_table(index="backend", columns="entries", values="ns_per_op", aggfunc="mean")
        pivot = pivot.reindex([b for b in ORDER if b in pivot.index])
        lines = []
        if multi:
            lines.append(f"\n**{machine_id}**\n")
        lines.append("| Backend | " + " | ".join(human(c) for c in pivot.columns) + " |")
        lines.append("|---|" + "|".join(["--:"] * len(pivot.columns)) + "|")
        for backend, row in pivot.iterrows():
            cells = ["—" if pd.isna(v) else f"{v:.1f}" for v in row]
            lines.append(f"| `{backend}` | " + " | ".join(cells) + " |")
        blocks.append("\n".join(lines))
    return "\n".join(blocks)


def k_table(k_sweep: pd.DataFrame) -> str:
    """Markdown table of FPR by k, one block per bit density (and per machine when several)."""
    lines = []
    multi = k_sweep["machine_id"].nunique() > 1
    for (machine_id, density), rows in k_sweep.groupby(["machine_id", "bits_per_entry_effective"]):
        rows = rows.sort_values("k")
        ks = " | ".join(str(int(k)) for k in rows["k"])
        fprs = " | ".join(f"{v * 100:.3f} %" for v in rows["fpr"])
        tag = f"{density:.2f} bits/entry" + (f" — {machine_id}" if multi else "")
        lines.append(f"\n**{tag}**\n")
        lines.append(f"| `k` | {ks} |")
        lines.append("|---|" + "|".join(["--:"] * len(rows)) + "|")
        lines.append(f"| FPR | {fprs} |")
    return "\n".join(lines)


def inject(markdown: str, name: str, body: str) -> str:
    """Replace the content between the BEGIN/END markers for ``name``."""
    begin, end = f"<!-- BEGIN GENERATED:{name} -->", f"<!-- END GENERATED:{name} -->"
    if begin not in markdown or end not in markdown:
        print(f"  ! markers for '{name}' not found in performance.md — skipped", file=sys.stderr)
        return markdown
    head = markdown[: markdown.index(begin) + len(begin)]
    tail = markdown[markdown.index(end) :]
    return f"{head}\n<!-- Generated by docs/measurements/plot.py — edit the CSV, not this block. -->\n\n{body}\n\n{tail}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--machine-id", help="restrict to a single machine from machines.json")
    args = parser.parse_args()

    PLOTS.mkdir(exist_ok=True)
    machines = json.loads(REGISTRY.read_text(encoding="utf-8"))["machines"]
    lookup = pd.read_csv(HERE / "filter_lookup.csv")
    k_sweep = pd.read_csv(HERE / "k_sweep.csv")
    sizing = pd.read_csv(HERE / "filter_sizing.csv")
    build = pd.read_csv(HERE / "filter_build.csv")

    if args.machine_id:
        machines = {k: v for k, v in machines.items() if k == args.machine_id}
        lookup = lookup[lookup["machine_id"] == args.machine_id]
        k_sweep = k_sweep[k_sweep["machine_id"] == args.machine_id]
        sizing = sizing[sizing["machine_id"] == args.machine_id]
        build = build[build["machine_id"] == args.machine_id]
        if lookup.empty:
            print(f"no rows for machine_id={args.machine_id!r}", file=sys.stderr)
            return 1

    for produced in (
        plot_lookup_latency(lookup, machines),
        plot_k_sweep(k_sweep),
        plot_sizing(sizing),
        plot_full_db(build),
    ):
        print(f"wrote {produced.relative_to(HERE.parent.parent)}")

    if PERFORMANCE_MD.exists():
        md = PERFORMANCE_MD.read_text(encoding="utf-8")
        md = inject(md, "filter_lookup_table", latency_table(lookup))
        md = inject(md, "k_sweep_table", k_table(k_sweep))
        PERFORMANCE_MD.write_text(md, encoding="utf-8", newline="")
        print(f"updated {PERFORMANCE_MD.relative_to(HERE.parent.parent)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
