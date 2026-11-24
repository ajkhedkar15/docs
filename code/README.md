# Backend code: filters and AE search (common screen APIs)

Code to copy into `aur_map_api_svc` (`com.ss.corp.map.api`).
Java 21, Spring Boot 3.2+ (Hibernate 6.4+), Spring Data JPA, PostgreSQL 13+.

- API 1: `GET /screens/{screen}/filters?group=basic|advanced` returns `FilterResponse`
- API 2: `POST /screens/{screen}/aes/search` takes `FilterRequest` and returns `PagedResponse<AEResponse>`

## How it fits together
- `Screen` lists every screen. Each screen has one option enum (`AttributeTaggingOption`), and every option has
  a key, a source (`AE` / `ATTRIBUTE_TYPE`) and a group (`BASIC` / `ADVANCED`).
- **Filters:** `FilterServiceImpl` loops over the screen's options. AE options go to `AEService` and attribute
  types go to `AttributeService`. The service never calls a repository directly.
- **Search:** `AEServiceImpl.search` checks the keys (`requireKnownFilters`, 400) and runs
  `AESearchSpecification`, sorted by AE name then AE id. The specification routes each key by its source the
  same way: an AE key is a column `in (...)`, and an attribute type is `exists` an active tag of that type with
  one of the names. OR within a filter, AND across filters, and empty filters return all active AEs.
- Adding a screen: one `Screen` constant and one option enum. Adding a filter: one enum line, plus one case in
  `FilterServiceImpl.aeOptions` and `AESearchSpecification.aeColumn` if it's an AE column.

## Attribute types: EST only for now
Each attribute type has its own master table, with its own columns, and some have parent/child levels, so
each type gets its own entity and repository (no shared base class). Only EST is mapped. To add the next one,
for example Key Legal Entities:
1. `entity/LegalEntityMaster` mapped to its table, and `repository/LegalEntityMasterRepository` with a
   distinct-names query (for a hierarchy, only the levels that can be tagged).
2. One case in `AttributeServiceImpl.findNamesByType`: `case "legalEntity" -> legalEntityMasterRepository...`.
3. One line in `AttributeTaggingOption`: `LEGAL_ENTITY("legalEntity", ATTRIBUTE_TYPE, ADVANCED)`, in screen order.
Search needs no change: it matches tags in `auditable_entity_attributes` by `attr_type` and name.

## Files
| Package | Class | What it is |
|---|---|---|
| `enums` | `Screen` | All screens; `fromKey` (400 on unknown), `findOption`, `requireKnownFilters` (400 on unknown keys) |
| `enums` | `ScreenOption`, `AttributeTaggingOption` | A screen's dropdowns in screen order: key, source, group |
| `enums` | `FilterGroup` | `basic` / `advanced`; `fromKey` gives 400 on unknown |
| `model` | `FilterRequest` | Search body `{ filters: { <type>: [text] } }` (also what a saved filter stores) |
| `model` | `FilterResponse`, `FilterOption` | `{ screen, filters: [{ type, group, options: [text] }] }` |
| `model` | `AEResponse`, `PagedResponse<T>`, `Person` | One AE card; `{ page, pageSize, totalRecords, totalPages, items }`, pages from 1 |
| `entity` | `AuditableEntity`, `AuditableEntityAttribute` | `aur_map_app.auditable_entity` and its tags |
| `entity` | `EstMaster` | `est_master`, read-only: the EST dropdown values |
| `repository` | `AuditableEntityRepository` | Spring Data JPA + Specification; AE dropdown value queries |
| `repository` | `AESearchSpecification` | Selected filters to a JPA `Specification` |
| `repository` | `EstMasterRepository` | Distinct EST names |
| `service` | `FilterService`, `AEService`, `AttributeService` (+ `impl`) | See above |
| `exception` | `BadRequestException`, `FieldError` | 400 with `errors[{ field, message }]`, same as the app's |
| `src/test` | `AESearchIT`, `FilterServiceIT` | Search and dropdowns against PostgreSQL, 9 tests |

`snippets/` holds the two controller changes: `getFilters` gets an optional `group` param, and `searchAEs` keeps
its signature but passes `userName` to `aeService.search`.

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
- **Tested:** compiled with Spring Boot 3.3.5 (Hibernate 6.5) and run against PostgreSQL 16. `AESearchIT`
  covers empty filters, the owner array, OR within and AND across filters, EST tags (active only),
  paging, isOwner and 400s; `FilterServiceIT` covers the basic group and EST values from `est_master`. Both run
  in a rolled-back transaction and only look at their own `TEST-*` rows.
- **Owner filter:** `ae_owner` is `text[]`. `&&` / `@>` fail because Hibernate binds the values as `varchar[]`
  (`operator does not exist: text[] && character varying[]`; the old builder had this bug). The search uses
  `array_position(ae_owner, ?) > 0` per picked owner instead, with the owner bound as a parameter.
- **TODO in `AEServiceImpl.toResponse`:** `recommendationCount` is 0 and `lockedBy` is null until the
  recommendation and lock services exist.
- **TODO in `EstMaster`:** schema `aur_map_app` and the column types (ids as text, `last_updated_on` as a
  timestamp) are guesses from the column list; check them against the table.
- **Response size:** as attribute types are added, `GET /filters` grows (2,000+ values per type). If it gets slow,
  add `@Cacheable` on `AttributeService.findNamesByType` (values change on the daily load).
- Only dropdowns with data are listed: the 5 AE ones and EST. Audit Team and the other 10 attribute types
  come later. The spec (8.1.0) still lists all 16 types as the target.
- Delete from the app (replaced by this design): `ScreenFilter`, `AeFilterCondition`, `AttributeTaggingFilter`,
  and the old `filter/` package (`FilterDefinition`, `AttributeTaggingFilters`, `AESpecificationBuilder`) and `FilterSource`.
