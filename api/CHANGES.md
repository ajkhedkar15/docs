# mAP API spec: 7.0.0 → 7.1.0

All changes are additive. Nothing that exists in 7.0.0 is renamed or removed, so APIs 1 and 2 (built) and the UI types stay valid. Lints clean with the bundle's `redocly.yaml` (0 errors, 0 warnings).

## New operations (11 → 13)

| # | operationId | Endpoint | Why |
|---|---|---|---|
| 8 | `getAEHistory` | `GET /attribute-tagging/aes/{aeId}/history?attributeType&page&pageSize` | Audit trail: who added, removed, re-commented or rejected what, when, why, and from which source (Functional Guide 4.2, 4.3, 5.1). Also what the outbound feeds need. |
| 9 | `getUsers` | `GET /users` (tag `Users`, no auth) | The "Who are you?" screen needs `{ id, name }` to send `X-User-Id`; API 1 `aeOwner` options only have names. Goes away with JWT. |

## Changed operations

- **API 5 `updateAEAttributes`**: optional `rejected: [{ attributeId, attributeType, reason }]` to reject recommendations in the same save (Accept / Reject / Change from the guide). Rejected items leave Suggested to Add and are written to the history. The new 400 rules are: attribute both tagged and rejected, rejection without a reason, and adding an attribute that is expired in its source. Added a full 200 example and a specific 400 example.
- **API 3 `getAE`**: describes rejected and expired behaviour; example shows the new fields.
- **API 4a `lockAE`**: 200 example added.
- **API 6b `getAttributeChildren`**: 400 (unknown type) added; specific 404 example; uses the new shared `AttributeId` parameter.
- **API 7**: examples for GET and POST; POST says names are unique per user; DELETE says another user's id returns 404, and adds 400 for a non-numeric id.

## Schema changes

- `TaggedAttribute`: `source` (`TagSource`: MANUAL, RECOMMENDATION, MIGRATED) and `expired` (boolean), both required.
- `RecommendedAttribute`: `source` (`RecommendationSource`: RULE, AI, required) and `confidence` (0–1, null for RULE).
- New: `TagSource`, `RecommendationSource`, `RejectedRecommendation`, `HistoryAction`, `AttributeHistoryEntry`, `AttributeHistoryPagedResponse`; parameter `AttributeId`.

## Deliberately not included

- AE management (add, expire, merge, split) and risk rating / rationale. They wait for the myAP vs M7 decision (PROJECT_CONTEXT section 8).
- Inbound/outbound integrations: batch jobs, not REST.

## Matched to the running backend (photos of the controller and Swagger UI)

- `info.title` is now "myAuditPlan (mAP) API", and the description opens with "Backend for the mAP".
- Servers: `http://localhost:8080/api/v1` (local) added. Paths still start after `/api/v1`, as in `ui-plan.md`.
- Tag description: "Find Auditable Entities and tag attributes to them".
- Summaries now use the controller's wording ("Get filter dropdowns", "Search Auditable Entities"), and every other operation follows the same verb-first style. The API numbers stay in the table in `info.description`.
- The getFilters and searchAEs descriptions now open with the controller's text.

Kept on purpose:
- OpenAPI 3.0.3, not 3.1. Swagger UI shows 3.1 because springdoc generates 3.1 from the code. The hand-written contract stays on 3.0.3 for the contract-test validator and openapi-typescript.
- `info.version` 7.1.0, not v1. The contract version is bumped on every change, while `v1` is the URL version. `OpenApiConfig` can show 7.1.0.
- `AEPagedResponse`. The JSON is the same as `PagedResponse<AEResponse>`; springdoc just names the schema differently.
- `/xml` (health-controller) is left out because it is not part of the screen contract.

## Errors: ProblemDetail → ApiError

Every error response now uses `ApiError` with `application/json`, the same as the controller's `@ApiResponse`s. The fields keep their existing names (`type`, `title`, `status`, `detail`, `instance`, `errors[{ field, message }]`). They are still to be checked against the real `ApiError` class. `plan.md`, `ui-plan.md` and `coding-standards.md` still say ProblemDetail and need the same change.

# 7.1.0 → 8.0.0: common screen APIs (breaking)

- Filters, AE search and saved filters are shared by every screen. The UI puts the screen in the URL:
  - `GET /screens/{screen}/filters` (was `/attribute-tagging/filters`)
  - `POST /screens/{screen}/aes/search` (was `/attribute-tagging/aes/search`)
  - `GET` / `POST /screens/{screen}/saved-filters`, `DELETE /screens/{screen}/saved-filters/{id}`
- `{screen}` is the `Screen` enum, now kebab-case: `attribute-tagging`. An unknown screen returns 400.
- `screen` is removed from the search body. `FilterResponse.screen` echoes the screen in the URL.
- Saved filters are per user and per screen (max 3 per screen).
- New tag `Screens`. The operationIds are unchanged.
- Screen-only APIs (AE detail, lock, update, attribute types, history) stay under `/attribute-tagging/…`. `GET /users` is unchanged.
- Backend: `@GetMapping("/screens/{screen}/filters")` with `Screen.fromValue(screen)` in place of the hard-coded `ATTRIBUTE_TAGGING`, and drop `screen` from `FilterRequest`.

# 8.0.0 → 8.1.0: basic and advanced filters (additive)

- The screen shows a few dropdowns on load (basic) and the rest under Advanced filters (advanced). Which are basic is decided per screen.
- `GET /screens/{screen}/filters` takes an optional `group` query parameter (`basic` | `advanced`). Omitted = every dropdown, as before. Unknown group returns 400.
- `FilterOption` has a required `group` field. New schema `FilterGroup`, new parameter `FilterGroupQuery`.
- Attribute Tagging: basic = aeId, aeName, aeOwner, businessDivision. The other 12 (including businessArea) are advanced.
- getFilters examples split into `basic` and `advanced`; 400 response added.
- Backend: `FilterGroup` enum, `ScreenOption.group()`, `FilterService.getFilters(screen, group)`, optional `@RequestParam group` on the controller.
