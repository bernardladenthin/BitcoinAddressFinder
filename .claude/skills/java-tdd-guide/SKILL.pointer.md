---
name: java-tdd-guide-pointer
description: Marker file — the canonical Java TDD skill lives in the sibling workspace repo at .claude/skills/java-tdd-guide/SKILL.md and must be in the Claude session scope for the harness to load it.
---

# Pointer — Java TDD Guide

The canonical skill content lives in the `workspace` sibling repo:

`../../../../workspace/.claude/skills/java-tdd-guide/SKILL.md`

For Claude sessions opened against this repo to find the skill, the
`workspace` repo must also be in the session's repository scope. The
standard remote-execution setup adds it automatically.

If a session reports the skill as missing, verify the session scope
includes `bernardladenthin/workspace` and retry.

This file exists so human readers and any future drift-detection tooling
can see the dependency from this repo to the canonical skill.
