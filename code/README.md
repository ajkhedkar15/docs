# Backend code: Attribute Tagging, APIs 1 and 2

DTOs, DAO and services for the first two APIs, to copy into `aur_map_api_svc` (`com.ss.corp.map.api`).
Java 21, Spring Boot 3.2+ (Hibernate 6.4+), Spring Data JPA, PostgreSQL.

- API 1: `GET /attribute-tagging/filters` → `FilterResponse`
- API 2: `POST /attribute-tagging/aes/search` with `FilterRequest` → `PagedResponse<AEResponse>`

## Files

| Package | Class | What it is |
|---|---|---|
| `model` | `FilterRequest` | Search body `{ screen, filters: { <type>: [text] } }` (also what a saved filter stores) |
| `model` | `FilterResponse`, `FilterOption` | `{ screen, filters: [{ type, options: [text] }] }` |
| `model` | `AEResponse` | One AE card |
| `model` | `PagedResponse<T>` | `{ page, pageSize, totalRecords, totalPages, items }`, pages from 1 |
| `model` | `Person` | `{ id, name }` (id = name until real user ids exist) |
| `entity` | `AuditableEntity` | `aur_map_app.auditable_entity` |
| `entity` | `AuditableEntityAttribute` | `aur_map_app.auditable_entity_attributes` |
| `enums` | `ScreenFilter` | Interface every screen's filter enum implements (`getKey()`) |
| `enums` | `AttributeTaggingFilter` | The screen's 16 dropdown keys, in screen order |
| `enums` | `Screen` | Screen name → its filter enum; unknown screen → 400 |
| `service` | `FilterService` / `impl.FilterServiceImpl` | API 1: loops the screen's enum, runs one query per dropdown |
| `service` | `AEService` / `impl.AEServiceImpl` | API 2: filters → JPA `Specification` (OR within a dropdown, AND across), paged |
| `mapper` | `AEMapper` | `AuditableEntity` → `AEResponse` |
| `repository` | `AuditableEntityRepository` | Spring Data JPA + Specification; dropdown value queries |
| `repository` | `AuditableEntityAttributeRepository` | Tags of an AE; attribute dropdown values |
| `exception` | `ValidationException` | 400 with field and message |

## Database changes these entities expect

```sql
-- auditable_entity_attributes: one row per AE + type + attribute
ALTER TABLE aur_map_app.auditable_entity_attributes DROP CONSTRAINT IF EXISTS <unique on ae_id>;
ALTER TABLE aur_map_app.auditable_entity_attributes DROP CONSTRAINT IF EXISTS <unique on attr_id>;
ALTER TABLE aur_map_app.auditable_entity_attributes DROP CONSTRAINT IF EXISTS <unique on attr_name>;
ALTER TABLE aur_map_app.auditable_entity_attributes
  ADD CONSTRAINT uq_ae_attr UNIQUE (ae_id, attr_type, attr_id),
  ADD COLUMN IF NOT EXISTS attr_comment text;
CREATE INDEX IF NOT EXISTS ix_ae_attr_type_name ON aur_map_app.auditable_entity_attributes (attr_type, attr_name);

-- auditable_entity
ALTER TABLE aur_map_app.auditable_entity DROP CONSTRAINT IF EXISTS <unique on ae_name>;   -- names can repeat
ALTER TABLE aur_map_app.auditable_entity ALTER COLUMN ae_desc TYPE text, ALTER COLUMN ae_desc DROP NOT NULL;
ALTER TABLE aur_map_app.auditable_entity RENAME COLUMN modfied_by TO modified_by;         -- if you can
CREATE INDEX IF NOT EXISTS ix_ae_name ON aur_map_app.auditable_entity (ae_name);
CREATE INDEX IF NOT EXISTS ix_ae_owner_gin ON aur_map_app.auditable_entity USING gin (ae_owner);
```
Replace `<unique on …>` with the real constraint names (`\d aur_map_app.auditable_entity_attributes` in psql).

## Notes

- **Not compiled here.** Expect small fixes (imports, Lombok) when you add the files to the project.
- **Owner filter:** `AEServiceImpl` uses Hibernate's `collectionOverlaps` (`ae_owner && values`). Test it first; if your Hibernate version doesn't translate it, filter owners with native SQL instead.
- **No cache yet** on `getFilters` (waiting on infra). Add `@Cacheable(cacheNames = "filters", key = "#screen")` + `@EnableCaching` later.
- **Screen name** is `attributeTagging`; the UI must send exactly that.
- **Next:** the controller, current user, and `GlobalExceptionHandler` (`ValidationException` → 400).
- `api/openapi.yml` doesn't have `screen` in the filters response and search body yet; update it to match these DTOs.
