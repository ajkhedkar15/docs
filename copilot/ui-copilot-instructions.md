# Copilot instructions: myAuditPlan (mAP) UI

## What this app is
The **Attribute Tagging** screen of myAuditPlan:

1. The user lands on the screen and sees the **Auditable Entities (AEs) they own**: the AE Owner filter starts preselected with them.
2. They pick values in the dropdown filters to find the AEs they want.
3. They open an AE and see its **11 attribute types** (EST / Business Function, Direct Legal Entity, Relevant Legal Entity, Products, Risk Units, Risks, Standards (Regulations), Assets (Applications), Third Parties, Models, Location), each with three lists: **tagged**, **recommended** and **available**. Show them in the order the API returns them, with the API's `label`.
4. An owner clicks **Edit** (takes the edit lock), **adds** or **removes** attributes in the browser, and clicks **Update** to save. Everyone else sees the AE view-only.

## Tech stack
- React + TypeScript (strict), TanStack Query for server data
- Use the component library, router and styling already in `package.json`. Do not add a new UI library.

## The API spec is the contract
- Generate types with `openapi-typescript` from the bundled spec (`api/dist/openapi.bundled.yml`) into `src/api/generated.ts`. **Never hand-write API types** or edit the generated file.
- Call only the endpoints in the spec. If something is missing, flag that the spec needs changing.
- All calls go through one API client in `src/api/` that adds the `X-User-Id` header (later the bearer token) and parses ProblemDetail errors.

## The APIs (all under `/api/v1/attribute-tagging`)
| # | Endpoint | When |
|---|---|---|
| 1 | `GET /filters` | On open: every dropdown with all its values |
| 2 | `POST /aes/search?page&pageSize` | On open and on every filter change: `{ filters: { <key>: [values] } }` |
| 3 | `GET /aes/{aeId}` | Opening an AE |
| 4 | `POST /aes/{aeId}/lock` / `DELETE` | Edit / Done, Cancel, or switching AE |
| 5 | `PUT /aes/{aeId}/attributes` | Update button |
| 6 | `GET` / `POST /saved-filters`, `DELETE /saved-filters/{id}` | Saved Filters dropdown, "Add to Saved Filters" |

## Who is the user (temporary login)
- No JWT yet. The app starts on a **"Who are you?" screen**: a searchable dropdown built from the **`aeOwner` options of API 1**. The user picks their name; never use a free-typed name.
- Store `{ id, name }` in `sessionStorage` and a `CurrentUserContext`. The API client sends **`X-User-Id: <id>`** on every call. On 401 → clear it and go back to this screen.
- Show the name in the header with "Switch user" (clears the stored user and calls `queryClient.clear()`).
- Keep this in `src/auth/` so it can be swapped for JWT later.

## Filter bar and list
- Draw the dropdowns from API 1, in its order. Show each option's `label`; send its `value` (AE ids for AE ID / AE Name, user ids for AE Owner).
- **Typing in a dropdown only narrows its options in the browser** (for example typing "2000" in AE ID shows 2000, 20001, 20002…). There is no search API and no free-text search box.
- Dropdowns with thousands of options (AE ID, AE Name) must use a virtualized list.
- Filter state = `{ <key>: [values] }`, exactly the API's `filters`. Initial state: `{ aeOwner: [<my id>] }`. Keep it in the URL query string.
- Every change → API 2 with the new `filters`, page 1. Show chips for selected values; "Clear All Filters" → `{}` (all AEs).
- Cards: AE name, AE ID, business area, last modified, "N Recommendations", description. `isOwner` → Edit option; otherwise View. If `lockedBy` is set, show "Being edited by <name>".
- Paging from API 2 (`page`, `totalPages`).

## Saved filters
- API 6 `GET` fills the Saved Filters dropdown; choosing one loads its `filters` and runs API 2.
- "Add to Saved Filters" → `POST { name, filters }`. On 409 show the message (max 3) and offer to delete one.

## AE detail
- API 3. For each of the 11 attribute types show three sections: **Tagged**, **Recommended** (with reason), **Available**. Everyone sees all three, including recommendations; only the owner in edit mode gets Add and Remove buttons.
- `canEdit` false → everything read-only. If `isOwner` and `lock` is someone else's → "Being edited by <name>", Edit disabled. If `isOwner` and no lock → Edit enabled.
- Never decide editability yourself from owners; use `isOwner`, `lock` and `canEdit` from the API.

## Editing
1. **Edit** → API 4 `POST`. 200 → edit mode. 409 → show "Being edited by <name>" and stay view-only. 403 → view-only.
2. **Keep the lock alive:** while in edit mode, call API 4 `POST` every **5 minutes** if the user has interacted in the last 15 minutes. The server expires the lock after 15 minutes without activity.
3. **Add** (from Recommended or Available): moves the attribute into Tagged **in the browser only**, highlighted **green**, with a required **comment** field ("why are you tagging this?").
4. **Remove** (from Tagged): marks it removed in the browser (struck through, red), undo-able.
5. **Update** → API 5 with the **complete** list of attributes that should be tagged across all 11 types: every kept, added and not-removed item as `{ attributeId, attributeType, comment }`. Block Update while any added item has no comment. On 200 replace the AE with the response (highlights cleared) and stay in edit mode. The response has **recomputed recommendations**: the backend re-runs its rules after each save, so the Recommended and Available lists can change.
6. **Done / Cancel** → API 4 `DELETE`, back to view mode. Cancel discards unsaved changes.
7. **Opening another AE, leaving the screen or Switch user while in edit mode:** if there are unsaved changes, show a warning ("You have unsaved changes. Leaving releases your lock and discards them."). On confirm → API 4 `DELETE`, then continue. On tab close, call it with `fetch(..., { method: 'DELETE', keepalive: true })`; the 15-minute expiry covers anything that slips through.
8. On 409 from API 5 (lock lost to another owner) → tell the user, reload the AE view-only; their unsaved changes can't be saved.

## TanStack Query
- Keys: `['filters']`, `['aes', filters, page]`, `['ae', aeId]`, `['savedFilters', userId]`.
- After API 5: set `['ae', aeId]` from the response, invalidate `['aes']` and `['filters']`.
- `queryClient.clear()` on Switch user.

## Errors
- ProblemDetail: show `detail`; map `errors[]` (for example `attributes[1].comment`) onto fields.
- 401 → "Who are you?" screen. 404 → "AE not found". 5xx → generic message with retry.

## Tests
- Filter bar: preselected AE Owner, OR/AND building of `filters`, Clear All, typing narrows options.
- Detail: three lists per type; read-only when `canEdit` is false; "being edited by" state.
- Editing: add needs a comment, remove + undo, Update sends the complete list, lock renewal timer, warning on switching AE with unsaved changes, 409 handling.

## Don't
- Don't add a free-text search or call endpoints not in the spec.
- Don't send the user id anywhere but the header.
- Don't save on Add/Remove; only Update calls the server.
