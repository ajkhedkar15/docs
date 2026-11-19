# myAuditPlan API - Attribute Tagging

OpenAPI 3.0 specification for the Attribute Tagging screen.

| File | What it is |
|---|---|
| [`openapi.yml`](openapi.yml) | Root: overview, auth, list of paths |
| [`attribute-tagging/`](attribute-tagging/) | The screen's endpoints and schemas |
| [`common/`](common/) | Shared schemas, parameters and error responses |
| [`dist/openapi.bundled.yml`](dist/openapi.bundled.yml) | **Generated** single file for Swagger UI and code generation. Don't edit by hand. |

## The APIs

| # | API | Used for |
|---|---|---|
| 1 | `GET /attribute-tagging/filters` | Every dropdown and all its values |
| 2 | `POST /attribute-tagging/aes/search` | The AE list for the selected filters, paged |
| 3 | `GET /attribute-tagging/aes/{aeId}` | One AE: tagged, recommended and available attributes for each of the 11 types |
| 4 | `POST` / `DELETE /attribute-tagging/aes/{aeId}/lock` | Take (or renew) / release the edit lock |
| 5 | `PUT /attribute-tagging/aes/{aeId}/attributes` | Update: save the full list of tagged attributes |
| 6 | `GET` / `POST` / `DELETE /attribute-tagging/saved-filters` | Up to 3 saved filters per user |

## How the screen uses them

1. **Open:** API 1 (dropdowns), API 6 (saved filters), and API 2 with `{"filters":{"aeOwner":["<my id>"]}}` (my AEs).
2. **Filter:** the user picks dropdown values → API 2 with the selected filters. Typing in a dropdown only narrows its options in the browser; there is no search API.
3. **Open an AE:** API 3. Owners see Edit; everyone else sees View.
4. **Edit:** API 4 `POST` takes the lock. If another owner has it → 409 "being edited by …".
5. **Add / Remove:** in the browser only (added rows turn green).
6. **Update:** API 5 with the complete list. Items not in the list are removed; new ones need a comment. The lock is kept.
7. **Done, Cancel or another AE:** API 4 `DELETE` releases the lock (the UI warns about unsaved changes first).

## Rules

- **Who:** the UI sends `X-User-Id` (temporary, until JWT). The "Who are you?" screen uses the AE Owner options from API 1.
- **View:** anyone can view any AE.
- **Edit:** only an owner holding the lock. The first owner to click Edit gets it.
- **Lock timeout:** 15 minutes without activity. API 4 `POST` and API 5 renew it; the UI renews every 5 minutes while the user is active in edit mode.
- **Filters:** OR within a dropdown, AND across dropdowns. `{}` = all AEs.
- **Saved filters:** at most 3 per user; a 4th returns 409.
- **Recommendations:** everyone sees them (view only for non-owners). After every Update the backend re-runs the recommendation rules, and API 5 returns the fresh lists.

## Working on the spec

```bash
npx @redocly/cli lint map                                         # check for errors
npx @redocly/cli bundle map -o api/dist/openapi.bundled.yml       # rebuild the single file
npx swagger-ui-watcher api/openapi.yml                            # Swagger UI, live
```

Commit the rebuilt bundle with your change. The **API spec** GitHub Action fails a PR if the spec has errors or the bundle is out of date.
