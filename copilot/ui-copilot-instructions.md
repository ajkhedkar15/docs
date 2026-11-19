# Copilot instructions: myAuditPlan (mAP) UI

## What this app is
myAuditPlan is an audit application made up of several screens (Attribute Tagging first, then Risk Assessment, Audit Plan and others). Almost every screen follows the same pattern:

1. On opening, show the **Auditable Entities (AEs) assigned to the user**, plus a filter bar of dropdowns.
2. The user changes filters (or switches to **All AEs**) to find what they want.
3. The user clicks an AE to see its details, and can edit it **only if they are an owner**. Other AEs are view-only.

Typical size: about 1,000–2,500 AEs in total; one user owns far fewer. This is small enough to filter in the browser.

## Tech stack
- React + TypeScript (strict mode), TanStack Query for server data
- Use the component library, router and styling already in `package.json`. Do not add a new UI library.

## The API spec is the contract
- API types are generated with `openapi-typescript` from the bundled spec (`api/dist/openapi.bundled.yml`) into `src/api/generated.ts`. **Never hand-write API request or response types**, and never edit the generated file.
- Call endpoints only as the spec defines them. If something is missing, flag that the spec needs changing; don't invent an endpoint or field.
- All HTTP calls go through the shared API client in `src/api/`, which adds the user header (later the bearer token) and parses errors.

## Who is the user (temporary login)
- There is no JWT login yet. The app starts on a **"Who are you?" screen**: a typeahead that calls `GET /users?q=` (min 2 characters, debounced 250 ms). The user **picks** their name from the list; free-typed names are never used.
- Store the picked user `{ id, name }` in `sessionStorage` and in a `CurrentUserContext`. Then call `GET /me` to confirm it; on 401 go back to the "Who are you?" screen.
- The API client adds **`X-User-Id: <id>`** to every request. Never put the user id in a URL or request body.
- Show the user's name in the header with a "Switch user" action that clears the stored user and calls `queryClient.clear()`.
- Keep all of this inside `src/auth/` so it can be swapped for a JWT login later without touching screens.

## Folder structure
```
src/
├── api/                 generated types, API client, query key factory
├── auth/                "Who are you?" screen, CurrentUserContext (later: JWT login)
├── shared/
│   └── AeSearchPage/    filter bar, chips, saved filters, MINE/ALL switch, result list, local filtering
└── features/
    ├── attribute-tagging/   card, detail page, edit form for this screen
    ├── risk-assessment/
    └── <screen>/
```
A screen folder contains only what is specific to that screen. **Do not copy the search, filter or paging logic into a screen.**

## Building a screen
Each screen is the shared `AeSearchPage` plus its own card, detail page and edit form:
```tsx
<AeSearchPage
  screen="attribute-tagging"
  renderCard={(ae) => <TaggingCard ae={ae} />}
  onOpen={(ae) => navigate(`/attribute-tagging/${ae.aeId}`)}
/>
```
Each screen's endpoints are under `/api/v1/<screen>/...` and have the same shape: `POST /aes/search` (the only list endpoint), `GET /filters`, `GET /aes/{aeId}`, plus edit endpoints. List responses are always `{ page, pageSize, totalRecords, totalPages, lastPage, items }`. For Attribute Tagging the edit is `PUT /attribute-tagging/aes/{aeId}/tags`; the form's choices come from `GET /lookups/{key}` (for example `/lookups/keyApplication`), and it sends back `version` (409 means someone else changed it: reload and retry).

## Data loading (inside AeSearchPage)
1. On open, fetch in parallel: `GET /filters?scope=MINE`, `POST /aes/search?page=1&pageSize=1000` with body `{}`, and saved filters for the screen.
2. If a page returns `lastPage: false`, fetch the next pages in the background (`useInfiniteQuery`) until `lastPage: true`. Then the data set is **complete**.
3. **When complete, filter in the browser** with no API call. Until complete, send the current filters to `POST /aes/search`.
4. When the user switches to **All AEs**, do the same with `scope=ALL` (filters and list).
5. Show a skeleton while the first page loads and a clear empty state when nothing matches.

## Filter rules (must match the backend exactly)
- Dropdowns and their options come **only from `/filters`**. Never build options from the loaded rows, which may be incomplete.
- Show each option's `label`; send its `value`. For AE ID and AE Name, the value is the AE id because names repeat.
- Show labels for codes on cards by looking them up in the `/filters` options (keep a `key → value → label` map). Detail pages already get labels in `attributes`.
- Values inside one filter are **OR**; different filters are **AND**. `EXCLUDE` means the AE has none of the selected values.
- Filter only on `aeId`, `aeName`, `aeDescription` (search text) and the card's **`filterValues`** map. For `aeId` and `aeName` the selected values are AE ids, so compare with `ae.aeId`. Every other filter key is in `filterValues` as an array, and matches if **any** of its codes is selected. A key missing from `filterValues` means the AE has no value.
- `searchText` is a case-insensitive contains match, debounced by 250 ms.
- `/filters` returns every option for every filter (there is no typeahead). The search box inside a dropdown filters that dropdown's options locally.
- Put the local filtering in one pure function (`applyFilters(items, criteria, searchText)`) with unit tests, because its results must equal the server's.

## Filter state and UI
- Filter state = `{ scope, searchText, criteria[] }`, exactly the API `SearchRequest`. Saved filters store it as `search`.
- Keep it in the **URL query string** so views can be bookmarked and shared, and so the back button works.
- Show selected values as chips (`<Filter label>: <option label> ✕`). "Clear All Filters" resets everything except scope.
- "Add to Saved Filters" saves `{ screen, name, search }`. Choosing a saved filter loads `search` into state.

## Editing and permissions
- Each AE has `canEdit`, computed by the server. **Use only `canEdit`** to decide whether to show edit controls. Never work it out by comparing owners on the client.
- When `canEdit` is false, show the AE read-only (for example a lock icon and "View only"), with edit buttons hidden.
- Still handle a **403** from an edit call (ownership can change): show a message and refresh the AE.
- After a successful edit, invalidate the screen's list, filters and detail queries.

## TanStack Query
- Build keys with a factory in `src/api/queryKeys.ts`, always including `screen`, `scope` and the user id, for example `['aes', screen, scope, userId]`.
- Call `queryClient.clear()` on "Switch user" (later: logout) so the next user never sees cached data.
- Use `staleTime` of about 5 minutes for lists and filters.

## Errors
- Errors arrive as ProblemDetail (`status`, `title`, `detail`, `errors[]`). Show `detail` to the user; map `errors[]` onto form fields.
- 401 → clear the stored user and go back to the "Who are you?" screen (later: log in again). 404 on detail → "AE not found". 5xx → a generic message with a retry button.

## Performance and accessibility
- Wrap filtering in `useMemo`; debounce text input.
- Use pagination or list virtualization when more than about 200 cards are shown.
- Dropdowns, chips and cards must work with the keyboard and have accessible labels.

## Tests
- Unit tests for `applyFilters`: OR within a filter, AND across, INCLUDE vs EXCLUDE, multi-value `filterValues`, missing keys, aeId/aeName by id, search text.
- Component tests (React Testing Library): local filtering vs server search before the data is complete, chips and Clear All, read-only display when `canEdit` is false, and the 403 message.

## Adding a new screen (checklist)
1. Make sure the screen's endpoints are in the spec, then regenerate `src/api/generated.ts`.
2. Create `src/features/<screen>/` with its card, detail page and edit form.
3. Add the route and use `AeSearchPage` with `screen="<screen>"`.
4. Add tests from the list above.

## Don't
- Don't hand-write API types or call endpoints that aren't in the spec.
- Don't send the owner or user id to the API, or decide editability on the client.
- Don't build dropdown options from loaded rows.
- Don't duplicate `AeSearchPage` logic inside a screen.
- Don't keep filter state only in component state; it belongs in the URL.
