# Quickstart — 026: Wording Updates for User Roles

**Date**: 2026-05-18
**Audience**: Reviewers + future-you doing local validation
**Time budget**: ~20 minutes for the full sweep (5 minutes per renamed Role, per SC-2605)

---

## Run locally

```bash
# from repo root
cd frontend && npm install   # only if node_modules is stale
npm run dev                  # Vite dev server on http://localhost:5173

# in a second terminal, from repo root
cd backend && ./gradlew bootRun   # Spring Boot on http://localhost:8080
```

Both servers are required: the Setup-tab assertion (User Story 1) needs only the frontend; the poster-text assertion (User Story 2) needs both, because the poster's Role string is composed server-side by `TextStage`.

---

## Validate User Story 1 — Setup-tab Role grid (P1)

1. Open `http://localhost:5173`.
2. Capture or upload a photo to advance to the Setup tab.
3. Scroll to the Role grid (the section labelled **Role**).
4. Visual scan: confirm each of the nine options reads as follows.

   | Stable identity | Expected label |
   |---|---|
   | `cloud-architect` | Cloud Architect |
   | `backend-dev` | **Backend Developer** ✅ new |
   | `frontend-dev` | **Frontend Developer** ✅ new |
   | `ai-engineer` | AI Engineer |
   | `platform-eng` | **Platform Engineer** ✅ new (no trailing period) |
   | `data-engineer` | Data Engineer |
   | `hr` | **People Operations** ✅ new |
   | `administration` | Administration |
   | `customer-relations` | Customer Relations |

5. The grid is **9 cells, three columns × three rows**, ordering preserved. Each cell still has the same icon it had before.
6. Tab into the grid with the keyboard; the focus ring lands on each cell in the same order; the accessible name announced by the screen reader equals the visible label.

✅ Pass criteria: every renamed cell reads in the new wording (4/4); none of the other five cells changed.

---

## Validate User Story 2 — Poster text on the Alter Ego tab (P1)

Repeat steps 1–4 below for each of the four renamed Roles (`backend-dev`, `frontend-dev`, `platform-eng`, `hr`).

1. On the Setup tab: type a first name, leave the custom-role field blank, click the Role cell under test, pick a Universe and an Art Style.
2. Click **Generate my alter ego**. Wait for the loading view to advance to the Alter Ego tab.
3. Read the Role text printed on the poster (it appears under or near the alter-ego name, baked into the rendered image).
4. Confirm it reads in the new wording — the same wording the Setup grid showed for that Role.

| Picked on Setup | Expected on poster |
|---|---|
| `backend-dev` | **Backend Developer** |
| `frontend-dev` | **Frontend Developer** |
| `platform-eng` | **Platform Engineer** |
| `hr` | **People Operations** |

5. Click **Start Over** between runs; pick a different Role; repeat.

✅ Pass criteria: each of the four posters shows the new wording, character-for-character matching the Setup grid (SC-2602).

---

## Validate edge case — free-form custom Role overrides prefab labels

1. On Setup, type any string into the Custom Role input (e.g. *"Chief Belly Rub Officer"*).
2. The Role grid blurs / disables.
3. Generate. The poster prints **"Chief Belly Rub Officer"** verbatim — no prefab label leaks through.

This proves FR-2607 (only prefab Roles change; custom Roles pass through verbatim, untouched).

---

## Validate edge case — Surprise Me lands on a renamed Role

1. On Setup, click **Surprise Me** repeatedly (or run it 10–20 times) until it lands on `hr`, `backend-dev`, `frontend-dev`, or `platform-eng`. (Each has a 1-in-9 chance, so 20 rolls usually cover all four. If you want determinism, edit `frontend/src/lib/randomSelections.ts` injectable RNG in DevTools.)
2. Confirm the picked cell in the Setup grid reads in the new wording.
3. Generate; confirm the poster reads in the new wording.

✅ Pass criteria: Surprise Me selection of any of the four renamed Roles displays + prints in the new wording (FR-2609).

---

## Validate the wire payload (SC-2604 — byte-identical)

1. Open browser DevTools → Network tab → Filter `alter-egos`.
2. Pick **Backend Developer** on Setup, click Generate, and inspect the outgoing `POST /api/v1/alter-egos` request body.
3. Confirm: `"archetype": "backend-dev"` (kebab-case stable identifier, **not** the display label "Backend Developer").
4. Repeat with **People Operations** (`hr`), **Frontend Developer** (`frontend-dev`), **Platform Engineer** (`platform-eng`).

✅ Pass criteria: every request sends the same nine wire values it would have sent before this change. **No request body sends `"backend-developer"`, `"people-operations"`, `"frontend-developer"`, or `"platform-engineer"` — those are display strings, not wire identifiers.**

---

## Automated checks

```bash
# from repo root
cd frontend && npm test -- src/features/alterego/components/ArchetypeGrid.test.tsx
# Expect: all assertions in the "renders 9 options in order" test reflect the new wording.

cd ../backend && ./gradlew unitTest --tests com.aiavatar.alterego.unit.EnumsTest
# Expect: Archetype.HR.label() == "People Operations" (etc.) assertions pass.

# Full suite, every tier:
./gradlew test
# Expect: green. Coverage ≥ 90% (Constitution III).

# Contract tier sanity (paranoia check on the wire enum):
./gradlew contractTest
# Expect: green; no JSON shape changed.
```

---

## Out-of-scope sanity (negative checks)

The following continue to do their thing unchanged. If any of these flips behaviour, the change has grown beyond the spec.

- `backend/.../infrastructure/provider/gemini/GeminiPromptBuilder.ROLE_LABELS.get(HR)` continues to return `"Human Resources"` (private to the model — not the guest-facing label). See research R2.
- `backend/.../infrastructure/provider/falai/FalAiPromptBuilder.ROLE_LABELS.get(HR)` — same.
- `AccentResolver.deriveAccent(...)` returns the same accent palette per Role enum identity (it never reads `.label()`).
- The OpenAPI YAML pinned by `contract/` tests is byte-identical.
- No new file under `frontend/src` or `backend/src/main` was added by this feature. The only new files live under `specs/026-role-label-wording/`.
