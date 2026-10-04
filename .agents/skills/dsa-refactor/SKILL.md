---
name: dsa-refactor
description: Plan and execute refactoring of a whole codebase (or large module) written in another architecture into Trade Republic's Domain Service Architecture (DSA). Typical sources are classic Spring controller/service/repository, fat controllers, god services with @Transactional, JPA entities as models, hexagonal or clean architecture, CQRS handlers, or no structure at all. Use whenever the user asks to migrate, convert, restructure or re-architect a service "to DSA", "the DSA way" or "like domain-service-template", to fix many ArchUnit layer violations, or to "clean up the architecture" of a Kotlin/Java Spring service. For single features in an existing DSA repo, use the dsa skill.
---

# Refactor a codebase to DSA

First read `../dsa/SKILL.md` and `../dsa/references/core-rules.md`. They define the target.
The work is **behaviour-preserving, incremental and guarded**: structure changes only, no functional changes,
green build after every slice.

## Ground rules (and why)
- **Freeze contracts.** HTTP paths/JSON, topics/queues/schemas, DB schema, idempotency spaces, job names,
  metric names and bean names stay identical (`references/pattern-mapping.md` §5). Consumers and in-flight data depend on them.
- **Separate behaviour changes.** Outbox adoption, adding idempotency, fixing bugs you find: each is its
  own user-approved step. Log them under *Findings*; don't fix silently.
- **Never weaken tests to go green.** Use `git mv` to keep history. One slice per commit or PR.
- **The plan file is the resumable source of truth.** Update it after every slice so any agent can continue.

## Phase 0: Baseline
Clean tree, new branch (ask for the ticket ID), full build and tests green. Record the commands, durations and coverage.

## Phase 1: Discover (read-only)
1. `python3 scripts/inventory.py <repo> --out docs/dsa-migration/inventory.md`. It lists every class with a
   suggested layer, all entry points, and flags (`fat-entry-point`, `infra-in-application`,
   `domain-service-does-io`, `impure-domain`, `@Transactional→TransactionProvider`, ...). The output is heuristic,
   so confirm each row by reading the class.
2. Identify the source pattern (`references/pattern-mapping.md` §1).
3. Each entry point is a use case. Trace each one to its I/O. Identify aggregates and bounded contexts.
4. For large repos, fan out discovery to subagents, one per package or entry-point group.

## Phase 2: Design (get user sign-off before moving code)
- Target: domain-service-template conventions (`../dsa/references/template-conventions.md`). If the repo is already partly DSA, keep its existing dialect where it doesn't break the core rules.
- Layout: flat or per-model. Build the old→new class map, then the slice order:
  1. foundations (`TransactionProvider`, ports);
  2. one simple read use case as a pilot;
  3. writes;
  4. listeners and jobs;
  5. the most tangled code last.
- Write `docs/dsa-migration/PLAN.md` with these sections: metadata, contract freeze, inventory summary,
  class map, slices (status), decisions, findings, open questions.

## Phase 3: Guardrails
Add ArchUnit rules with a frozen baseline (`references/archunit-guardrails.md`), and commit them.
The violation store or count may only shrink from here on.

## Phase 4: Migrate slice by slice
For each slice:
1. Add characterization tests at the entry point if coverage is thin.
2. Domain model and ports: strip annotations; mapping happens in infrastructure.
3. Infrastructure adapters implement the ports.
4. App service: one use case, transaction plus idempotency, orchestration only; business rules go to the domain.
5. Thin the entry point to map → one call → map.
6. Delete dead code.
7. Build, run the tests and ArchUnit, and shrink the baseline.
8. Update PLAN.md and commit.

Recipes R1–R8 for each transformation are in `references/pattern-mapping.md`.
When many entities repeat the same plumbing, introduce the generic bases from
`../dsa/references/advanced-patterns.md` (generic repository/DAO, `Validated<T>`, state-machine framework,
idempotent service template, lock modes, priority/delayed outbox) early, so later slices only extend them.
Parallel agents are an option: give each a disjoint package in its own worktree, while one coordinator owns
PLAN.md and the shared files (DI config, ArchUnit store, shared domain types).

## Phase 5: Finish
Baseline at zero, escape hatches removed, rules tightened, docs updated, full test run.
Then give the user a summary of what changed, the findings, and the follow-ups.

## Stop and ask the user when
- the baseline is red;
- a contract would have to change;
- domain boundaries are ambiguous;
- a behaviour change is needed (outbox, idempotency, transaction semantics such as `REQUIRES_NEW`).
