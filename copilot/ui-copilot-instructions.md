# Copilot instructions: myAuditPlan (mAP) UI

## What this app is
The **Attribute Tagging** screen of myAuditPlan:

1. The user lands on the screen and sees the **Auditable Entities (AEs) they own**: the AE Owner filter starts preselected with them.
2. They pick values in the dropdown filters or a saved filter. Every dropdown is a plain list: AE ID, AE Name, AE Owner, Business Area, Division, plus one per attribute type (EST, Risks, Key Legal Entities…).
3. They open an AE (it expands in the list) and see its **11 attribute type sections**: Est, Risks, Key Legal Entities, Key Regulations, Key Third Parties, Key Applications, Key Models, Archer Risk Units, General Ledger / Cost Centers, Locations, Products. Each shows its recommendation count; opened, it shows **Tagged to AE** and **Suggested to Add**, and in edit mode also **All Available**.
4. An owner clicks **Edit** (takes the edit lock), **adds** or **removes** attributes in the browser, and clicks **Update** to save. Everyone else sees the AE view-only.

## Tech stack
- React + TypeScript (strict), TanStack Query for server data
- Use the component library, router and styling already in `package.json`. Do not add a new UI library.

## The API spec is the contract
- Generate types with `openapi-typescript` from the spec (`api/openapi.yml`) into `src/api/generated.ts`. **Never hand-write API types** or edit the generated file.
- Call only the endpoints in the spec. If something is missing, flag that the spec needs changing.
- All calls go through one API client in `src/api/` that adds the `X-User-Id` header (later the bearer token) and parses ProblemDetail errors.

## The APIs (all under `/api/v1/attribute-tagging`)
| # | Endpoint | When |
|---|---|---|
| 1 | `GET /filters` | On open: every dropdown with all its values |
| 2 | `POST /aes/search?page&pageSize` | On open and on every filter change: `{ filters: { <type>: [text, …] } }` |
| 3 | `GET /aes/{aeId}` | Opening an AE |
| 4 | `POST /aes/{aeId}/lock` / `DELETE` | Edit / Done, Cancel, or switching AE |
| 5 | `PUT /aes/{aeId}/attributes` | Update button |
| 6 | `GET /attribute-types/{type}/attributes` and `…/{attributeId}/children` | All Available (when a section is opened in edit mode); Required Dependency dialog |
| 7 | `GET` / `POST /saved-filters`, `DELETE /saved-filters/{id}` | Saved Filters dropdown, "Add to Saved Filters" |

## Who is the user (temporary login)
- No JWT yet. The app starts on a **"Who are you?" screen**: a searchable dropdown built from the **`aeOwner` options of API 1**. The user picks their name; never use a free-typed name.
- Store `{ id, name }` in `sessionStorage` and a `CurrentUserContext`. The API client sends **`X-User-Id: <id>`** on every call. On 401 → clear it and go back to this screen.
- Show the name in the header with "Switch user" (clears the stored user and calls `queryClient.clear()`).
- Keep this in `src/auth/` so it can be swapped for JWT later.

## Filter bar and list
- Draw the dropdowns from API 1, in its order. Each is `{ type, options }` and `options` is a plain list of text: show it as is and send the same text back. The UI holds the label for each `type` (for example `est` → "EST", `legalEntity` → "Key Legal Entities") in one map.
- **Typing in a dropdown only narrows its options in the browser** (for example typing "2000" in AE ID shows 2000, 20001, 20002…). There is no search API.
- Every dropdown can have thousands of options (AE ID, AE Name, and 2,000+ per attribute type): always use a virtualized list.
- Filter state = `{ <type>: [text, …] }`, exactly the API's `filters`. Initial state: `{ aeOwner: [<my name>] }`. Keep it in the URL query string.
- Every change → API 2 with the new `filters`, page 1. Show chips for selected values; "Clear All Filters" → `{}` (all AEs).
- Cards: AE name, AE ID, business area, last modified, "N Recommendations", description. `isOwner` → Edit option; otherwise View.
- Paging from API 2 (`page`, `totalPages`).

## Reuse for new screens
- The filter bar, chips, saved filters and the `filters` state are the same on every screen: build them once as a shared `FilterBar` component that takes the screen name and calls `/<screen>/filters`, `/<screen>/aes/search` and `/<screen>/saved-filters`.
- A new screen only supplies its card and detail; it never copies the filter code.

## Saved filters
- API 7 `GET` fills the Saved Filters dropdown; choosing one loads its `filters` and runs API 2.
- "Add to Saved Filters" → `POST { name, filters }`. On 409 show the message (max 3) and offer to delete one.

## AE detail
- API 3. Show the 11 sections in the API's order, with `label` and `recommendationCount` ("N Recommendations"). Opening a section shows its `description`, **Tagged to AE** (chips) and **Suggested to Add** (with reason).
- If `lock` is set and it isn't the user's, show the banner **"Currently being modified by <name>. Edit capability is removed."**
- `canEdit` false → read-only (no Add, no ✕, no Update). Never decide editability yourself; use `isOwner`, `lock` and `canEdit` from the API.
- Tagged chips show the name; show `path` (parents) as a tooltip.
- Each list has its own search box that filters that list **in the browser** only.

## Editing
1. **Edit** → API 4 `POST`. 200 → edit mode. 409 → show "Currently being modified by <name>" and stay view-only. 403 → view-only.
2. **Keep the lock alive:** while in edit mode, call API 4 `POST` every **5 minutes** if the user has interacted in the last 15 minutes. The server expires the lock after 15 minutes without activity.
3. **All Available:** when a section is opened in edit mode, load API 6 for its type (cached for the session; it is the same for every AE). Expect 2,000+ attributes per type: always use a virtualized list. Hide attributes already tagged or suggested. Show the count ("All Available (1998)").
4. **Add** (from Suggested or All Available), in the browser only:
   - attribute with `hasChildren: false` → goes to Tagged, highlighted **green** ("New Attribute"), with a required **comment** ("why are you tagging this?");
   - attribute with `hasChildren: true` → open the **Required Dependency** dialog with API 6 `…/{attributeId}/children` (checkbox list, search, "Add All"). The user must pick at least one. Picked children that also have children open the dialog again, level by level, until every pick has no children. Only attributes without children are added to Tagged.
   - "Add All" adds every listed attribute the same way.
5. **Remove** (✕ on a tagged chip): marks it removed in the browser (struck through), undo-able.
6. **Update** → API 5 with the **complete** list of attributes that should be tagged across all 11 types: every kept, added and not-removed item as `{ attributeId, attributeType, comment }`. Block Update while any added item has no comment. On 200 replace the AE with the response (highlights cleared, recommendations recalculated by the backend) and stay in edit mode.
7. **Cancel** → discard unsaved changes. **Done** → API 4 `DELETE`, back to view mode.
8. **Opening another AE, leaving the screen or Switch user while in edit mode:** if there are unsaved changes, warn first ("You have unsaved changes. Leaving releases your lock and discards them."). On confirm → API 4 `DELETE`, then continue. On tab close, call it with `fetch(..., { method: 'DELETE', keepalive: true })`; the 15-minute expiry covers anything that slips through.
9. On 409 from API 5 (lock lost to another owner) → tell the user and reload the AE view-only.

## TanStack Query
- Keys: `['filters']`, `['aes', filters, page]`, `['ae', aeId]`, `['attributes', type]`, `['children', type, attributeId]`, `['savedFilters', userId]`.
- `['attributes', …]` and `['children', …]` don't change during a session: `staleTime: Infinity`.
- After API 5: set `['ae', aeId]` from the response, invalidate `['aes']` and `['filters']`.
- `queryClient.clear()` on Switch user.

## Errors
- ProblemDetail: show `detail`; map `errors[]` (for example `attributes[1].comment`) onto fields.
- 401 → "Who are you?" screen. 404 → "AE not found". 5xx → generic message with retry.

## Tests
- Filter bar: preselected AE Owner, building `filters`, Clear All, typing narrows options.
- Detail: 11 sections with counts; read-only when `canEdit` is false; the "being modified" banner.
- Editing: add needs a comment, Required Dependency dialog (multi-level, at least one child), remove + undo, Update sends the complete list, lock renewal timer, warning on switching AE with unsaved changes, 409 handling.

## Don't
- Don't add a free-text search or call endpoints not in the spec.
- Don't send the user id anywhere but the header.
- Don't save on Add/Remove; only Update calls the server.
- Don't add an attribute that has children to Tagged; only its children without children.
