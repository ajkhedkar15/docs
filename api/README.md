# myAuditPlan API - Attribute Tagging

OpenAPI 3.0 specification for the Attribute Tagging screen.

The whole API is one file: [`openapi.yml`](openapi.yml). Open it directly in Swagger, the 42Crunch preview or a code generator; there is nothing to build.

## The APIs (under `/api/v1`)

Common APIs take the screen in the URL (`/screens/{screen}/…`, `{screen}` = `attribute-tagging`). Attribute Tagging's own APIs are under `/attribute-tagging/…`.

| # | API | Used for |
|---|---|---|
| 1 | `GET /screens/{screen}/filters` | Every dropdown and all its values |
| 2 | `POST /screens/{screen}/aes/search` | The AE list for the selected filters, paged |
| 3 | `GET /attribute-tagging/aes/{aeId}` | One AE: for each of the 11 attribute types, what is tagged and what is recommended |
| 4 | `POST` / `DELETE /attribute-tagging/aes/{aeId}/lock` | Take (or renew) / release the edit lock |
| 5 | `PUT /attribute-tagging/aes/{aeId}/attributes` | Update: save the full list of tagged attributes |
| 6 | `GET /attribute-tagging/attribute-types/{type}/attributes` and `…/{attributeId}/children` | All Available, and the Required Dependency dialog |
| 7 | `GET` / `POST` / `DELETE /screens/{screen}/saved-filters` | Up to 3 saved filters per user per screen |
| 8 | `GET /attribute-tagging/aes/{aeId}/history` | The AE's tagging history (audit trail) |
| 9 | `GET /users` | The "Who are you?" list: id and name of every AE owner |

## How the screen uses them

1. **Open:** API 1 (dropdowns), API 7 (saved filters), and API 2 with `{"filters":{"aeOwner":["<my name>"]}}` (my AEs).
2. **Filter:** the user picks dropdown values → API 2. Typing in a dropdown only narrows its options in the browser.
3. **Open an AE:** API 3. Each of the 11 sections shows Tagged and Suggested to Add, with its recommendation count.
4. **Edit:** API 4 `POST` takes the lock. If another owner has it → 409, and the screen shows "Currently being modified by …".
5. **All Available:** API 6, once per attribute type, when a section is opened in edit mode. It is the same for every AE, so it is cached.
6. **Add:** in the browser only (green). If the attribute has children, the Required Dependency dialog lists them (API 6 `/children`); the user picks at least one, level by level, until every pick has no children.
7. **Remove:** the ✕ on a tagged chip, in the browser only.
8. **Update:** API 5 with the complete list. Items not in the list are removed; new ones need a comment. The lock is kept; recommendations are recalculated.
9. **Done, Cancel or another AE:** API 4 `DELETE` releases the lock (the UI warns about unsaved changes first).

## Rules

- **Who:** the UI sends `X-User-Id` (temporary, until JWT). The "Who are you?" screen uses API 9 (`GET /users`).
- **View:** anyone can view any AE, including recommendations.
- **Edit:** only an owner holding the lock. The first owner to click Edit gets it.
- **Lock timeout:** 15 minutes without activity. API 4 `POST` and API 5 renew it; the UI renews every 5 minutes while the user is active in edit mode.
- **11 attribute types:** Est, Risks, Key Legal Entities, Key Regulations, Key Third Parties, Key Applications, Key Models, Archer Risk Units, General Ledger / Cost Centers, Locations, Products.
- **Attribute data:** each of the 11 types has its own table with 2,000+ values. Some types have parent and child attributes; only attributes without children can be tagged.
- **Filters:** every dropdown is a plain list. AE ID, AE Name, AE Owner (preselected), Business Area and Division come from the AE table; one dropdown per attribute type (11) comes from that type's table and finds AEs with the picked values tagged. OR within a dropdown, AND across dropdowns. `{}` = all AEs.
- **Saved filters:** at most 3 per user; a 4th returns 409.

## The filter pattern (reuse it for every new screen)

Filters, AE search and saved filters are common APIs: the UI puts the screen in the URL and the backend picks that screen's dropdowns. A new screen uses the same endpoints and only defines its own dropdowns.

| Part | Same on every screen | Per screen |
|---|---|---|
| `GET /screens/{screen}/filters` | Response: `[{ type, options: [text, …] }]`, plain lists (sorted, no duplicates), cached | Which dropdowns, and where their values come from |
| `POST /screens/{screen}/aes/search` | Body `{ filters: { <type>: [text, …] } }` (the same text as in options); OR within a type, AND across types; `{}` = everything; unknown type → 400; paged `{ page, pageSize, totalRecords, totalPages, items }` | What a card shows (`items`) |
| `/screens/{screen}/saved-filters` | `GET` / `POST { name, filters }` / `DELETE`; max 3 per user per screen | — |
| Labels | Not in the API: the UI holds the label for each `type` (for example `est` → "EST") | The screen's labels |
| Typing in a dropdown | Narrows the options in the browser; no search API | — |

Shared shapes are under `components/schemas` in `openapi.yml` (`Filter`, `Filters`, `PageMeta`, `Person`, `ErrorResponse`). A screen adds its own `<Screen>FilterType` enum and `<Screen>Filter` that uses it, like `AttributeTaggingFilterType` and `AttributeTaggingFilter`. Never reuse an existing schema name.

**Adding a screen:** in `openapi.yml`, add its key to the `Screen` enum (kebab-case), add its `<Screen>FilterType` and card schemas, and put any screen-only APIs under `/<screen>/…` with their own tag, then lint. The common `/screens/{screen}/…` paths are not copied.

## Working on the spec

```bash
npx @redocly/cli lint map                    # check for errors
npx swagger-ui-watcher api/openapi.yml       # Swagger UI, live
```

The **API spec** GitHub Action lints `api/openapi.yml` on every PR and fails it if there are errors.
