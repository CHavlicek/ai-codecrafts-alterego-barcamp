# Quickstart: Tab Transfer Animation on Generate

**Feature**: 005-tab-transfer-animation

## Pre-requisites

- Node 20+, npm 10+ (same toolchain as 002/003).
- Checked out on branch `claude/animation-transferring-to-your-alter-ego-tab-6S2Mi`.

## Run the feature locally

```bash
cd frontend
npm install            # first run only; no new dependency was added for 005
npm run dev            # Vite dev server on http://localhost:5173
```

## Manually validate the happy path

1. Open `http://localhost:5173/`.
2. Upload / snap a photo, select a pose, role, universe, type a first name, optionally a vibe.
3. Click **Generate my alter ego**.
4. Observe the Alter Ego panel fade in (opacity 0 → 1) and glide up (translateY 12 px → 0) over roughly a third of a second before settling. The loading indicator is visible inside the animating panel.
5. Without refreshing, click back to **1 setup** — the switch is instant, every form value is intact. Click **2 Your Alter Ego** — the switch is instant again (no animation for manual clicks).
6. Click **Start over** from inside the Alter Ego tab — the tab returns to Setup instantly with no motion.

## Manually validate the reduced-motion path

- Chrome DevTools → Rendering pane → *Emulate CSS media feature* → `prefers-reduced-motion: reduce`.
- Repeat steps 2–3 above. The Alter Ego panel appears instantly with no fade / translate. Accessibility pane should show `aria-selected="true"` on the Alter Ego tab in the same moment as the click.

## Automated verification

From `frontend/`:

```bash
npm run lint
npm run build
npm run test          # Vitest — reducer, hooks, TabsShell
npm run test:e2e      # Playwright — tabs-shell.spec.ts
```

### What the tests prove

- **Reducer unit** — `ActiveTabChanged` with `reason: 'generate'` increments `generateAutoSwitchNonce`; manual dispatches leave it unchanged; `StartOverRequested` resets it to 0; re-dispatching `reason: 'generate'` while the alter-ego tab is already active still bumps the counter (spec Acceptance 4); unrelated actions (`GenerateSubmitted`, `GenerateSucceeded`) leave the counter unchanged.
- **`useGenerateAlterEgo` unit** — `onMutate` dispatches `{ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' }` BEFORE `GenerateSubmitted`.
- **TabsShell component** — when the reducer dispatches the generate-flavoured `ActiveTabChanged`, the alter-ego panel renders with `data-animating="true"` and the `.tabs-shell__panel--animate-in` class; `animationend` clears both; manual clicks keep `data-animating="false"`.
- **Playwright E2E** — `auto-switch on Generate` now asserts `data-animating="true"` on the alter-ego panel in the same commit as the Generate click (before verifying `aria-selected="true"`, preserving 002 FR-108). A new negative test confirms manual tab clicks do not animate.

## Troubleshooting

- **Animation does not fire** — confirm you clicked Generate and not a tab button; manual clicks are intentionally instant.
- **Animation fires twice / doesn't reset** — confirm the reducer has the transient-reset branches in all non-`ActiveTabChanged` cases (data-model.md §Reducer transitions).
- **`animationend` does not fire under reduced motion** — the 400 ms safety timeout in the TabsShell effect clears `isAnimatingIn`. If the attribute is stuck longer than 400 ms, inspect the cleanup function.
