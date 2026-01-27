# Commit Message Convention

This document defines the commit message format used in this repository.
The goal is a clean, readable history that explains *why* changes were made.

---

## Commit message format

All commits must follow this format:

type(scope): short summary


### Rules
- `type` is **mandatory**
- `scope` is **mandatory**
- summary:
    - uses **imperative mood** (e.g. “add”, not “added”)
    - is **lowercase**
    - is **≤ 72 characters**
    - does **not** end with a period

---

## Allowed commit types

- **feat** — introduce a new feature or capability
- **fix** — fix a bug or incorrect behavior
- **refactor** — change code structure without changing behavior
- **test** — add or modify tests
- **docs** — documentation only changes
- **chore** — maintenance tasks (no production code)
- **build** — build system or dependency changes
- **ci** — CI/CD configuration changes

---

## Allowed scopes

Scopes indicate *where* the change applies.

Common scopes:
- `ingest`
- `triage`
- `logging`
- `docs`
- `ci`
- `repo`

Scopes are **extensible** when new modules or concerns are introduced.

---

## Examples

### Good examples


- feat(ingest): emit message received event
- fix(triage): handle empty decision result
- docs(logging): document logging discipline
- ci(repo): add github actions build gate


### Bad examples
- fix bug
- WIP
- update stuff


Why these are bad:
- no type or scope
- unclear intent
- not searchable or readable in history

---

## Guiding principle

A good commit message should allow someone to understand:
- **what changed**
- **where**
- **why**

without opening the code.
