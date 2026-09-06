# Implementation Plan: Default outbound email to Namecheap PrivateEmail

**Branch**: `027-privateemail-smtp` | **Date**: 2026-05-19 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/027-privateemail-smtp/spec.md`

## Summary

This is an **operational / configuration** feature, not a new product capability. The existing 023 SMTP seam (`AlterEgoEmailService` + `EmailConfigured` + `application.yml` `spring.mail.*` block) is preserved verbatim; the changes are:

1. **`application.yml` defaults change** — the previously-blank `spring.mail.*` block ships pointed at Namecheap PrivateEmail's documented SMTP submission service (`mail.privateemail.com:587`, STARTTLS, SMTP-AUTH). Operators no longer have to research PrivateEmail's connection parameters; only the per-deployment secrets are supplied at runtime through Terraform-managed env vars.
2. **`EmailConfigured` gate tightens** from "host non-blank" to "host AND username AND password AND From all non-blank" (FR-2706). This restores the helpful "not yet configured" alert in deployments that have the new default host but no credentials — without it the 023 host-only rule would misfire as "configured" out of the box and click-time errors would surface as a confusing retryable-failure alert.
3. **Operator runbook** ships in this feature folder (FR-2712) — env-var names, the Terraform variables that feed them, PrivateEmail's From-alignment constraint, smoke-test recipe.

The public HTTP contract (`POST /api/v1/alter-egos/email`) and the entire user-facing surface (Setup tab Email field, Alter Ego tab Send-as-Email button, three alert variants) are **byte-identical** before and after.

## Technical Context

**Language/Version**: Java 21 (LTS) on the backend; **frontend is untouched.**
**Primary Dependencies**: Spring Boot 3.x (existing); `spring-boot-starter-mail` (existing — 023); Jakarta Mail (transitive, existing); Spring Retry (existing — wraps `mailSender.send()`). **No new runtime, test, or build dependency.**
**Storage**: N/A — no-persistence posture inherited from 001 / 023 / 026 is preserved verbatim (FR-2710).
**Testing**: JUnit 5 + Spring Boot Test + Mockito + ArchUnit 1.3 (existing). Affected tiers: `unit/` (`EmailConfiguredTest`, `AlterEgoEmailServiceTest`), `integration/` (`AlterEgoEmailControllerIT`, `AlterEgoEmailControllerNotConfiguredIT`).
**Target Platform**: Linux container — `eclipse-temurin:21-jre-alpine` (existing).
**Project Type**: Web application (`backend/` + `frontend/`). This feature touches `backend/` only — specifically `infrastructure/config/` plus `src/main/resources/application.yml`.
**Performance Goals**: Inherits 023 SC-2304 — end-to-end click-to-dispatched within 5 s under normal conditions (this plan does not change the latency envelope).
**Constraints**: PrivateEmail enforces (a) From-alignment with the authenticated mailbox, (b) submission rate limits, and (c) STARTTLS/SSL on the encrypted submission port. (a) is operator-discipline (called out in runbook); (b) is intentionally ignored per spec clarification (no load expected; uniform 023 retry budget); (c) is configured via the new defaults.
**Scale/Scope**: Single deployment, ~1 sender mailbox, ≤ ~100 sends across a CodeCrafts event window (effectively zero load). One configuration file + one Spring component + one runbook.

## Constitution Check

*Constitution version 1.1.0. Gate evaluated against each principle in scope.*

| Principle | In scope? | Verdict | Notes |
|---|---|---|---|
| I. Modern & Secure Stack (NON-NEGOTIABLE) | Yes | **PASS** | No new dependencies. All affected libraries (`spring-boot-starter-mail`, Jakarta Mail, Spring Retry) are current and CVE-clean as of constitution audit date. |
| III. Test-First (TDD, NON-NEGOTIABLE) | Yes | **PASS** | Test-first cycle: extend `EmailConfiguredTest` for the four-input gate (host / username / password / from), extend `AlterEgoEmailControllerNotConfiguredIT` for the credentials-missing case, then change `EmailConfigured` + `application.yml`. Coverage gate (≥ 90%) holds because the change is small and fully covered. |
| IV. Resilient HTTP | Yes | **PASS** | Existing RetryTemplate around `mailSender.send()` is untouched per FR-2713. No rate-limit-aware fast-fail logic introduced. |
| V. Feature Branch Workflow | Yes | **PASS** | Branch `027-privateemail-smtp` was cut from `main` via `create-new-feature.sh`. |
| VI. Zero Deprecated Dependencies | Yes | **PASS** | No new packages, no deprecated removals. `npm audit` / `dependencyCheckAnalyze` outcomes are unchanged. |
| VII. Layer Convention | Yes | **PASS** | All edits land in `infrastructure/config/` (and `application.yml` under `src/main/resources/`). No `boundary` / `application` / `domain` changes. ArchUnit rules continue to pass unchanged. |
| VIII. Provider Seam | No | N/A | Provider-seam rules govern image / character generators. The email seam is a single `EmailSenderPort` already; no provider-identity branching introduced. |
| IX. Test Pyramid | Yes | **PASS** | Changes land in existing tiers (`unit/`, `integration/`). No new tier introduced. Unit-tier still <10s wall-clock. |

**Result**: All gates pass. No complexity-tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/027-privateemail-smtp/
├── plan.md              # This file
├── spec.md              # Feature specification (with Clarifications section)
├── research.md          # Phase 0 output — PrivateEmail SMTP recipe, Spring property mapping, Terraform var convention
├── data-model.md        # Phase 1 output — Configured state + Mail-server target entities
├── quickstart.md        # Phase 1 output — operator runbook (FR-2712)
├── contracts/
│   └── README.md        # Phase 1 — no public HTTP contract change; record that explicitly
└── checklists/
    └── requirements.md  # From /speckit.specify validation pass
```

### Source Code (repository root) — files touched by this feature

```text
backend/
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.yml                       # ✏ Modified — spring.mail.* defaults pointed at PrivateEmail; STARTTLS + auth properties added
    │   └── java/com/aiavatar/alterego/
    │       └── infrastructure/
    │           └── config/
    │               ├── EmailConfigured.java          # ✏ Modified — gate widens to host + username + password + from
    │               └── EmailProperties.java          # ➖ Unchanged — `from` + `subject` already typed-bound; the new password/username inputs read via @Value in EmailConfigured (same pattern as host)
    └── test/
        └── java/com/aiavatar/alterego/
            ├── service/email/
            │   └── EmailConfiguredTest.java          # ✏ Modified — four new cases (username-blank, password-blank, from-blank, all-present)
            └── integration/
                ├── AlterEgoEmailControllerIT.java                  # ✏ Modified — set all four properties in @TestPropertySource to keep "configured" true
                └── AlterEgoEmailControllerNotConfiguredIT.java     # ✏ Modified — flip to "host present but creds blank" to assert tightened gate

frontend/                                                            # ➖ Unchanged — FR-2709 byte-identical user surface
```

**Structure Decision**: Web-app structure inherited from 002+. This feature touches only `backend/` (one config file + one component + the matching test files). No new packages, no new modules.

## Complexity Tracking

> No Constitution Check violations. Table omitted.
