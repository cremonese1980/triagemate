# ADR-003: Why deterministic decision engine before AI

## Status
Accepted

## Decision
Run deterministic rule-based decisioning before any AI component.

## Rationale
- Guarantees explainability for baseline behavior.
- Simplifies testing and reproducibility.
- Provides safe fallback when AI is unavailable.

## Consequences
- Rule engine must be maintained as first-class domain asset.
- AI becomes augmentation, not control-plane authority.
