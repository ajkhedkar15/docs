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
| 3 | `GET /aes/{aeId}` | One AE with 11 attribute-type sections: `tagged`, `recommended`, `recommendationCount`, `description`. Plus `isOwner`, `lock`, `canEdit`. |
| 4 | `POST /aes/{aeId}/lock` / `DELETE` | Take or renew / release the edit lock. |
| 5 | `PUT /aes/{aeId}/attributes` | Save the complete list of tagged attributes. |
| 6 | `GET /attribute-types/{type}/attributes`, `GET /attribute-types/{type}/attributes/{attributeId}/children` | All Available (top level of a type) and one attribute's children. Same for every AE → cache. |
| 7 | `GET` / `POST /saved-filters`, `DELETE /saved-filters/{id}` | Up to 3 saved filters per user. |

There is **no free-text search**. Typing in a dropdown or a list's search box only narrows what the browser already has.

## The 11 attribute types
In screen order: `est` (Est), `risk` (Risks), `legalEntity` (Key Legal Entities), `regulation` (Key Regulations), `thirdParty` (Key Third Parties), `application` (Key Applications), `model` (Key Models), `archerRiskUnit` (Archer Risk Units), `costCenter` (General Ledger / Cost Centers), `location` (Locations), `product` (Products).

**Data:** each type has **its own table**, with 2,000+ attributes each. Map the API's `type` to its table in one place (for example an enum → repository).

**Hierarchies:** some types have parent and child attributes, possibly several levels deep (`parent_id` on the attribute). Others are flat.
- `hasChildren` = the attribute has at least one child.
- **Only attributes without children can be tagged.** API 5 rejects any other with 400.
- Tagged attributes carry `path`: the names of their parents, top level first.

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
- Filters: `aeId`, `aeName`, `aeOwner` (preselected by the UI), `businessArea`, `businessDivision`. **All from the AE table**, each a plain list of distinct values. Unknown key → 400.
- `filters` is a map of filter key → selected values. **OR within a key, AND across keys.**
- `aeId` and `aeName` values are AE ids; `aeOwner` values are user ids.
- Every filter → `IN (...)` on the AE table's column. If an AE's owners are stored as several rows (an AE can have several owners), filter `aeOwner` with an `EXISTS` subquery, never a join (joins duplicate rows and break paging).
- Build queries with JPA `Specification`s; never concatenate SQL/JPQL.
- Load a page in **two queries**: the page of AEs, then what the cards need for those ids (`WHERE ae_id IN (:ids)`): recommendation counts, owners for `isOwner`, current locks. No N+1, no `JOIN FETCH` with paging.
- An empty result is **200** with `items: []`.
- `/filters`: `SELECT DISTINCT` per column of the AE table, `@Cacheable`. Evict when AE data changes.

## AE detail (API 3)
- 11 sections in screen order, each with `label`, `description`, `recommendationCount`, `tagged` and `recommended`.
- `tagged` = the AE's `ACTIVE` attributes of that type (with comment, path, taggedBy, taggedOn).
- `recommended` = from the recommendation rules, minus what is tagged (with `reason`). A recommended attribute may have children. Shown to everyone, owners and viewers alike.
- **Not** All Available: that is API 6, the same for every AE.
- `isOwner` = caller is one of the AE's owners. `canEdit` = `isOwner` **and** caller holds a non-expired lock.

## All Available and children (API 6)
- `GET /attribute-types/{type}/attributes` → the type's top-level attributes (`parent_id IS NULL`); for flat types that is all of them. Sorted by name, `@Cacheable` per type.
- `GET /attribute-types/{type}/attributes/{attributeId}/children` → direct children, sorted by name, cacheable. 404 if the attribute doesn't exist or isn't of that type.
- Set `hasChildren` with one grouped query, not one per attribute.

## Edit lock (API 4)
- Store one lock row per AE: `ae_id` (unique), `locked_by`, `locked_on`, `expires_at`.
- **Expiry: 15 minutes after the last activity.** Activity = taking/renewing the lock (POST) or saving (API 5); each sets `expires_at = now + 15 min`.
- POST rules: not an owner → 403. No lock or expired → caller gets it. Caller holds it → renew. Another owner holds a live lock → **409** with their name.
- Take/renew must be **atomic** (one conditional `INSERT … ON CONFLICT` / `UPDATE … WHERE expires_at < now OR locked_by = :me`, or a unique constraint + retry) so two owners clicking at once can't both win.
- DELETE releases only the caller's own lock; otherwise it does nothing and returns 204.

## Update (API 5)
- Body = the **complete** list of attributes that should be tagged after the save, each `{ attributeId, attributeType, comment }`.
- Caller must be an owner (else 403) and hold the lock; if their lock expired and nobody else took it, renew it and continue; if another owner holds it → 409.
- Validate every item: the attribute exists, belongs to `attributeType`, and **has no children** → 400 otherwise. Name, description and parent come from the attribute table, never from the request.
- In one transaction, compare with the current `ACTIVE` rows:
  - new → insert `ACTIVE` with comment (comment **required** → 400 if blank), `tagged_by`, `tagged_on`;
  - missing from the list → set `INACTIVE` (keep the row for history);
  - present in both → keep; save a changed comment.
- **Re-run the recommendation rules for this AE** after saving (new tags can change what is recommended) and store the new recommendations and counts.
- Update the AE's `modified_on` / `modified_by`, renew the lock (it stays held), evict the `/filters` cache, return the AE as API 3.

## Saved filters (API 7)
- Per user, **at most 3**. A 4th → **409** "You can save up to 3 filters". Duplicate name for the same user → 409.
- Store `filters` as JSON. Users only see and delete their own (someone else's id → 404).

## Conventions
- Paging: `page` starts at 1; response `{ page, pageSize, totalRecords, totalPages, items }`.
- Errors: Spring `ProblemDetail` (`application/problem+json`) from one `@RestControllerAdvice`; never stack traces.
- JSON camelCase; ISO-8601 UTC timestamps; AE ids are strings (leading zeros); arrays never null.
- Turn on response compression (`server.compression.enabled=true`).

## Tests
- Filters: OR within a key, AND across keys, `aeOwner`, unknown key → 400, empty result → 200.
- Detail: tagged and recommended don't overlap; counts; `canEdit` only for the owner holding the lock.
- All Available: top level only; children endpoint; `hasChildren` correct; flat types.
- Lock: owner gets it; second owner → 409; non-owner → 403; expiry after 15 min; renew; concurrent take → only one wins; DELETE by non-holder is a no-op.
- Update: add (comment required), remove (→ INACTIVE), keep, wrong type → 400, attribute with children → 400, without lock → 409, non-owner → 403, recommendations recomputed.
- Saved filters: 4th → 409; can't delete another user's.

## Don't
- Don't add endpoints or fields that aren't in the spec.
- Don't take the user from a parameter or body, or trust `canEdit` from the client.
- Don't use joins for multi-value filters or `JOIN FETCH` with paging.
- Don't hard-delete tagged attributes; set them `INACTIVE`.
- Don't send All Available inside API 3.
