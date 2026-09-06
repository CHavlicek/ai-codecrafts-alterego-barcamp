# Frontend State Conventions

Three named concerns, three designated mechanisms (Constitution Principle VII / Spec FR-2416). New UI work picks the right mechanism per concern; cross-cutting changes that touch more than one require explicit review.

## 1. Server state — TanStack Query

In-flight requests, responses, and errors against the backend live in **TanStack Query** (`@tanstack/react-query`) only. The two canonical entry points:

- `useGenerateAlterEgo` (`src/features/alterego/hooks/useGenerateAlterEgo.ts`) — drives `POST /api/v1/alter-egos`.
- `useSendAlterEgoEmail` (`src/features/alterego/hooks/useSendAlterEgoEmail.ts`) — drives `POST /api/v1/alter-egos/email`.

If a new feature needs another server-bound flow, add a sibling hook in `hooks/` that wraps `useMutation` (or `useQuery` for read paths). **Do not** re-implement loading / error state on the side.

`QueryClient` is instantiated once in `src/main.tsx`; no per-feature client.

## 2. Session state — the reducer + provider

Cross-component user state for one in-browser session (selections, photo, active tab, generation phase, email phase, etc.) lives in the `AlterEgoSessionState` reducer (`src/features/alterego/state/reducer.ts`) plus `AlterEgoProvider` + `useAlterEgoSession()` (`state/AlterEgoProvider.tsx`, `state/context.ts`). The reducer's action union is **closed**; adding a new field requires a new action and an obvious home in the reducer table.

Use **memoized selectors** (`state/selectors.ts`) for derived values that more than one component reads. Selectors must be referentially stable when their inputs are unchanged (FR-2417).

## 3. Component-local UI state — `useState`

Transient flags scoped to one component (hover, focus, transient open/close, in-progress drag, …) live in plain `useState` _inside the component_. **Do not** lift these into the reducer just because they cross a render. If something starts out local and later needs to be read by another component, _that_ is the moment to promote it to session state.

## Enforced by

- A custom ESLint rule in `eslint.config.js` forbids `useReducer` outside `src/features/alterego/state/` (FR-2418).
- Selector memoization is asserted by a Vitest test (re-renders without input change must not invoke the selector body).
- The `AlterEgoProvider` returns frozen selector references; consumers should call selector functions, not pass them through their own `useMemo`.

## Anti-patterns to reject in review

- A second `useReducer` outside `state/` (ESLint will fail before review).
- Server state mirrored into the reducer's `generationResult` for any reason other than what `useGenerateAlterEgo`'s `onSuccess` already does.
- Component-local state lifted into the reducer "just in case" some future component needs it. Wait until the second consumer is real.
- A second `QueryClient` instance.
