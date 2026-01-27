# Git Workflow

This document defines the Git workflow used in this repository.
It is intentionally simple and optimized for a solo, PR-style process.

---

## Stable branch

- `main` is the **stable** branch.
- `main` must always be in a releasable state.
- **Direct commits to `main` are not allowed.**

All changes must be merged into `main` from a feature branch.

---

## Branching strategy

All work is done on short-lived branches created from `main`.

### Branch naming conventions

Use the following prefixes:

- `feat/<short-description>`
- `fix/<short-description>`
- `refactor/<short-description>`
- `docs/<short-description>`
- `chore/<short-description>`
- `ci/<short-description>`

Examples:
- `feat/event-emission`
- `fix/null-decision`
- `docs/git-workflow`

---

## Merge strategy

- **Squash merge** is the preferred strategy.
- After merging, the branch must be **deleted**.
- `main` should contain a clean, linear history.

---

## Solo PR-style workflow

Even as a solo developer, changes follow a PR-style flow:

1. Create a branch from `main`
2. Make focused commits
3. Run checks locally (and CI when available)
4. Merge into `main`
5. Delete the branch

---

## Summary

> All changes are developed on feature branches and merged into `main` via squash to keep the history clean and stable.
