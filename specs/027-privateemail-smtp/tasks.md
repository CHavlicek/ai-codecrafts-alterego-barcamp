---

description: "Tasks for 027-privateemail-smtp — default outbound email to Namecheap PrivateEmail"
---

# Tasks: Default outbound email to Namecheap PrivateEmail

**Input**: Design documents from `/specs/027-privateemail-smtp/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/README.md ✓, quickstart.md ✓

**Tests**: MANDATORY per Constitution Principle III. Every implementation task in this feature is preceded by a failing test task in the same user story. Unit + service tier coverage MUST stay ≥ 90%; the existing 023 integration tier covers the end-to-end journey.

**Scope**: Backend-only configuration change. **Zero frontend changes** (FR-2709). No new runtime, test, or build dependency. All edits land in two source files (`application.yml`, `EmailConfigured.java`) plus three matching test files.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2 / US3 (maps to spec.md user stories)
- Setup / Foundational / Polish tasks have no `[Story]` label

## Path Conventions

Java backend (Gradle Kotlin DSL, Spring Boot 3.x, Java 21):

- Production: `backend/src/main/java/com/aiavatar/alterego/...`
- Resources: `backend/src/main/resources/application.yml`
- Service-tier tests: `backend/src/test/java/com/aiavatar/alterego/service/...`
- Integration tests (`@SpringBootTest`): `backend/src/test/java/com/aiavatar/alterego/integration/...`

Frontend untouched — no `frontend/` paths in this task list.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the 023/024 email surface that this feature refines is in place.

- [X] T001 Verify branch `027-privateemail-smtp` is checked out, `git status` is clean except for `specs/027-privateemail-smtp/`, and the 023 wiring is intact by reading `backend/src/main/resources/application.yml` (the `spring.mail.*` and `aiavatar.email.*` blocks) and `backend/src/main/java/com/aiavatar/alterego/infrastructure/config/EmailConfigured.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None. The 023 SMTP seam (`AlterEgoEmailService`, `EmailConfigured`, `EmailProperties`, `AlterEgoEmailController`, `ProblemDetailAdvice`) and the 024 retry / problem-detail wiring are already in place and require no foundational work. This phase is intentionally empty.

**Checkpoint**: Foundation already exists — user story implementation can begin immediately after T001.

---

## Phase 3: User Story 1 — Out-of-the-box, app ships pre-pointed at PrivateEmail (Priority: P1) 🎯 MVP

**Goal**: An operator deploys the backend with only `SPRING_MAIL_USERNAME` + `SPRING_MAIL_PASSWORD` + `AIAVATAR_EMAIL_FROM` set and immediately delivers via `mail.privateemail.com:587` (STARTTLS + SMTP-AUTH) — no operator research, no other config knobs touched.

**Independent Test**: Boot the backend with the three credential-shaped env vars set against a real PrivateEmail mailbox; POST a multipart request to `/api/v1/alter-egos/email`; observe `200 sent` and the message arriving in the recipient inbox (quickstart.md §5b). With no env vars set, observe the existing 023 not-configured alert (which becomes the focus of US2).

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T002 [P] [US1] Add new service-tier test class `MailServerDefaultsTest` at `backend/src/test/java/com/aiavatar/alterego/service/email/MailServerDefaultsTest.java` that boots an `ApplicationContextRunner` with `MailSenderAutoConfiguration` only, loads the production `application.yml`, and asserts: (a) `JavaMailSenderImpl#getHost()` equals `mail.privateemail.com`; (b) `getPort()` equals `587`; (c) `getJavaMailProperties().getProperty("mail.smtp.auth")` equals `"true"`; (d) `…starttls.enable` equals `"true"`; (e) `…starttls.required` equals `"true"`. Test MUST fail initially because `application.yml` still ships the 023 defaults.
- [X] T003 [P] [US1] Extend `AlterEgoEmailControllerIT` at `backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoEmailControllerIT.java` `@TestPropertySource` from a single `spring.mail.host=smtp.test` line to FOUR properties — `spring.mail.host=smtp.test`, `spring.mail.username=tester`, `spring.mail.password=pw`, `aiavatar.email.from=tester@example.com` — so the test continues to pass once US2 tightens the gate. (This is a US1 task because it is the test-suite half of changing the defaults; the property-bundle for "configured" is what US1 establishes operationally.)

### Implementation for User Story 1

- [X] T004 [US1] Modify `backend/src/main/resources/application.yml` `spring.mail.*` block (lines ~40-45 in current file): change `host` default from blank to `mail.privateemail.com`; change `port` default from `25` to `587`; add three new keys under `spring.mail.properties.mail.smtp.*` — `auth: ${SPRING_MAIL_SMTP_AUTH:true}`, `starttls.enable: ${SPRING_MAIL_SMTP_STARTTLS_ENABLE:true}`, `starttls.required: ${SPRING_MAIL_SMTP_STARTTLS_REQUIRED:true}`. Keep `username`, `password`, `protocol` unchanged (still env-var-overridable, still blank default for username/password). Update the YAML comment block above the `spring.mail` block to reference 027 and the new PrivateEmail defaults (one paragraph max — match the 023 comment style). After this edit, T002 and T003 MUST go green.

**Checkpoint**: Application boots pointed at PrivateEmail by default; the new service-tier defaults test (T002) passes; the configured-happy-path IT (T003) passes. The unconfigured IT and `EmailConfiguredTest` still pass under the 023 host-only gate — US2 changes that next.

---

## Phase 4: User Story 2 — Operator forgot to supply credentials → clear "not configured" signal (Priority: P1)

**Goal**: With the new default host being non-blank (US1), tighten `EmailConfigured` from "host-only" to "host + username + password + From all non-blank" (FR-2706) so a fresh deploy without secrets still surfaces the helpful 023 "not yet configured" alert instead of confusing 502s.

**Independent Test**: Boot the backend with only `spring.mail.host` defaulting to `mail.privateemail.com` and nothing else; POST to `/api/v1/alter-egos/email`; observe `503 not-configured` with `type=…/email/not-configured`. Supply username + password + From; restart; same request now reaches the SMTP layer.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T005 [P] [US2] Extend `EmailConfiguredTest` at `backend/src/test/java/com/aiavatar/alterego/service/email/EmailConfiguredTest.java` with four new test methods covering the AND-gate (FR-2706): (a) `hostPresentButUsernameBlankProducesUnconfigured` — `spring.mail.host=mail.privateemail.com`, `spring.mail.username=`, expect `isConfigured()=false`; (b) `hostAndUsernamePresentButPasswordBlankProducesUnconfigured` — host + username set, `spring.mail.password=`, expect false; (c) `hostUsernamePasswordPresentButFromBlankProducesUnconfigured` — host + username + password set, `aiavatar.email.from=`, expect false; (d) `allFourPresentProducesConfigured` — all four set, expect `isConfigured()=true`. Keep the existing four 023 tests passing (they only set `host`; under the new gate they MUST now flip to assert `isConfigured()=false` instead of `true` in the `hostPresentProducesConfiguredState` case — rename that case `hostOnlyPresentProducesUnconfigured` and invert the assertion). Tests MUST fail before T007 lands.
- [X] T006 [P] [US2] Flip `AlterEgoEmailControllerNotConfiguredIT` at `backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoEmailControllerNotConfiguredIT.java` `@TestPropertySource` from `spring.mail.host=` (currently empty) to a "host present but credentials missing" shape: `spring.mail.host=mail.privateemail.com`, `spring.mail.username=`, `spring.mail.password=`, `aiavatar.email.from=`. The 503 assertion stays unchanged (still `/email/not-configured`, still 503, still verifies no SMTP attempt). Rationale: this is the new dominant failure mode in the 027 world — host comes pre-set, credentials are what's missing. Update the class-level Javadoc accordingly (mention FR-2706). Test MUST fail before T007 lands (current `EmailConfigured` returns `true` for any non-blank host).

### Implementation for User Story 2

- [X] T007 [US2] Modify `backend/src/main/java/com/aiavatar/alterego/infrastructure/config/EmailConfigured.java`: add three new constructor-injected `@Value` fields — `@Value("${spring.mail.username:}") String username`, `@Value("${spring.mail.password:}") String password`, `@Value("${aiavatar.email.from:}") String from`. Introduce `private static boolean isNonBlank(String s) { return s != null && !s.isBlank(); }` helper. Change `@PostConstruct init()` to compute `this.configured = isNonBlank(host) && isNonBlank(username) && isNonBlank(password) && isNonBlank(from);`. Keep the single INFO log line shape unchanged (`event=email.config.evaluated configured={}`) — no per-input mask (research R5). Update the class-level Javadoc to reference FR-2706 and the new 4-input AND. After this edit, T005 and T006 MUST go green.

**Checkpoint**: A deploy without credentials reliably surfaces the "not yet configured" alert; with credentials set, the existing 023 happy + retry paths still work end-to-end. US1 + US2 together are the deployable MVP (both P1).

---

## Phase 5: User Story 3 — Operator runbook (Priority: P2)

**Goal**: `quickstart.md` (already generated by `/speckit.plan`) covers FR-2712 (a)–(f) and lets an operator unfamiliar with the codebase complete a smoke-test send within 15 minutes (SC-2703).

**Independent Test**: A fresh reader follows quickstart.md §1–§5 cold and reaches a `200 sent` response within 15 minutes (assumes mailbox + DNS already provisioned).

### Tests for User Story 3

There is no automated test for documentation. The "test" is the manual walk-through (T008) plus verifying the smoke-test command in quickstart.md §5a runs green (T009).

### Implementation for User Story 3

- [X] T008 [US3] Manual review pass of `specs/027-privateemail-smtp/quickstart.md` against FR-2712 (a)–(f): confirm each of the six sub-requirements has at least one section addressing it — (a) env vars listed → §2 table; (b) shipped defaults named → §3 table; (c) From-alignment rule called out → §4; (d) smoke-test recipe → §5; (e) restart-on-config-change → §7 first bullet; (f) outbound port reachability → §7 second bullet. No code edits — this is a read-and-checkmark verification. If any sub-requirement is missing, add the missing paragraph.
- [X] T009 [US3] Run the bundled smoke-test command from quickstart.md §5a — `./gradlew :backend:integrationTest --tests AlterEgoEmailControllerIT --tests AlterEgoEmailControllerNotConfiguredIT` — and confirm both classes are green. This validates that the runbook's documented "local smoke test" actually works as written after US1 + US2 land.

**Checkpoint**: Operator-facing documentation is complete and validated. The feature is fully shippable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Confirm no regressions in the existing 023 / 024 / 025 / 026 surfaces; confirm constitutional gates (coverage, ArchUnit, no new deps) still hold.

- [X] T010 [P] Run the full backend test suite (`./gradlew test` from repo root or `cd backend && ./gradlew test`) and confirm zero failures across all five tiers (`unitTest`, `serviceTest`, `contractTest`, `integrationTest`, `archTest` per Constitution Principle IX).
- [X] T011 [P] Run JaCoCo aggregate (`./gradlew jacocoTestReport` from `backend/`) and confirm line coverage remains ≥ 90% (Constitution Principle III). `EmailConfigured` should land at 100% line coverage because all four AND-branches are exercised by `EmailConfiguredTest`.
- [X] T012 [P] Run the architecture tier in isolation (`./gradlew :backend:archTest`) to confirm no new layer violations — the 027 edits live in `infrastructure/config/`, which is allowed to read `application.*` ports and `domain.*` types, so the dependency-direction rule continues to pass.
- [X] T013 Verify no new runtime dependency landed by diffing `backend/build.gradle.kts` against `main` — `git diff main -- backend/build.gradle.kts` MUST be empty (FR plan: "No new runtime, test, or build dependency").
- [X] T014 Manually verify the public HTTP contract is byte-identical: `git diff main -- backend/src/main/java/com/aiavatar/alterego/boundary/` MUST be empty (FR-2709). If any file under `boundary/` shows changes, that is an unintended regression and must be reverted.
- [X] T015 Manually verify the frontend is untouched: `git diff main -- frontend/` MUST be empty (FR-2709). Any frontend diff is an unintended regression and must be reverted.
- [X] T016 Append a one-paragraph entry under `## Recent Changes` in `/Users/dmytrokorniienko/aiavatar/CLAUDE.md` summarising 027 (PrivateEmail defaults + 4-input gate + Terraform-fed env vars) following the format of the 026 entry already in that file. Keep it under ~12 lines.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1, T001)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Empty — no tasks.
- **US1 (Phase 3, T002–T004)**: Depends on T001 only.
- **US2 (Phase 4, T005–T007)**: Depends on US1 completion. Rationale: US2's `EmailConfiguredTest` rewrite assumes US1's `application.yml` defaults are in place (the test cases reference `mail.privateemail.com` as the host value); also `AlterEgoEmailControllerIT` must already have its four `@TestPropertySource` entries (T003) before US2's gate tightening (T007) lands, otherwise T003's `AlterEgoEmailControllerIT` would flip from green to red on the way through.
- **US3 (Phase 5, T008–T009)**: Depends on US2 completion (T009 is the smoke test that exercises the post-US2 wiring).
- **Polish (Phase 6, T010–T016)**: Depends on all user stories.

### User Story Dependencies (within Phase 3 + Phase 4)

- US1 and US2 are both P1 and ship together as the deployable MVP. US1 is sequenced first because US2's gate tightening builds on US1's defaults — but the two stories can be implemented by the same developer in a single sitting since the scope is small (one YAML file + one Java class + three test files total).

### Within Each User Story

- Tests MUST be written FIRST and MUST FAIL before implementation lands (Constitution Principle III — non-negotiable). The order within each US-phase is: test tasks → impl task.
- T002 + T003 are [P] (different files); after both fail, T004 makes them go green.
- T005 + T006 are [P] (different files); after both fail, T007 makes them go green.

### Parallel Opportunities

- **Within US1**: T002 and T003 in parallel (different test files).
- **Within US2**: T005 and T006 in parallel (different test files).
- **Within Polish**: T010, T011, T012 are [P] — independent Gradle invocations against the same compiled classpath; can run as one `./gradlew test jacocoTestReport archTest` invocation if preferred.

---

## Parallel Example: User Story 1

```bash
# Step 1 — Launch both US1 test tasks in parallel (different files):
Task: "Add MailServerDefaultsTest in backend/src/test/java/com/aiavatar/alterego/service/email/MailServerDefaultsTest.java"
Task: "Extend AlterEgoEmailControllerIT @TestPropertySource in backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoEmailControllerIT.java"

# Step 2 — Confirm both fail:
./gradlew :backend:serviceTest --tests MailServerDefaultsTest          # expect RED
./gradlew :backend:integrationTest --tests AlterEgoEmailControllerIT  # expect RED for the new property assertions

# Step 3 — Implementation (T004):
Task: "Modify backend/src/main/resources/application.yml spring.mail.* block per research R1"

# Step 4 — Confirm both go green:
./gradlew :backend:serviceTest --tests MailServerDefaultsTest          # expect GREEN
./gradlew :backend:integrationTest --tests AlterEgoEmailControllerIT  # expect GREEN
```

## Parallel Example: User Story 2

```bash
# Step 1 — Launch both US2 test tasks in parallel:
Task: "Extend EmailConfiguredTest with four new AND-gate cases in backend/src/test/java/com/aiavatar/alterego/service/email/EmailConfiguredTest.java"
Task: "Flip AlterEgoEmailControllerNotConfiguredIT to host-present-but-creds-blank in backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoEmailControllerNotConfiguredIT.java"

# Step 2 — Confirm both fail (the new AND assertions reject what the 023 host-only gate currently accepts as configured):
./gradlew :backend:serviceTest --tests EmailConfiguredTest                          # expect RED
./gradlew :backend:integrationTest --tests AlterEgoEmailControllerNotConfiguredIT  # expect RED

# Step 3 — Implementation (T007):
Task: "Modify EmailConfigured.java to read host + username + password + from and AND them"

# Step 4 — Confirm both go green:
./gradlew :backend:serviceTest --tests EmailConfiguredTest                          # expect GREEN
./gradlew :backend:integrationTest --tests AlterEgoEmailControllerNotConfiguredIT  # expect GREEN
```

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. T001 — confirm 023 surface intact.
2. T002 + T003 in parallel — both RED.
3. T004 — `application.yml` defaults flip; T002 + T003 go GREEN.
4. T005 + T006 in parallel — both RED.
5. T007 — `EmailConfigured` gate tightens; T005 + T006 go GREEN.
6. **STOP and VALIDATE**: Run `./gradlew test` — full suite green. Smoke-test against a real PrivateEmail mailbox per quickstart.md §5b (manual).
7. Deployable as MVP — US3 is documentation polish.

### Incremental Delivery

- Phase 3 (US1) lands → app points at PrivateEmail by default, but credentials-missing still surfaces 502 (the regression US2 fixes). Do NOT release at this point; ship US1 + US2 together.
- Phase 4 (US2) lands → credentials-missing now surfaces 503/not-configured. **This is the deployable cut.**
- Phase 5 (US3) lands → runbook validated. No code changes here; documentation polish only.

### Single-Developer Strategy

This feature is small enough (one YAML + one Java class + three test files) that one developer completes Phases 1–4 in a single sitting. The TDD discipline is preserved by sequencing test tasks before their corresponding impl task within each story.

---

## Notes

- All `[P]` tasks operate on different files with no shared state — safe to parallelize.
- No frontend tasks. No Playwright test changes. No new dependency. No persistence change.
- The two existing RFC 7807 type URIs (`/email/not-configured`, `/email/send-failed`) and the `200 sent` response shape are byte-identical before and after — verified by T014.
- After T007, the existing four 023 test cases in `EmailConfiguredTest` change semantics: the case that previously asserted `host=smtp.test → configured=true` MUST now assert `false` because only host is present. Rename and re-orient that case in T005, do not delete it.
- The audit-log line in `EmailConfigured` (`event=email.config.evaluated configured=true|false`) MUST emit a single boolean — no per-input `username=present/absent` mask (research R5, PII safety).
- Do NOT add `mail.debug=true` to `application.yml` in any profile (quickstart.md §7 third bullet) — Jakarta Mail's debug flag dumps SMTP AUTH frames into logs.
