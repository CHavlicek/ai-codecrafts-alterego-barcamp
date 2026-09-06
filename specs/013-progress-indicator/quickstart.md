# Quickstart — Image Generation Progress Indicator

End-to-end manual smoke test in the dev server. Use this after the implementation tasks
are green to verify the spec's User Stories and Edge Cases hold in a real browser.

## Prerequisites

- Project deps installed: `npm install` from the repo root (or from `frontend/` if the
  feature has already split into the `frontend/` workspace per CLAUDE.md).
- Backend running OR not running — both are valid here, since the FE-side fallback path
  (per 003) will produce a poster regardless.

## Steps

1. `npm run dev` from `frontend/` and open the URL Vite prints (typically
   `http://localhost:5173`).
2. On the **Setup** tab, complete the minimum required inputs (photo + first name + the
   four mandatory category picks; or just photo + first name and click **Surprise Me**).
3. Click **Generate my alter ego** (or **Surprise Me**). The "Your Alter Ego" tab
   becomes active and the existing pulsating circle appears with the
   "Generating your alter ego…" copy.
4. **Verify P1 / FR-1301**: an integer percentage with a `%` sign is visible inside the
   inner dark disc of the pulsating ring. It should be readable against the background
   (light text on dark disc). Initial value should be `0%` or `1%`.
5. **Verify P1 / FR-1303 / FR-1306**: leave the page alone for ~30 seconds. The
   percentage should advance multiple times (typically 4–10 updates), at irregular
   intervals — not a steady tick. The number should never go down.
6. **Verify P1 / FR-1305** (if your generation is fast — usually the case in the FE
   fallback path which returns near-instantly): the loading view will swap to the poster
   before the percent gets anywhere near the cap. **Confirm** you never saw "100%".
7. **Verify P1 / FR-1305 — long-generation path**: in DevTools, throttle network to "Slow 3G"
   (or use the in-product "real Gemini" path if your `.env` has the key). Trigger a fresh
   generation. Wait until elapsed time exceeds 20 s. The displayed value should plateau
   at `99%` and stay there. **Confirm** the number "100" is never visible.
8. **Verify P1 / FR-1308**: when the poster appears, click **Start over**, complete the
   form again, click **Generate**. The percent should restart from a low value, not
   resume from where the previous run left off.
9. **Verify P3 / FR-1309 (a11y)**: with VoiceOver / NVDA enabled, trigger a generation.
   You should hear "Generating your alter ego…" once. You should NOT hear the screen
   reader announcing each percent update.
10. **Verify SC-1306 (no regression)**: the conic-gradient ring still spins, the pulse
    animation still pulses, the numbered steps list ("Analysing your photo / Shaping the
    character / Rendering the poster") still renders unchanged below the ring.

## Edge-case spot checks

- **Tab backgrounded** (Edge Case in spec.md): start a generation, switch to another tab
  for ~20 s, come back. The percent should reflect ~20 s of elapsed time (i.e. ~50%),
  not "0% because the throttled tab only ticked once".
- **Reduced motion**: in OS settings enable "Reduce motion". Re-trigger a generation.
  The pulse animation may calm down (per existing site CSS), but the percentage text
  itself should still render and advance — it is plain text, not animated.

## Stop conditions

If any of the following is observed, the implementation is incorrect — file a defect:

- The displayed value reads `"100%"` at any point while the loading view is still visible.
- The displayed value goes down between two consecutive observations.
- The screen reader announces every percent tick.
- The numbered steps list, the spin animation, or the polite "Generating…" announcement
  changes from before this feature.
