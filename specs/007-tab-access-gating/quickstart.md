# Quickstart — Tab Access Gating (007)

## Run it locally

From `frontend/`:

```bash
npm run dev
```

Open the reported local URL. You should see two tabs at the top:

1. **1 Setup** (capital S — new)
2. **2 Your Alter Ego** (greyed out, cursor shows "not-allowed" when hovered)

## Manual validation

### Flow A — First-load gating (US1)

1. On load, confirm the alter-ego tab is visibly dimmed and the cursor becomes `not-allowed` on hover.
2. Click the alter-ego tab. Nothing should happen — Setup stays visible.
3. Press Tab to move keyboard focus into the tablist; focus lands on the Setup tab. Press ArrowRight. Focus stays on Setup (Arrow skips the disabled sibling because it's the only other option).
4. Inspect the alter-ego tab in DevTools. Expect `aria-disabled="true"`, `tabindex="-1"`, `data-disabled="true"`.

### Flow B — Mid-request Setup lock (US2)

1. Fill the Setup form (photo, all pickers, name) and click **Generate my alter ego**.
2. As soon as the spinner appears on the alter-ego panel, Setup should be visibly disabled.
3. Click the Setup tab. Nothing happens — the spinner keeps spinning.
4. Wait for the response. Both tabs become active again; you can freely click between them and the poster survives the round-trip (panel state preservation from 002).

### Flow C — Start-over (US4)

1. From the resolved state, click **Start over**.
2. You're back on Setup; the alter-ego tab is greyed out again, identical to fresh-load.
3. All Setup inputs are cleared.

## Automated validation

```bash
# unit + component (Vitest + RTL)
cd frontend
npm test

# lint
npm run lint

# production build
npm run build

# E2E (Playwright) — covers the new spec
npx playwright test tests/e2e/tab-access-gating.spec.ts
```

Key new tests to grep for if something fails:

- `selectors.test.ts` — block beginning `describe('tabDisabled', …)` — 10-row truth table.
- `TabsShell.test.tsx` — block `describe('TabsShell — disabled tab (007)', …)` — click / Enter / Space / ArrowRight / Home / End / aria-disabled / tabindex / active-tab-is-always-enabled invariant.
- `tests/e2e/tab-access-gating.spec.ts` — full flow: first-load → Generate → mid-flight → resolved → Start-over.

## Where the code lives

- Spec: `specs/007-tab-access-gating/spec.md`
- Plan: `specs/007-tab-access-gating/plan.md`
- Research: `specs/007-tab-access-gating/research.md`
- Data model: `specs/007-tab-access-gating/data-model.md`
- Selector: `frontend/src/features/alterego/state/selectors.ts` (`tabDisabled` — new)
- Shell: `frontend/src/features/alterego/components/TabsShell.tsx` (extended)
- Composition: `frontend/src/features/alterego/AlterEgoPage.tsx` (passes the flag; label "1 Setup")
- CSS: `frontend/src/index.css` (single `[aria-disabled="true"]` rule on `.tabs-shell__tab`)

## Non-goals (explicit)

- No reducer changes. No new action. No new state field.
- No backend change. No OpenAPI delta.
- No persistence. No new network request.
- No new dependency.
- No motion changes — the 005 entrance animation contract is untouched.
