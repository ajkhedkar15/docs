# myAuditPlan API

OpenAPI 3.0 specification for the myAuditPlan backend (Attribute Tagging screen).

- Spec: [`mAP-OpenAPI-Spec.yml`](mAP-OpenAPI-Spec.yml)
- View it: paste the file into https://editor.swagger.io, or serve it from the Spring Boot app with springdoc.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/auditable-entities?scope=MINE\|ALL` | AEs, paged (page starts at 1, pageSize up to 1000). MINE by default |
| POST | `/auditable-entities/search` | Search and filter with criteria (INCLUDE / EXCLUDE) |
| GET | `/auditable-entities/filters?scope=MINE\|ALL` | Filter dropdowns and options |
| GET | `/auditable-entities/{aeId}` | One AE, on click |
| GET | `/auditable-entities/{aeId}/recommendations` | Recommendations for an AE |
| GET/POST/PUT/DELETE | `/saved-filters` | The user's saved filters |

## View vs edit

- Every user can **view** every AE. The screen opens on `scope=MINE` (AEs the user owns); switching to `scope=ALL` shows every AE.
- Only an owner can **edit** an AE. Each AE in a response has `canEdit`, computed by the server from the bearer token; the UI shows the rest read-only.
- Edit endpoints re-check ownership and return **403** for non-owners. The owner is never a request parameter.
