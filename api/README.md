# myAuditPlan API - Attribute Tagging

OpenAPI 3.0 specification for the Attribute Tagging screen.

| File | What it is |
|---|---|
| [`openapi.yml`](openapi.yml) | Root: overview, auth, list of paths |
| [`attribute-tagging/`](attribute-tagging/) | The screen's endpoints and schemas |
| [`common/`](common/) | Shared schemas, parameters and error responses |
| [`dist/openapi.bundled.yml`](dist/openapi.bundled.yml) | **Generated** single file for Swagger UI and code generation. Don't edit by hand. |

## The APIs (under `/api/v1/attribute-tagging`)

| # | API | Used for |
|---|---|---|
| 1 | `GET /filters` | Every dropdown and all its values |
| 2 | `POST /aes/search` | The AE list for the selected filters, paged |
| 3 | `GET /aes/{aeId}` | One AE: for each of the 11 attribute types, what is tagged and what is recommended |
| 4 | `POST` / `DELETE /aes/{aeId}/lock` | Take (or renew) / release the edit lock |
| 5 | `PUT /aes/{aeId}/attributes` | Update: save the full list of tagged attributes |
| 6 | `GET /attribute-types/{type}/attributes` and `…/{attributeId}/children` | All Available, and the Required Dependency dialog |
| 7 | `GET` / `POST` / `DELETE /saved-filters` | Up to 3 saved filters per user |

## How the screen uses them

1. **Open:** API 1 (dropdowns), API 7 (saved filters), and API 2 with `{"filters":{"aeOwner":["<my id>"]}}` (my AEs).
2. **Filter:** the user picks dropdown values → API 2. Typing in a dropdown only narrows its options in the browser.
3. **Open an AE:** API 3. Each of the 11 sections shows Tagged and Suggested to Add, with its recommendation count.
4. **Edit:** API 4 `POST` takes the lock. If another owner has it → 409, and the screen shows "Currently being modified by …".
5. **All Available:** API 6, once per attribute type, when a section is opened in edit mode. It is the same for every AE, so it is cached.
6. **Add:** in the browser only (green). If the attribute has children, the Required Dependency dialog lists them (API 6 `/children`); the user picks at least one, level by level, until every pick has no children.
7. **Remove:** the ✕ on a tagged chip, in the browser only.
8. **Update:** API 5 with the complete list. Items not in the list are removed; new ones need a comment. The lock is kept; recommendations are recalculated.
9. **Done, Cancel or another AE:** API 4 `DELETE` releases the lock (the UI warns about unsaved changes first).

## Rules

- **Who:** the UI sends `X-User-Id` (temporary, until JWT). The "Who are you?" screen uses the AE Owner options from API 1.
- **View:** anyone can view any AE, including recommendations.
- **Edit:** only an owner holding the lock. The first owner to click Edit gets it.
- **Lock timeout:** 15 minutes without activity. API 4 `POST` and API 5 renew it; the UI renews every 5 minutes while the user is active in edit mode.
- **11 attribute types:** Est, Risks, Key Legal Entities, Key Regulations, Key Third Parties, Key Applications, Key Models, Archer Risk Units, General Ledger / Cost Centers, Locations, Products.
- **Attribute data:** each of the 11 types has its own table with 2,000+ values. Some types have parent and child attributes; only attributes without children can be tagged.
- **Filters:** every dropdown is a plain list. AE ID, AE Name, AE Owner (preselected), Business Area and Division come from the AE table; one dropdown per attribute type (11) comes from that type's table and finds AEs with the picked values tagged. OR within a dropdown, AND across dropdowns. `{}` = all AEs.
- **Saved filters:** at most 3 per user; a 4th returns 409.

## The filter pattern (reuse it for every new screen)

Filters are screen-based: a new screen keeps the same APIs and shapes and only defines its own dropdowns.

| Part | Same on every screen | Per screen |
|---|---|---|
| `GET /<screen>/filters` | Response: `[{ key, label, options: [{ id, name }] }]`, plain lists, cached | Which dropdowns, their labels and where their values come from |
| `POST /<screen>/aes/search` | Body `{ filters: { <key>: [ids] } }`; OR within a key, AND across keys; `{}` = everything; unknown key → 400; paged `{ page, pageSize, totalRecords, totalPages, items }` | What a card shows (`items`) |
| `/<screen>/saved-filters` | `GET` / `POST { name, filters }` / `DELETE`; max 3 per user | — |
| Option | `id` is sent back, `name` is shown; same text when there's no separate id | — |
| Typing in a dropdown | Narrows the options in the browser; no search API | — |

Shared shapes are in `common/schemas.yml` (`Filter`, `Option`, `Filters`, `PageMeta`). A screen adds its own `FilterKey` enum and a `Filter` that uses it, like `attribute-tagging/schemas.yml`.

**Adding a screen:** create `api/<screen>/paths.yml` and `schemas.yml`, copy the `filters`, `aesSearch` and `savedFilters` paths from Attribute Tagging, change the `FilterKey` list and the card, add the paths and a tag to `openapi.yml`, then lint and bundle.

## Working on the spec

```bash
npx @redocly/cli lint map                                         # check for errors
npx @redocly/cli bundle map -o api/dist/openapi.bundled.yml       # rebuild the single file
npx swagger-ui-watcher api/openapi.yml                            # Swagger UI, live
```

Commit the rebuilt bundle with your change. The **API spec** GitHub Action fails a PR if the spec has errors or the bundle is out of date.
