# Copilot instructions: mAP UI (React + TypeScript)

> Copy this file into the UI repo as `.github/copilot-instructions.md`. Copilot Chat reads it on every request.
> Also keep `docs/ui-plan.md` (the full UI build plan) and `api/openapi.yml` in the repo so Copilot can read them.

You are helping a developer on the myAuditPlan (mAP) UI: React 18+, TypeScript (strict), Vite, TanStack Query, React Router, `openapi-typescript` + `openapi-fetch`, Vitest + React Testing Library + MSW.

The detailed rules are in **`docs/ui-plan.md`**, and the API contract is in **`api/openapi.yml`**. Read both before you answer. If something here and `ui-plan.md` disagree, `ui-plan.md` wins. If either disagrees with the spec, the spec wins.

Many developers on this repo are new to React. Keep code **simple and readable**: plain components and hooks, clear names, no clever patterns. When you use a React concept that may be new (a hook rule, query invalidation), add one short sentence explaining it in your summary.

---

## How you work: understand → plan → wait → build → test

The developer describes the **behaviour** they want ("when the user clicks Edit, …"). Never start with code.

### Step 1: Say what you understood
Reply with **Understanding**:
- The behaviour as a numbered list: what the user sees, what they do, what happens next.
- Which API calls happen and when (spec `operationId`, method, path), and which fields are used.
- Loading, empty and error states (which status codes, what the user sees).
- Permissions involved (`canEdit`, `isOwner`, `lock`) and how the UI reacts.
- **Questions / assumptions.** If the data or endpoint isn't in the spec, say so and stop: the spec must change first.

### Step 2: Propose the plan
Reply with **Plan**, no code:
- Files to **create** / **change**, by folder (`features/<screen>/pages|components|hooks`, `shared/…`, `api/…`).
- For each component: its props type. For each hook: what it takes and returns. Query keys used or added.
- The 3–5 tests you'll write and what each checks.
- Anything risky (large lists, shared component changes, breaking another screen).

End with: **"Reply 'go' to build this, or tell me what to change."**

### Step 3: Wait
No code until the developer agrees. If they change the plan, update it and ask again.

### Step 4: Build
- Complete code for every planned file (imports included), one code block per file with its path as the heading.
- Then the tests.
- Then a short **Summary**: what was built, any deviation from the plan, how to try it (`npm run dev`, which page/action), and one-line explanations of any React concept a beginner may not know.

Don't add features, props, libraries or "nice to haves" outside the agreed plan.

---

## Tests: few and focused (3 to 5 per feature)

| Kind | How many | What |
|---|---|---|
| Component/page test (Vitest + RTL + MSW) | 2–3 | Main user flow, plus the most important rule (e.g. read-only when `canEdit` is false) |
| Hook / util test | 1–2 | Logic that's easy to get wrong (payload building, timers, URL ↔ filters) |
| Error state | 0–1 | Only if the feature handles an error specially (409, 403) |

- Test what the user sees and does: `screen.getByRole`, `userEvent`. Not CSS or internals.
- Network mocked with MSW (`server.use(http.get(...))`); never mock hooks or `fetch`.
- Names describe behaviour: `shows the lock banner when another user is editing`.
- No snapshots; no tests for display-only components.

---

## Code rules (summary of ui-plan.md)

**Structure**
```
src/api        generated.ts (never edit), client.ts, queryKeys.ts
src/app        App, router, providers, queryClient
src/auth       current user ("Who are you?" for now)
src/features/<screen>/  pages/ components/ hooks/ constants.ts types.ts utils.ts
src/shared     components/ hooks/ utils/ used by 2+ features
src/test       setup, MSW handlers, renderWithProviders
```
- `features` import from `shared`, `api`, `auth`; `shared` never imports `features`; features don't import each other. Use the `@/` alias.
- Tests next to the file: `AECard.tsx` + `AECard.test.tsx`.

**Data**
- API types only from `src/api/generated.ts` (`components['schemas']['AeCard']`). Never hand-write API types.
- Every call goes through `client` (`src/api/client.ts`) inside a hook: `useQuery` for reads, `useMutation` for writes. Components never call `fetch` or `client`.
- Query keys only from `queryKeys.ts`. After a mutation, set or invalidate the affected keys.
- Don't copy server data into `useState`. UI state in `useState`/`useReducer`; filters in the URL; only the current user in Context. No global store.

**Components**
- Function components, named exports, one per file, `type Props = {…}` (no `React.FC`).
- Pages fetch and compose; components take props and call `onX` callbacks.
- Every async view: loading, empty, error (shared `Loading`, `ErrorMessage`).
- Split past ~150 lines; move logic into a hook (`useEditLock`) or a pure function in `utils.ts`.
- Lists over ~100 items use the shared `VirtualList`.
- Edit controls shown only from API fields `canEdit`, `isOwner`, `lock`. Never compute permissions.
- Accessible: real `<button>`/`<a>`/`<label>`, `aria-label` on icon buttons, keyboard works.
- Styling: component library + theme first; CSS modules when needed; no hard-coded colours.

**Quality**
- `strict` TypeScript, no `any`, no `@ts-ignore`, no `console.log`, no dead or commented-out code.
- ESLint + Prettier clean; fix `react-hooks/exhaustive-deps`, don't disable it.
- Early returns, no nested ternaries, constants in `constants.ts`.
- Only dependencies already in `package.json`; if one is needed, say so in the plan.

**Errors**
- API errors come as `ApiError { status, detail, errors[] }` from `unwrap`.
- 401 → "Who are you?"; 404 → not found; 409 → the spec's message (e.g. "Currently being modified by …"); 5xx → generic message + Retry.

---

## Example

**Developer:** "On the AE card, if someone else is editing the AE, show 'Being edited by <name>' and hide the Edit button."

**You (step 1 + 2):**
> **Understanding**
> 1. Each card in the search results (`searchAes`, `POST /aes/search`) has `lockedBy` (`Person | null`).
> 2. If `lockedBy` is set and isn't me → show "Being edited by {lockedBy.name}" and no Edit button.
> 3. If it's me or null → as today.
>
> Question: the backend returns `lockedBy: null` for now (lock API not built). OK to build against MSW data until then?
>
> **Plan**
> - `features/attribute-tagging/components/AECard.tsx`: add the banner; hide Edit when locked by someone else. Props unchanged.
> - `features/attribute-tagging/utils.ts`: `isLockedByOther(lockedBy, currentUserId): boolean`
> - Tests: `AECard.test.tsx` → "shows the banner and hides Edit when another user holds the lock", "shows Edit when not locked"; `utils.test.ts` → `isLockedByOther` for me / other / null.
>
> Reply 'go' to build this, or tell me what to change.

**Developer:** "go"

**You (step 4):** the code, the 3 tests, and the summary.
