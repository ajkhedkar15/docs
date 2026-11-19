# Copilot instructions: myAuditPlan (mAP) backend API

## What this service is
The Spring Boot REST API behind the **Attribute Tagging** screen of myAuditPlan. Users find Auditable Entities (AEs) with dropdown filters, open one, and (if they own it and hold its edit lock) tag or untag attributes of 11 attribute types. About 1,000–2,500 AEs in total.

## Tech stack
- Java 17+, Spring Boot 3, Spring Web, Spring Data JPA, Spring Security
- Use the libraries already in `pom.xml`. Do not add a dependency without saying why.

## The API spec is the contract
- Source: `api/openapi.yml` (split into `api/common` and `api/attribute-tagging`). Generate from the bundled `api/dist/openapi.bundled.yml` with openapi-generator (`interfaceOnly=true`) and implement the generated interfaces.
- **If code and spec disagree, the spec wins.** Never add, rename or change an endpoint or field in code alone; change the spec first, in the same PR.

## The APIs (all under `/api/v1/attribute-tagging`)
| # | Endpoint | Does |
|---|---|---|
| 1 | `GET /filters` | Every filter in screen order with every value across all AEs. Same for every user → cache once. |
| 2 | `POST /aes/search?page&pageSize` | Body `{ filters: { <key>: [values] } }`. Server-side filtering, sorted by AE name, paged (default 20, max 100). `{}` = all AEs. |
| 3 | `GET /aes/{aeId}` | One AE with 11 attribute-type groups: `tagged`, `recommended`, `available`. Plus `isOwner`, `lock`, `canEdit`. |
| 4 | `POST /aes/{aeId}/lock` / `DELETE` | Take or renew / release the edit lock. |
| 5 | `PUT /aes/{aeId}/attributes` | Save the complete list of tagged attributes. |
| 6 | `GET` / `POST /saved-filters`, `DELETE /saved-filters/{id}` | Up to 3 saved filters per user. |

There is **no free-text search**. Typing in a dropdown narrows its options in the browser only.

## Package structure
```
com.map
├── common            CurrentUser, error handling (ProblemDetail), paging helpers
└── attributetagging  controllers, services, repositories, entities for the APIs above
```

## Who is the caller
- **Now (temporary, no JWT):** the UI sends the user id in the **`X-User-Id`** header. Implement `HeaderCurrentUser`, active only under the Spring profile **`dev-auth`**: read the header, load the user, return 401 if missing or unknown. Not secure; never enable in production.
- **Later:** `JwtCurrentUser` reads the token. Only the `CurrentUser` implementation changes.
- Get the caller **only** through the `CurrentUser` interface. Never accept the user as a query parameter or body field.

## Filtering (API 1 and 2)
- `filters` is a map of filter key → selected values. **OR within a key, AND across keys.** Unknown key → 400.
- `aeId` and `aeName` values are AE ids; `aeOwner` values are user ids.
- AE-table fields (`aeId`, `aeName`, `est`, `businessArea`, `businessDivision`, `auditTeam`) → `IN (...)` on the column.
- Multi-value fields (owners and the attribute types) → **`EXISTS` subqueries** on the ownership / attribute tables, never joins (joins duplicate rows and break paging). Only `ACTIVE` attribute rows count.
- Build queries with JPA `Specification`s; never concatenate SQL/JPQL.
- Load a page in **two queries**: the page of AEs, then whatever the cards need for those ids (`WHERE ae_id IN (:ids)`): recommendation counts, owners for `isOwner`, current locks. No N+1, no `JOIN FETCH` with paging.
- An empty result is **200** with `items: []`.
- `/filters`: `SELECT DISTINCT` per filter across all AEs, `@Cacheable`, evicted when API 5 changes attributes.

## AE detail (API 3)
- 11 groups in screen order. Each attribute appears in **exactly one** list:
  - `tagged` = the AE's `ACTIVE` attributes (with comment, taggedBy, taggedOn),
  - `recommended` = from the recommendation rules, minus tagged (with `reason`),
  - `available` = every attribute of that type that is neither tagged nor recommended.
- `isOwner` = caller is one of the AE's owners. `canEdit` = `isOwner` **and** caller holds a non-expired lock.

## Edit lock (API 4)
- Store one lock row per AE: `ae_id` (unique), `locked_by`, `locked_on`, `expires_at`.
- **Expiry: 15 minutes after the last activity.** Activity = taking/renewing the lock (POST) or saving (API 5); each sets `expires_at = now + 15 min`.
- POST rules: not an owner → 403. No lock or expired → caller gets it. Caller holds it → renew. Another owner holds a live lock → **409** with their name.
- Take/renew must be **atomic** (one conditional `INSERT … ON CONFLICT` / `UPDATE … WHERE expires_at < now OR locked_by = :me`, or a unique constraint + retry) so two owners clicking at once can't both win.
- DELETE releases only the caller's own lock; otherwise it does nothing and returns 204.

## Update (API 5)
- Body = the **complete** list of attributes that should be tagged after the save, each `{ attributeId, attributeType, comment }`.
- Caller must be an owner (else 403) and hold the lock; if their lock expired and nobody else took it, renew it and continue; if another owner holds it → 409.
- In one transaction, compare with the current `ACTIVE` rows:
  - new → insert `ACTIVE` with comment (comment **required** → 400 if blank), `tagged_by`, `tagged_on`;
  - missing from the list → set `INACTIVE` (keep the row for history);
  - present in both → keep; save a changed comment.
- Validate every `attributeId` exists and belongs to the given `attributeType` → 400 otherwise. Name and description come from the attribute table, never from the request.
- Update the AE's `modified_on` / `modified_by`, renew the lock (it stays held), evict the `/filters` cache, return the AE as API 3.

## Saved filters (API 6)
- Per user, **at most 3**. A 4th → **409** "You can save up to 3 filters". Duplicate name for the same user → 409.
- Store `filters` as JSON. Users only see and delete their own (someone else's id → 404).

## Conventions
- Paging: `page` starts at 1; response `{ page, pageSize, totalRecords, totalPages, items }`.
- Errors: Spring `ProblemDetail` (`application/problem+json`) from one `@RestControllerAdvice`; never stack traces.
- JSON camelCase; ISO-8601 UTC timestamps; AE ids are strings (leading zeros); arrays never null.
- Turn on response compression (`server.compression.enabled=true`).

## Tests
- Filters: OR within a key, AND across keys, `aeOwner`, unknown key → 400, empty result → 200.
- Detail: each attribute in exactly one list; `canEdit` only for the owner holding the lock.
- Lock: owner gets it; second owner → 409; non-owner → 403; expiry after 15 min; renew; concurrent take → only one wins; DELETE by non-holder is a no-op.
- Update: add (comment required), remove (→ INACTIVE), keep, wrong type → 400, without lock → 409, non-owner → 403.
- Saved filters: 4th → 409; can't delete another user's.

## Don't
- Don't add endpoints or fields that aren't in the spec.
- Don't take the user from a parameter or body, or trust `canEdit` from the client.
- Don't use joins for multi-value filters or `JOIN FETCH` with paging.
- Don't hard-delete tagged attributes; set them `INACTIVE`.
