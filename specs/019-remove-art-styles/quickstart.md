# Quickstart: 019-remove-art-styles

**Feature**: Remove Line Art, Low-Poly 3D, and Pixel Art from Art Style category
**Audience**: A developer (or reviewer) who has not been in the planning conversation and needs to (a) reproduce the change locally, or (b) verify the deployed change behaves correctly.

This is a **subtractive** feature: nothing new is built, six options remain where nine used to be. The whole change is mechanical — TypeScript and `javac` both light up the exact set of files that need updating once the source-of-truth declarations are trimmed.

---

## 1. Prerequisites

- Repo checked out and the `019-remove-art-styles` branch checked out:
  ```sh
  git switch 019-remove-art-styles
  ```
- Node / Java toolchains as per `CLAUDE.md` (Node 20 + Vite 8 on the frontend; Java 21 + Gradle on the backend).

---

## 2. Reproduce the implementation locally (Red → Green TDD order)

### 2a. Frontend — RED first

Update the user-facing tests so they assert the new six-member set. These tests will fail against today's nine-member source-of-truth.

1. **`frontend/src/features/alterego/components/ArtStyleGrid.test.tsx`** — change "renders nine tiles" → "renders six tiles"; drop the three retired-value test rows.
2. **`frontend/src/features/alterego/lib/randomSelections.test.ts`** — keep the existing first/last-element picks (their indices remain valid against the trimmed array because the array is still non-empty); add a new `it('never picks a retired Art Style across ≥1 000 draws')` test (this is SC-1902's measurable guarantee).
3. **Every test file that uses `'pixel-art' | 'low-poly-3d' | 'line-art'` as a placeholder `artStyle`** — swap to `'oil-painting'` (the natural default — first in the array, broadly representative). The TS compiler will list these for you in step 2b.

Commit the failing tests:
```sh
cd frontend
npm test -- --run            # confirm tests RED (or TypeScript errors at fixture sites)
git add -p
git commit -m "test(019): RED — Art Style category is now a six-member set"
```

### 2b. Frontend — GREEN

Trim the source-of-truth declarations:

1. **`frontend/src/features/alterego/types.ts`** — remove the three string literals (`'pixel-art' | 'low-poly-3d' | 'line-art'`) from the `ArtStyle` union.
2. **`frontend/src/features/alterego/options.ts`** — remove the three matching rows from `ART_STYLE_OPTIONS`.

Now run `tsc --noEmit` (or `npm run lint && npm test -- --run`). Any **remaining** TS errors are exactly the test fixtures step 2a missed — fix each one by swapping the retired value to `'oil-painting'`. Re-run until green.

```sh
npm run lint
npm test -- --run            # confirm tests GREEN
```

Sanity-check the dev server:
```sh
npm run dev
# Open http://localhost:5173, navigate to Setup → Art Style:
#   - exactly six tiles render
#   - none is labelled Pixel Art / Low-Poly 3D / Line Art
#   - tile order: Oil Painting → Watercolor → Pop Art → Renaissance Portrait
#                  → Japanese Woodblock → Cel-Shaded
# Click Surprise Me ~20 times: the picked Art Style is always one of the six.
```

### 2c. Backend — RED first

Same RED-first protocol. Update fixtures and cardinality assertions before changing source.

1. **`backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java`** — change the `ArtStyle.values().length` assertion from `9` to `6`; verify the surviving members' `wire()` / `label()` strings.
2. **`backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoControllerInputValidationIT.java`** — add a parameterised case: a request body with `"artStyle":"pixel-art"` (or `"low-poly-3d"`, or `"line-art"`) returns HTTP 400 with an RFC 7807 problem-detail body. (FR-1903)
3. **`GeminiPromptBuilderTest`, `GeminiCharacterPromptBuilderTest`, `FalAiPromptBuilderTest`** — delete the parameterised rows for the three retired values.
4. **Every backend test file that uses `ArtStyle.PIXEL_ART | LOW_POLY_3D | LINE_ART` as a placeholder fixture** — swap to `ArtStyle.OIL_PAINTING`. `javac` (`./gradlew compileTestJava`) will list these for you in step 2d.

```sh
cd backend
./gradlew test                # confirm tests RED (or javac errors at fixture sites)
git add -p
git commit -m "test(019): RED — backend Art Style is now a six-member enum"
```

### 2d. Backend — GREEN

1. **`backend/src/main/java/com/aiavatar/alterego/model/ArtStyle.java`** — remove the `PIXEL_ART`, `LOW_POLY_3D`, `LINE_ART` constants.
2. **`backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java:55-59`** — remove the three `ART_STYLE_LABELS.put(...)` lines.
3. **`backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiCharacterPromptBuilder.java:75-77`** — remove the three `ART_STYLE_LABELS.put(...)` lines.
4. **`backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java:60-64`** — remove the three `ART_STYLE_LABELS.put(...)` lines.

Run the test suite; any remaining `javac` "cannot find symbol PIXEL_ART/LOW_POLY_3D/LINE_ART" diagnostics are exactly the test fixtures step 2c missed — swap each to `ArtStyle.OIL_PAINTING`. Re-run until green.

```sh
./gradlew test                # confirm tests GREEN
./gradlew check               # full gates including coverage; expect ≥ 90 % preserved
```

### 2e. Contract delta

The 019 OpenAPI delta is already published at `specs/019-remove-art-styles/contracts/alter-egos.openapi.yaml`. No further edit is required during implementation; just verify the file matches the surviving enum members and lists the removed values in `info.description`.

### 2f. Open the PR

```sh
git push -u origin 019-remove-art-styles
gh pr create --base main \
  --title "feat(019): retire Pixel Art, Low-Poly 3D, Line Art (closes #49)" \
  --body "Removes three Art Style options from both manual selection and Surprise Me; see specs/019-remove-art-styles/spec.md."
```

---

## 3. Verify the deployed change behaves correctly

Run these checks against any environment where the feature has shipped.

### 3a. Manual UI (FR-1901)

- Load the Setup tab. The Art Style grid renders **exactly six** tiles. None is labelled Pixel Art, Low-Poly 3D, or Line Art. Tile order, labels, and icons for the six survivors match feature 006.
- Keyboard-navigate the grid (`Tab`, arrow keys). Focus moves through six tiles only; no hidden tile is reachable.
- Inspect the DOM. No `data-value="pixel-art" | "low-poly-3d" | "line-art"` attribute exists anywhere on the page.

### 3b. Surprise Me randomiser (FR-1902, SC-1902)

- Press Surprise Me 20 times in the UI. Every committed Art Style is one of the six survivors. (Visual smoke test.)
- The deterministic unit test `randomSelections.test.ts` runs ≥ 1 000 simulated draws with a uniform RNG and asserts zero retired-value outcomes. (CI coverage.)

### 3c. Stale-tab safety (FR-1903)

```sh
curl -i -X POST http://localhost:8080/alter-egos \
  -F 'photo=@frontend/public/sample-photo.jpg' \
  -F 'selections={"pose":"heroic","archetype":"backend-dev","universe":"marvel","artStyle":"pixel-art","photoMode":"single","firstName":"Paula"};type=application/json'
# Expected: HTTP/1.1 400 Bad Request
# Expected body: RFC 7807 problem-detail mentioning the invalid artStyle field.
```

Repeat for `low-poly-3d` and `line-art`; expect HTTP 400 each time. There must be **no** silent substitution — the response body must not contain a surviving Art Style value in any field that reflects the rejected selection.

### 3d. Surviving paths still work (FR-1904, FR-1906, SC-1904)

For each of the six survivors (`oil-painting`, `watercolor`, `pop-art`, `renaissance-portrait`, `japanese-woodblock`, `cel-shaded`), trigger a Generate request and confirm:

- The request returns `200 OK` with `meta.outcome` of either `real` or `fallback` — same as before 019.
- The poster, branding, frame, text overlay, print flow, and progress indicator behave exactly as in features 008 / 010 / 013 / 015 / 017 / 018.

### 3e. No-persistence posture (inherited from 001 FR-016 / FR-017 / FR-024)

The retired Art Style values are not logged, not cached, not persisted to disk anywhere. The existing log-redaction tests (`LogRedactionIT`, `LogRedactionGeminiTextIT`) continue to assert this; no new persistence has been introduced.

---

## 4. Rollback

This is a wire-spec change but it is **backwards** rather than forwards-incompatible — the new API is a strict subset of the old API. To roll back: revert the commit. Stale clients that already moved to the trimmed set will not break because every six-member-era request body is also a valid nine-member-era request body. The reverse direction (nine-member-era clients sending retired values to the six-member-era server) is the FR-1903 case and is expected to fail with a 400.

There is no data migration to undo (no persistence).
