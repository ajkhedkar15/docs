# myAuditPlan API

OpenAPI 3.0 specification for the myAuditPlan (mAP) backend.

| File | What it is |
|---|---|
| [`openapi.yml`](openapi.yml) | Root: info, auth, the list of paths |
| [`common/`](common/) | Shared schemas, parameters and error responses |
| [`shared/paths.yml`](shared/paths.yml) | Endpoints every screen uses: users, current user, core AE, saved filters, lookups |
| [`attribute-tagging/`](attribute-tagging/) | Attribute Tagging screen: paths and schemas |
| [`dist/openapi.bundled.yml`](dist/openapi.bundled.yml) | **Generated** single file for Swagger UI and code generation. Don't edit it by hand. |

## Working on the spec

```bash
npx @redocly/cli lint map                                         # check for errors
npx @redocly/cli bundle map -o api/dist/openapi.bundled.yml       # rebuild the single file
npx @redocly/cli preview                                          # browse it locally
```

Commit the rebuilt bundle with your change. The **API spec** GitHub Action fails a PR if the spec has errors or the bundle is out of date.

## Every screen has the same endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/<screen>/aes?scope=MINE\|ALL` | Cards, paged (page from 1, pageSize up to 1000) |
| POST | `/<screen>/aes/search` | Search with `criteria[]` (INCLUDE / EXCLUDE) |
| GET | `/<screen>/filters?scope=MINE\|ALL` | Filter dropdowns and options |
| GET | `/<screen>/aes/{aeId}` | Detail of one AE for that screen |
| PUT/POST | `/<screen>/aes/{aeId}/...` | Edits (owners only, otherwise 403) |

Shared: `GET /users?q=`, `GET /me`, `GET /auditable-entities/{aeId}`, `/saved-filters`, `GET /lookups/{type}`.

## Who is the caller

- **Now (temporary):** a "Who are you?" screen. The user picks themselves from `GET /users?q=`; the UI sends that id in the `X-User-Id` header on every call. Backend accepts it only with the `dev-auth` profile. Not secure - for development and demos only.
- **Later:** JWT bearer token. Only the backend's `CurrentUser` class changes.
- The user is never a query parameter or body field.

## View vs edit

- Every user can **view** every AE. Screens open on `scope=MINE`; `scope=ALL` shows every AE.
- Only an owner can **edit**. Each AE has `canEdit`, computed by the server; the UI shows the rest read-only.
- Edit endpoints re-check ownership and return **403** for non-owners.

## Adding a screen

1. Create `api/<screen>/paths.yml` and `api/<screen>/schemas.yml` (copy the Attribute Tagging ones as a starting point).
2. Add the screen's paths to `openapi.yml`, its tag, and its id to `ScreenId` in `common/schemas.yml`.
3. Add any new filters to `FilterKey` in `common/schemas.yml`.
4. Lint, bundle, open a PR.
