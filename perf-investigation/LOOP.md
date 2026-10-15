<!--
SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>

SPDX-License-Identifier: Apache-2.0
-->

# The investigation loop — what one iteration does

Driven by Claude Code's `/loop` (self-paced). The prompt is just: *"continue the GPU throughput
investigation per `perf-investigation/LOOP.md`"*. Everything an iteration needs is in this file and
in [`TODO.md`](TODO.md), the work master.

## Hard rules

1. **Branch `claude/perf-investigation` only. Never touch `main`**, never open a PR, never post
   anywhere; the branch was kept local while the loop ran and published once it was closed.
2. **Budget ≤ 14 USD of Vast.ai credit** (≈ 12 €) for the whole investigation; `vastbench.sh` enforces
   it per run. Other providers only if a question cannot be answered on Vast, and inside the same total.
3. **Nothing may keep running.** Every run destroys its own instance (EXIT trap); every iteration
   that rented anything ends with `bash perf-investigation/vastbench.sh sweep` and reads its output:
   0 `baf-perf-*` instances, 0 volumes, throwaway key deleted.
4. **Free before paid**: if the local RTX 3070 Laptop or an offline tool (`ptxas`) can answer the
   question, use it.
5. **Every code change gets a useful unit test, kept on the branch.** Run the narrow test surface per
   `CLAUDE.md` ("Test execution policy"), `mvn spotless:apply` before committing `.java`. Use a
   Temurin JDK 21 (`JAVA_HOME_TEMURIN`) — an Oracle JDK 21.0.9 cannot run NullAway's JSpecify mode.
6. **Whatever a run teaches about Vast.ai itself** (hosts that never start, CLI quirks, pricing,
   SSH, throttled cards, timing) goes into the maintainer's private
   Vast.ai notes (section *Lessons from the BAF perf runs*), not into this repository.
7. Diagnostic levers stay opt-in (system property or config flag defaulting to today's behaviour);
   the production default changes only when a measurement on more than one architecture justifies it,
   and that is then written down as a recommendation in `TODO.md`, not silently flipped.

## One iteration

1. Read `TODO.md`. Run `bash perf-investigation/vastbench.sh status`. **Wake up at least every 15
   minutes** (fallback heartbeat 900 s) and check that nothing is stuck: an instance `running` while
   its run log has not moved, an upload that does not finish, a run past its deadline — then intervene
   (stop the run, `sweep`, record what happened).
2. **A run still in flight** (a background `vastbench.sh run` not yet notified as finished): do
   nothing else; wait for its notification.
3. **New results** under `results/` not yet in the findings log: read them (`run.log`, `smi.csv`
   incl. throttle reasons, `jmh-*.log`, `baf-*.log`, `offer.json`), write the next `Fn` entry —
   numbers, the conclusion, what it rules in or out — update the hypotheses table, commit.
4. **Pick the next experiment**: the cheapest one that splits the remaining hypotheses best. Write or
   adapt `remote/expNN-*.sh`; if it needs code, change the code + tests first, then
   `vastbench.sh bundle` (rebuild whenever `src/` changed — `bundle.rev` records the commit).
5. Launch it in the background: `bash perf-investigation/vastbench.sh run <label> "<offer query>"
   perf-investigation/remote/expNN-*.sh <max_min>`. Prefer `verified=true reliability>0.98`, enough
   host RAM (`cpu_ram>=32` for 141 M-entry filters) and record the GPU model in the label.
6. Commit `TODO.md` and anything new (results included; they are small text).

## When to stop

* Budget left < ~1 USD, or
* the bottleneck is explained with evidence and the remaining open items need a design decision
  by the maintainer, or
* three experiments in a row produced no new information.

Then: final sweep, a **Summary** section at the top of `TODO.md` (what was found, what to change,
expected gain, what is still open), local commit, and stop the loop.
