# Copilot instructions: myAuditPlan (mAP) backend API

## What this service is
myAuditPlan is an audit application made up of several screens (Attribute Tagging first, then Risk Assessment, Audit Plan and others). Almost every screen lists **Auditable Entities (AEs)**, lets the user search and filter them, opens one AE, and lets the AE's owners edit something on it. This service is the Spring Boot REST API behind those screens.

Typical size: about 1,000–2,500 AEs in total. A single user owns far fewer.

## Tech stack
- Java 17+, Spring Boot 3, Spring Web, Spring Data JPA, Spring Security
- springdoc-openapi for Swagger UI
- Use the libraries already in `pom.xml`. Do not add a new dependency without saying why.

## The API spec is the contract
- The OpenAPI spec (source in `api/openapi.yml` split across `api/common`, `api/shared` and `api/<screen>`; generate code from the bundled `api/dist/openapi.bundled.yml`) defines every endpoint, parameter and schema. **If code and spec disagree, the spec wins.**
- Never add, rename or change an endpoint or field in code alone. Change the spec first, in the same PR.
- Controller interfaces and DTOs are generated from the spec with openapi-generator (`interfaceOnly=true`). Implement the generated interfaces; do not hand-write duplicate DTOs.

## Package structure
```
com.map
├── common            security (UserContext, CurrentUser), search engine (Specification builder),
│                     paging, error handling, saved filters, lookups
├── ae                AuditableEntity, owners, AE attributes, shared /auditable-entities/{aeId}
├── attributetagging  screen module
├── riskassessment    screen module
└── <screen>          one package per screen
```
Shared logic goes in `common` or `ae`. A screen package contains only what is specific to that screen.

## Every screen module has the same shape
Paths are prefixed with the screen name, for example `/api/v1/attribute-tagging`:

| Endpoint | Purpose |
|---|---|
| `GET /aes?scope=MINE\|ALL&page&pageSize&sort` | Cards for the screen, paged |
| `POST /aes/search` | Same list with a `SearchRequest` body (`scope`, `searchText`, `criteria[]`) |
| `GET /filters?scope=MINE\|ALL` | Filter definitions and dropdown options |
| `GET /aes/{aeId}` | Screen-specific detail of one AE |
| `PUT/POST/PATCH /aes/{aeId}/...` | Edit actions (owners only) |

Attribute Tagging's edit endpoint is `PUT /attribute-tagging/aes/{aeId}/tags` (replaces Application, Model and Third Party tags; includes `version` for 409 on concurrent edits). Edit forms get their choices from `GET /lookups/{type}`.

Each screen implements a `ScreenDefinition` bean that declares its filters (from the shared `FilterField` catalog), its card mapping, default sort and any extra data it needs. The generic search service, controller logic, paging, scope and `canEdit` come from `common`. **Adding a screen must not mean copying the search code.**

## Who is the caller
- **Now (temporary, no JWT yet):** the UI has a "Who are you?" screen. The user picks themselves from `GET /users?q=` and the UI sends that id in the **`X-User-Id` header** on every request.
  - Implement this as `HeaderCurrentUser`, active only under the Spring profile **`dev-auth`**. It reads `X-User-Id`, loads the user, and returns 401 if the header is missing or the id is unknown.
  - `GET /users` is the only endpoint that works without `X-User-Id`.
  - This is **not secure** (anyone can send any id). Never enable `dev-auth` in production.
- **Later:** `JwtCurrentUser` reads the user from the token. Only the `CurrentUser` implementation changes; controllers, services and the API stay the same.
- All code gets the caller **only** through the `CurrentUser` interface → `UserContext(userId, username, admin)`. Never read the header or token anywhere else.

## Access rules (critical)
- **Never accept the owner, user id or role as a query parameter or body field**, and never trust one sent that way.
- Every user can **view** every AE.
- `scope=MINE` (default) returns AEs where the caller is an owner; `scope=ALL` returns every AE.
- Only an owner (or admin) can **edit**. Every AE returned carries `canEdit`, computed on the server:
  `canEdit = user.admin() || ae.ownerIds().contains(user.userId())`.
- Every edit endpoint re-checks ownership and returns **403** if the caller is not an owner. `canEdit` in a response is for the UI only and is never a security check.
- Return **404** only when the AE does not exist.
- AEs can have several owners. Owners live in an ownership table; match on the stable user id, not the display name.

## Search and filtering rules
- Build queries with JPA `Specification`s; do not concatenate SQL or JPQL strings.
- Semantics: values inside one criterion are **OR**; different criteria are **AND**. `operator: EXCLUDE` means the AE has none of the values.
- Multi-value attributes (legal entities, applications, cost centres and so on) live in an attribute table. Filter them with **`EXISTS` / `NOT EXISTS` subqueries**, never with joins, so rows don't duplicate and paging stays correct.
- Validate each criterion's `field` against the screen's `ScreenDefinition.filters()`. An unknown field returns 400.
- `searchText` is a case-insensitive contains match on the fields listed in the spec.
- Paging: `page` starts at **1** (`spring.data.web.pageable.one-indexed-parameters=true` or convert explicitly). `pageSize` defaults to 1000 and must be capped at **1000**. Always append `aeId` to the sort as a tiebreaker.
- Load a page in **two queries**: (1) the page of AEs, (2) their attributes, owners and counts with `WHERE ae_id IN (:ids)`, then group in Java. Never use `JOIN FETCH` on collections with paging, and never cause N+1 queries.
- An empty result is **200** with an empty list, not 404.

## Filter options (`/filters`)
- Options are `DISTINCT` values (with counts) from the AEs in the requested scope, read from the database, never from a partial page.
- Each option has `value` (a code or id sent back in criteria) and `label` (display text). For AE Name, `value` is the AE id because names repeat.
- Cache with `@Cacheable`: key `screen + ":ALL"` for `scope=ALL` (shared by all users) and `screen + ":MINE:" + userId` for `scope=MINE`. Evict the relevant entries when an edit changes AE data.
- Filters with more than about 500 options are returned as `TYPEAHEAD` with no options and served by a typeahead endpoint.

## Response conventions
- Page envelope: `{ page, pageSize, totalRecords, totalPages, lastPage, <items> }`.
- Errors use Spring `ProblemDetail` (`application/problem+json`) with `status`, `title`, `detail`, and `errors[]` (`field`, `message`) for validation. Use a single `@RestControllerAdvice`; never return stack traces.
- JSON fields in camelCase. Dates and times in ISO-8601 (UTC for timestamps). AE ids are **strings** (they have leading zeros).
- Arrays are never null; return `[]`.

## Persistence
- Schema changes go through the project's migration tool; never rely on `ddl-auto` outside local development.
- Index every column used for filtering or scope, for example `(attr_type, attr_value, ae_id)` and `(ae_id)` on the attribute table, and `(user_id, ae_id)` on the ownership table.

## Tests
For every screen and every change, cover at least:
- scope `MINE` vs `ALL`,
- `canEdit` true/false,
- an edit by a non-owner returns 403,
- OR within a filter, AND across filters, INCLUDE vs EXCLUDE,
- paging stability (no duplicate or missing rows across pages),
- unknown filter field returns 400.

## Adding a new screen (checklist)
1. Add the screen's paths and schemas to the spec, reusing the shared schemas, parameters and responses. Bundle and lint the spec.
2. Regenerate the interfaces.
3. Add any missing filters to the shared `FilterField` catalog.
4. Create `<screen>/<Screen>ScreenDefinition`, the controller implementing the generated interface, and the edit service with the ownership check.
5. Add tests from the list above.

## Don't
- Don't put the owner or user in request parameters or trust them from the client.
- Don't duplicate search, paging or scope logic inside a screen package.
- Don't use joins for multi-value filters or `JOIN FETCH` with paging.
- Don't return 404 for an empty list, or 403/404 for viewing an AE the user doesn't own (viewing is allowed).
- Don't change the API without updating the spec.
