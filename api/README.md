# myAuditPlan API

OpenAPI 3.0 specification for the myAuditPlan backend (Attribute Tagging screen).

- Spec: [`mAP-OpenAPI-Spec.yml`](mAP-OpenAPI-Spec.yml)
- View it: paste the file into https://editor.swagger.io, or serve it from the Spring Boot app with springdoc.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/auditable-entities` | The logged-in user's AEs, paged (page starts at 1, pageSize up to 1000) |
| POST | `/auditable-entities/search` | Search and filter with criteria (INCLUDE / EXCLUDE) |
| GET | `/auditable-entities/filters` | Filter dropdowns and options |
| GET | `/auditable-entities/{aeId}` | One AE, on click |
| GET | `/auditable-entities/{aeId}/recommendations` | Recommendations for an AE |
| GET/POST/PUT/DELETE | `/saved-filters` | The user's saved filters |

The owner is taken from the bearer token and is never a request parameter.
