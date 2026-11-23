# ui-plan.md: myAuditPlan (mAP) UI build plan

The single plan for building the mAP UI (React + TypeScript). Give this file to GitHub Copilot together with the API spec, and it should generate code that is simple, consistent and easy to maintain.

> **How to use it with Copilot**
> 1. The screen's endpoints must already be in `api/openapi.yml` (section 0). No spec, no UI call.
> 2. Describe the **behaviour** you want ("when the user clicks Edit…"). Copilot replies with its understanding and plan, and builds only after you say **"go"** (0.3).
> 3. Review against the **Definition of Done** (section 13) before you raise the PR.
>
> If this file and older docs disagree, **this file wins**. If anything disagrees with `api/openapi.yml`, **the spec wins**.

---

## 0. Spec-first, from the UI side

The backend works spec-first (`plan.md` section 0). The UI follows the same contract:

### 0.1 Rules
- **Types come from the spec, never by hand.** Generate them with `openapi-typescript` into `src/api/generated.ts` and never edit that file. Regenerate whenever the spec changes: `npm run api:types`.
- **Call only endpoints that are in the spec.** If the screen needs something the API doesn't give, ask the backend developer to change the spec first; don't work around it in the UI.
- **Don't wait for the backend.** Build against a mock: MSW handlers in tests and dev, or `npx @stoplight/prism-cli mock api/openapi.yml` as a local fake server.

### 0.2 Workflow for every story

| Step | What | Done when |
|---|---|---|
| 1. Spec | Endpoint and fields agreed in `api/openapi.yml` (UI developer reviews the spec PR) | Spec merged |
| 2. Types | `npm run api:types` | `generated.ts` updated, `tsc` clean |
| 3. Build | Components + hooks with Copilot (0.3), against MSW/Prism | Screen works on mock data |
| 4. Test | 3–5 tests (section 10) | Green |
| 5. Integrate | Point `VITE_API_BASE_URL` at the real backend, check the flow | Works end to end |

### 0.3 Working with Copilot: understand → plan → approve → build

| Step | Copilot | Developer |
|---|---|---|
| 1. Understanding | Restates the behaviour as a numbered list: what the user sees and does, which API calls happen when, loading/empty/error states, permissions. Lists questions. If the needed data isn't in the spec, stops and says what's missing. | Checks the behaviour is right; answers the questions. |
| 2. Plan | Files to create/change by folder, components and hooks with their props/return types, query keys, the 3–5 tests. No code. | Asks for changes or replies **"go"**. |
| 3. Build | The agreed code and tests, then a summary of how to try it. | Runs it, checks the Definition of Done, raises the PR. |

Setup in the UI repo:
- `.github/copilot-instructions.md` ← `copilot/ui-copilot-instructions.md`
- `.github/prompts/ui-feature.prompt.md` ← `copilot/prompts/ui-feature.prompt.md`
- `docs/ui-plan.md` ← this file; `api/openapi.yml` ← the spec

---

## 1. Tech stack

| | |
|---|---|
| Language | TypeScript, `strict: true` |
| UI | React 18+ with function components and hooks only |
| Build | Vite |
| Server data | TanStack Query (React Query) |
| API types / client | `openapi-typescript` (types) + `openapi-fetch` (typed `fetch` wrapper) |
| Routing | React Router |
| Components / styling | The component library already in `package.json`; don't add a second one |
| Long lists | `@tanstack/react-virtual` |
| Tests | Vitest + React Testing Library + MSW |
| Quality | ESLint (typescript-eslint, react-hooks, jsx-a11y) + Prettier |

Use only libraries already in `package.json`. No Redux, no MobX, no form library unless a form truly needs it: ask first.

## 2. Principles

- **KISS.** Plain components, plain hooks, plain functions. If you can't explain a piece of code in one sentence, simplify it.
- **One way to do each thing:** one API client, one query-key factory, one error display, one loading pattern.
- **The server decides, the UI shows.** Permissions (`canEdit`, `isOwner`, `lock`), sorting, filtering and paging come from the API. The UI never recalculates them.
- **Small pieces.** A component does one job; past ~150 lines, split it. Logic goes into a custom hook; components mostly render.
- **YAGNI.** No generic "framework" components for a single use, no props "for later".
- **Readable over clever.** Clear names, early returns, no nested ternaries, no magic numbers.

## 3. Folder structure

Feature-based: everything for one screen lives together; only truly shared things go in `shared`.

```
src/
├── api/                     the only place that talks to the backend
│   ├── generated.ts         from openapi-typescript (never edit)
│   ├── client.ts            openapi-fetch client: base URL, X-User-Id header, error handling
│   └── queryKeys.ts         every TanStack Query key, in one factory
├── app/                     app shell
│   ├── App.tsx
│   ├── router.tsx
│   ├── providers.tsx        QueryClientProvider, CurrentUserProvider, theme
│   └── queryClient.ts
├── auth/                    who the user is (temporary "Who are you?" screen; JWT later)
│   ├── CurrentUserContext.tsx
│   └── WhoAreYouPage.tsx
├── features/
│   └── attribute-tagging/   one folder per screen
│       ├── pages/           route-level components (fetch data, compose)
│       │   └── AttributeTaggingPage.tsx
│       ├── components/      pieces of this screen (AECard, AEDetail, AttributeSection, …)
│       ├── hooks/           data + logic hooks (useAESearch, useAEDetail, useEditLock, …)
│       ├── constants.ts     labels (filter type → label), timings
│       ├── types.ts         UI-only types (never API types: those come from generated.ts)
│       └── utils.ts         pure functions (build the update payload, …)
├── shared/                  used by 2+ features
│   ├── components/          FilterBar, FilterChips, SavedFilters, Pagination, ErrorMessage, Loading, VirtualList
│   ├── hooks/               useUrlFilters, useDebounce
│   └── utils/
└── test/
    ├── setup.ts             RTL + MSW setup
    ├── handlers.ts          MSW handlers (default happy responses)
    └── renderWithProviders.tsx
```

**Import rules**
- `features/*` may import from `shared`, `api`, `auth`. `shared` never imports from `features`. Features never import from each other.
- Components never call `fetch` or `client` directly; they use hooks, and hooks use `api/client.ts`.
- Use the `@/` path alias (`@/shared/components/FilterBar`), no `../../../`.
- Tests sit next to the file they test: `AECard.tsx` + `AECard.test.tsx`.

## 4. Layer by layer

### 4.1 API layer (`src/api`)
- Types: `npm run api:types` → `openapi-typescript ../api/openapi.yml -o src/api/generated.ts`.
- One typed client; the base URL from `VITE_API_BASE_URL` (e.g. `http://localhost:8080/api/v1`; spec paths start after `/api/v1`); adds `X-User-Id` on every call; turns errors into one `ApiError` type.

```ts
// src/api/client.ts
import createClient from 'openapi-fetch';
import type { paths } from './generated';
import { getCurrentUserId } from '@/auth/CurrentUserContext';

export const client = createClient<paths>({ baseUrl: import.meta.env.VITE_API_BASE_URL });

client.use({
  onRequest({ request }) {
    const userId = getCurrentUserId();
    if (userId) request.headers.set('X-User-Id', userId);
    return request;
  },
});

export type ApiError = { status: number; detail: string; errors?: { field: string; message: string }[] };

/** Unwraps an openapi-fetch result: returns data or throws ApiError (ProblemDetail from the backend). */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error || result.data === undefined) {
    const problem = (result.error ?? {}) as Partial<ApiError>;
    throw { status: result.response.status, detail: problem.detail ?? 'Something went wrong', errors: problem.errors } as ApiError;
  }
  return result.data;
}
```

```ts
// src/api/queryKeys.ts
export const queryKeys = {
  filters: (screen: string) => ['filters', screen] as const,
  aes: (filters: Record<string, string[]>, page: number) => ['aes', filters, page] as const,
  ae: (aeId: string) => ['ae', aeId] as const,
  attributes: (type: string) => ['attributes', type] as const,
  children: (type: string, attributeId: string) => ['children', type, attributeId] as const,
  savedFilters: (userId: string) => ['savedFilters', userId] as const,
};
```

### 4.2 Server data: one hook per endpoint
- Every API call is wrapped in a hook in the feature's `hooks/` (or `shared/hooks` if shared): `useQuery` for reads, `useMutation` for writes.
- Hooks return what TanStack Query returns (`data`, `isLoading`, `error`…); components don't reshape server data into local state.
- After a mutation, update or invalidate the affected keys in `onSuccess`.

```ts
// src/features/attribute-tagging/hooks/useAESearch.ts
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { client, unwrap } from '@/api/client';
import { queryKeys } from '@/api/queryKeys';

export function useAESearch(filters: Record<string, string[]>, page: number, pageSize = 20) {
  return useQuery({
    queryKey: queryKeys.aes(filters, page),
    queryFn: async () =>
      unwrap(
        await client.POST('/attribute-tagging/aes/search', {   // path as in the spec; /api/v1 is in the base URL
          params: { query: { page, pageSize } },
          body: { screen: 'attributeTagging', filters },
        }),
      ),
    placeholderData: keepPreviousData,     // keep the old page while the next loads
  });
}
```

### 4.3 UI state
- Local UI state (open/closed, selected tab, edit draft) → `useState`, or `useReducer` when there are several related values.
- **Filters live in the URL** (`useUrlFilters` in `shared/hooks`), so a filtered view can be shared and survives refresh.
- Shared app state (current user) → React Context. Nothing else goes in context.
- No global store.

### 4.4 Components
- Function components, one per file, file named after the component (`AECard.tsx`), named export.
- `type Props = { … }` right above the component; no `React.FC`.
- **Pages** (`pages/`) call hooks and compose; **components** receive data through props and raise events through callbacks (`onEdit`, `onRemove`). Keep the data fetching in pages and hooks.
- Every async view handles **loading, empty and error** states, using the shared `Loading` and `ErrorMessage` components.
- Lists of more than ~100 items use `VirtualList` (dropdown options and All Available can have 2,000+).

```tsx
// src/features/attribute-tagging/components/AECard.tsx
import type { components } from '@/api/generated';

type AEResponse = components['schemas']['AEResponse'];

type Props = {
  ae: AEResponse;
  onOpen: (aeId: string) => void;
};

export function AECard({ ae, onOpen }: Props) {
  return (
    <article className="ae-card" aria-label={`Auditable entity ${ae.aeName}`}>
      <button type="button" onClick={() => onOpen(ae.aeId)}>
        <h3>{ae.aeName}</h3>
      </button>
      <p>{ae.aeId} · {ae.businessArea}</p>
      <p>{ae.recommendationCount} Recommendations</p>
      <p>{ae.aeDescription}</p>
    </article>
  );
}
```

### 4.5 Hooks with logic
- Behaviour that isn't just "fetch this" goes into a named hook: `useEditLock` (take/renew/release the lock), `useTaggingDraft` (added/removed items, comments).
- A hook does one thing and returns a small object with clear names (`{ isEditing, startEdit, stopEdit }`).
- Pure calculations (build the update payload, filter a list) go in `utils.ts` as plain functions: easy to test.

### 4.6 Errors and messages
- All API errors arrive as `ApiError` (`status`, `detail`, `errors[]`).
- Show `detail` with the shared `ErrorMessage`; map `errors[].field` onto form fields where relevant.
- 401 → back to "Who are you?". 404 → "Not found" message. 409 → the specific message from the spec (e.g. "Currently being modified by …"). 5xx → generic message with **Retry**.
- One app-level error boundary shows a friendly page instead of a blank screen.
- No `console.log` in committed code; `console.error` only inside the error boundary.

### 4.7 Permissions
- Show or hide edit controls only from what the API returns (`canEdit`, `isOwner`, `lock`). Never work out ownership or lock rules in the UI; the backend checks them anyway.

### 4.8 Accessibility
- Real elements: `<button>` for actions, `<a>`/`Link` for navigation, `<label>` for inputs.
- Every icon-only button has an `aria-label`. Everything works with the keyboard.
- Don't rely on colour alone (the green "new attribute" also has a text badge).

### 4.9 Styling
- Use the component library's components and theme first. Custom CSS only when needed, one CSS module per component (`AECard.module.css`).
- No inline style objects except for dynamic values; no hard-coded colours, use theme tokens.

## 5. Naming

| Thing | Pattern | Example |
|---|---|---|
| Component | `PascalCase.tsx` | `AttributeSection.tsx` |
| Hook | `useCamelCase.ts` | `useEditLock.ts` |
| Page | `<Screen>Page.tsx` | `AttributeTaggingPage.tsx` |
| Util / constant file | `camelCase.ts` | `utils.ts`, `constants.ts` |
| Test | `<file>.test.tsx` | `AECard.test.tsx` |
| Event props | `on<Event>` | `onOpen`, `onRemove` |
| Booleans | `is/has/can…` | `isEditing`, `canEdit` |
| Constants | `UPPER_SNAKE` | `LOCK_RENEW_MS` |
| Folders | `kebab-case` | `attribute-tagging` |

API field names stay exactly as in the spec (`aeId`, `recommendationCount`); don't rename them in the UI.

## 6. Code quality

- TypeScript `strict`; **no `any`** (use the generated types, or `unknown` and narrow). No `@ts-ignore`.
- ESLint + Prettier clean (run on save; CI fails on warnings). `react-hooks/exhaustive-deps` warnings are fixed, not disabled.
- Functions do one thing; prefer early returns; max 3 levels of nesting.
- No dead code, no commented-out code, no unused exports.
- Comments explain **why**, not what. Public hooks and shared components get a one-line JSDoc.
- Constants and labels in `constants.ts`, not scattered strings.

## 7. Configuration

- `.env` files: `VITE_API_BASE_URL` (and only `VITE_*` values the browser needs). `.env.local` is not committed.
- **No secrets in the UI.** Anything in the bundle is public.
- `package.json` scripts: `dev`, `build`, `test`, `lint`, `typecheck` (`tsc --noEmit`), `api:types`.

## 8. Performance

- Virtualize long lists (dropdown options, All Available).
- Let TanStack Query cache: data that doesn't change in a session (`attributes`, `children`) uses `staleTime: Infinity`.
- Lazy-load route pages (`React.lazy`) once there are several screens.
- `useMemo`/`useCallback` only for a measured problem or a heavy calculation, not by default.
- Debounce typing in search boxes (`useDebounce`, ~250 ms).

## 9. The Attribute Tagging screen (behaviour to build)

### 9.1 APIs (all under `/api/v1/attribute-tagging`)
| # | Endpoint | When |
|---|---|---|
| 1 | `GET /filters` | On open: every dropdown with all its values |
| 2 | `POST /aes/search?page&pageSize` | On open and on every filter change: body `{ screen, filters: { <key>: [text, …] } }` |
| 3 | `GET /aes/{aeId}` | Opening an AE |
| 4 | `POST` / `DELETE /aes/{aeId}/lock` | Edit / Done, Cancel, or switching AE |
| 5 | `PUT /aes/{aeId}/attributes` | Update button |
| 6 | `GET /attribute-types/{type}/attributes` and `…/{attributeId}/children` | All Available (edit mode); Required Dependency dialog |
| 7 | `GET` / `POST /saved-filters`, `DELETE /saved-filters/{id}` | Saved Filters dropdown, "Add to Saved Filters" |

APIs 1 and 2 exist in the backend; 3–7 are in the spec and come next.

### 9.2 Who is the user (temporary)
- Start on a **"Who are you?"** screen: a searchable dropdown of the `aeOwner` options from API 1. Store `{ id, name }` in `sessionStorage` + `CurrentUserContext`; the client sends `X-User-Id`.
- Header shows the name and "Switch user" (clears the user and `queryClient.clear()`). All of this stays in `src/auth/` so it can be replaced by SSO/JWT later.

### 9.3 Filter bar and list
- Dropdowns from API 1, in its order; each is `{ type, options }`. Labels per `type` in `constants.ts` (`est` → "EST", `legalEntity` → "Key Legal Entities").
- Typing in a dropdown only narrows its options **in the browser**; there is no search API.
- Filter state = `{ <type>: [text, …] }`, exactly the API's `filters`; starts as `{ aeOwner: [<my name>] }`; kept in the URL.
- Every change → API 2, page 1. Chips for selected values; "Clear All Filters" → `{}`.
- Cards: name, id, business area, last modified, "N Recommendations", description; paging from API 2.
- `FilterBar`, chips and saved filters are shared components that take the screen name, so the next screen reuses them.

### 9.4 AE detail and editing
- API 3: 11 attribute sections in the API's order, each with its count; **Tagged to AE** and **Suggested to Add**.
- `canEdit` false → read-only. Lock held by someone else → banner "Currently being modified by <name>. Edit capability is removed."
- **Edit** → API 4 `POST` (200 edit mode, 409 banner, 403 read-only); renew every 5 minutes while the user is active.
- Add/Remove happen in the browser only (added items green with a required comment; removed struck through, undo-able). Attributes with children open the **Required Dependency** dialog (API 6 children) until only leaf attributes are picked.
- **Update** → API 5 with the complete tagged list; blocked while a comment is missing. **Cancel** discards; **Done** → API 4 `DELETE`.
- Leaving with unsaved changes → confirm first, then release the lock.

### 9.5 Query keys and caching
- Keys from `queryKeys.ts` (4.1). `attributes`/`children`: `staleTime: Infinity`.
- After API 5: set `ae` from the response, invalidate `aes` and `filters`.

## 10. Testing: few and focused

**3 to 5 tests per feature**, enough to prove it behaves as the business expects.

| Kind | How many | What |
|---|---|---|
| Component/page test (Vitest + RTL + MSW) | 2–3 | The main user flow on screen (renders data, user action → expected result), and the most important rule (e.g. read-only when `canEdit` is false) |
| Hook or util test | 1–2 | Logic that's easy to get wrong (update payload, lock renewal timer, filter → URL) |
| Error state | 0–1 | One API error shown correctly, if the feature has special handling (409, 403) |

- Test what the user sees and does (`getByRole`, `userEvent.click`), not implementation details or CSS.
- Mock the network with MSW handlers in `src/test/handlers.ts`; never mock hooks or `fetch` directly.
- Test names describe behaviour: `shows read-only view when canEdit is false`.
- No snapshot tests; no tests for simple display components without logic.

```tsx
// src/features/attribute-tagging/components/AEList.test.tsx
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AttributeTaggingPage } from '../pages/AttributeTaggingPage';

test('shows the AEs returned by the search', async () => {
  server.use(
    http.post('*/aes/search', () =>
      HttpResponse.json({
        page: 1, pageSize: 20, totalRecords: 1, totalPages: 1,
        items: [{ aeId: 'AE-1', aeName: 'Payments', aeDescription: '', businessArea: 'Ops',
                  modifiedOn: '2026-11-01T00:00:00Z', recommendationCount: 2, isOwner: true, lockedBy: null }],
      }),
    ),
  );

  renderWithProviders(<AttributeTaggingPage />);

  expect(await screen.findByRole('heading', { name: 'Payments' })).toBeInTheDocument();
  expect(screen.getByText('2 Recommendations')).toBeInTheDocument();
});

test('shows an error with retry when the search fails', async () => {
  server.use(http.post('*/aes/search', () => HttpResponse.json({ detail: 'Boom' }, { status: 500 })));

  renderWithProviders(<AttributeTaggingPage />);

  expect(await screen.findByRole('button', { name: /retry/i })).toBeInTheDocument();
});
```

## 11. Copilot prompt

Use `.github/prompts/ui-feature.prompt.md`, or paste:
```
#file:docs/ui-plan.md #file:api/openapi.yml
I want: <behaviour in plain words: what the user sees, clicks, and what should happen, including errors>
First reply with your Understanding and Plan (files, components, hooks, query keys, 3–5 tests). Wait for my "go" before writing code.
```

## 12. Git and PR workflow

- Branch from `Develop`: `feature/<story-id>-<short-name>`.
- Commits: Conventional Commits (`feat(tagging): add AE search list`).
- PR: story link, screenshots or a short GIF of the screen, which spec operations it calls.
- CI: `typecheck`, `lint`, `test`, `build` all green.

## 13. Definition of Done (checklist before a PR)

- [ ] Calls only endpoints in `api/openapi.yml`; `generated.ts` regenerated, no hand-written API types
- [ ] Folder structure and import rules (3) followed; no `fetch` in components
- [ ] Server data through TanStack Query hooks with keys from `queryKeys.ts`; filters in the URL
- [ ] Loading, empty and error states on every async view
- [ ] Permissions only from API fields (`canEdit`, `isOwner`, `lock`)
- [ ] Long lists virtualized; inputs debounced
- [ ] Keyboard usable, `aria-label`s on icon buttons
- [ ] No `any`, no `@ts-ignore`, no `console.log`, no dead code; ESLint + Prettier clean
- [ ] Components under ~150 lines, logic in hooks/utils
- [ ] 3–5 tests: main flow, key rule, one error if special (10)
- [ ] No secrets in code or `.env` committed
