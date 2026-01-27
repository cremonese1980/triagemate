# Contributing Guidelines

This document defines how to contribute to this repository.
The goal is to keep the codebase clean, stable, and easy to reason about.

The project currently follows a **solo PR-style workflow**, but these rules are written as if external contributors were present.

---

## Repository workflow

The Git workflow used in this repository is defined here:

- [`docs/git-workflow.md`](docs/git-workflow.md)

In short:
- `main` is always stable
- no direct commits to `main`
- all work happens on feature branches
- squash merge is preferred

---

## Commit message convention

All commits must follow the commit message rules defined here:

- [`docs/commit-messages.md`](docs/commit-messages.md)

Commits that do not follow this convention should not be merged.


---

## Solo PR-style workflow (mandatory)

Even as a solo developer, all changes must follow a PR-style workflow to keep `main` stable and the history reviewable.

### Rules
- Create a branch from `main` for every change
- One story = one PR
- Keep PRs small and focused (avoid mixed concerns)
- Use 1–3 commits per PR whenever possible
- Ensure CI is green before merging
- Use squash merge
- Delete the branch after merge

### PR hygiene
- PR title should describe the change clearly (match the story intent)
- Commits must follow the commit message convention (`docs/commit-messages.md`)
- If documentation or behavior changes, update relevant docs in the same PR

### Enforcement
At this stage, these workflow rules are enforced by process and discipline.
Automated enforcement (required checks, commit/PR conventions tooling) may be introduced later when the workflow is stable.

---



## Branching rules

- Create branches from `main`
- Use short-lived branches
- Follow the naming conventions described in the Git workflow
- One logical change per branch

---

## Definition of Done

A change is considered **done** when:

- Code compiles successfully
- All tests pass locally
- No unused code, dead code, or commented-out code remains
- Logging follows the project logging discipline
- No secrets or sensitive data are introduced
- Documentation is updated if behavior changes

---

## Testing

Before merging a change, contributors must:

- Run tests locally using:
mvn test
- Ensure the application starts without errors

CI will later enforce these checks automatically.

---

## Logging discipline

Logging rules are defined in a dedicated document:

- [`docs/logging.md`](docs/logging.md)

At a high level:
- Logs must be structured and machine-readable
- Do not log secrets, tokens, credentials, or personal data
- Prefer meaningful business-level logs over technical noise
- Avoid excessive logging at `INFO` level


---

## Secrets management

- Secrets must **never** be committed to the repository
- Do not hardcode credentials or tokens
- Configuration must rely on:
- environment variables
- external configuration files excluded from Git

Secret handling will be enforced as the project evolves.

---

## Contribution style

- Keep changes small and focused
- Prefer clarity over cleverness
- Write code as if it will be read by someone else tomorrow
- If unsure, favor simplicity

---

## Final note

These rules exist to reduce friction and cognitive load.
If a rule feels unnecessary, it should be discussed—not silently ignored.
