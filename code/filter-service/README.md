# Simplified filter service

This is the simpler design for `GET /screens/{screen}/filters`, and it replaces the `filter/` package in `code/src`
for loading dropdowns. It works like this:
- A `Screen` enum lists all the screens.
- Each screen has one enum of its options, with a group (basic / advanced) and a source.
- `FilterServiceImpl` loops over the options and calls `AEService` or `AttributeService`, never a repository directly.

Java files are under `src/main/java/com/ss/corp/map/api/`, and the paths below are relative to it.
`AESpecificationBuilder` in `code/src` still handles search until search moves to this design.

Package root: `com.ss.corp.map.api`. Compiled and run against stubs on Java 21 + Lombok.

## Files
| File | What it is |
|---|---|
| `enums/ScreenOption.java` | Tiny interface every per-screen enum implements: `key()` + `source()` (`AE` or `ATTRIBUTE_TYPE`) |
| `enums/AttributeTaggingOption.java` | Attribute Tagging's dropdowns in screen order (5 AE options, 11 attribute types) |
| `enums/Screen.java` | Lists all screens. Keeps `fromKey` (400 on unknown screen) and `requireKnownFilters`. `findFilter` is now `findOption` |
| `service/impl/FilterServiceImpl.java` | Loops over the screen's options. `aeOptions` (switch over `AEService`), `attributeOptions` (`AttributeService`). Services only, no repos |

## Attribute options (AttributeService)
| File | What it is |
|---|---|
| `entity/attribute/AttributeEntity.java` | `@MappedSuperclass` with the shared columns: `attributeId`, `name`, `active` |
| `entity/attribute/<Type>Attribute.java` (11) | One read-only `@Entity @Immutable` per attribute type. Each holds only its `@Table` name |
| `repository/attribute/AttributeRepository.java` | `@NoRepositoryBean` base: `attributeType()` and one distinct-names query, shared through `#{#entityName}` |
| `repository/attribute/<Type>AttributeRepository.java` (11) | Says only which key it serves, e.g. `"legalEntity"` |
| `service/AttributeService.java` + `impl/AttributeServiceImpl.java` | Spring injects all 11 repos as a list, and the constructor turns them into a `Map<key, repo>`. `findNamesByType(key)` is a single map lookup |

Adding an attribute type: one entity, one repository, one enum line. The service doesn't change.

**TODO before running:** set the real table names (marked `TODO`) and check the column names in `AttributeEntity`.
If you already have an `AttributeRepository` in `com.ss.corp.map.api.repository`, this one is in the
`repository.attribute` sub-package, so the two don't clash. Rename one of them if that's confusing.

**Size warning:** `GET /filters` loads every value of all 16 dropdowns in one call, which is 11 attribute queries
plus 5 AE queries. The spec expects 2,000+ values per type, and Cost Centers and Applications may be much
larger. That's fine to start with. If the response gets slow or heavy, the first fixes are caching
(`@Cacheable` on `findNamesByType`, since the values change only on the daily load) or loading the big types on demand.

## Add to your services (if they don't have these yet)
```java
// AEService (+ impl just delegates to AERepository's existing findAllDistinct... queries)
List<String> findAllAeIds();             // aeRepository.findAllDistinctAEIds()
List<String> findAllAeNames();           // aeRepository.findAllDistinctAENames()
List<String> findAllAeOwners();          // aeRepository.findDistinctOwners()
List<String> findAllBusinessAreas();     // new: distinct business area
List<String> findAllBusinessDivisions(); // aeRepository.findAllDistinctDivisions()

// AttributeService (or your AttributeTaggingService)
List<String> findNamesByType(String attributeType); // active attribute names of one type, sorted, distinct
```

## Delete
- `ScreenFilter.java`
- `AeFilterCondition.java`
- `AttributeTaggingFilter.java` (replaced by `AttributeTaggingOption`)
- `AERepository` import from `FilterServiceImpl`

## Basic and advanced filters
- `enums/FilterGroup.java`: `BASIC` / `ADVANCED`. JSON is `basic` / `advanced`, and `fromKey` returns 400 for an unknown group.
- `ScreenOption.group()`: every screen enum sets its own group per option. Attribute Tagging's basic options are
  aeId, aeName, aeOwner and businessDivision. The other 12 are advanced. Basic options come first in the enum.
- `service/FilterService.java`: `getFilters(Screen screen, FilterGroup group)`, where `group = null` means all.
- `model/FilterOption.java`: now `(type, group, options)`. `model/FilterResponse.java` is unchanged, `(screen, filters)`.
- `snippets/ScreenController-getFilters.java.txt`: the new `getFilters` to paste in. It's the same as before
  plus an optional `@RequestParam String group`.
- Spec: 8.1.0 in `api-spec/openapi.yml`, with the entry in `api-spec/CHANGES.md`. Lint passes (0 errors, 0 warnings).

## Unchanged
- `BadRequestException`, `FieldError`.

## Search
The old `ScreenFilter.condition(...)` built the search predicates. With it gone, `AEServiceImpl.search` should
build them itself: loop over `request.filters()`, look up `screen.findOption(key)`, and for `AE` use
`root.get(column).in(values)`, for `ATTRIBUTE_TYPE` an `exists` subquery on the AE attributes table.
Not written here because I haven't seen `AEServiceImpl.search` or the entity names.


Audit Team is on the screen but has no data yet, so it is left out. Add it as one enum line and one `aeOptions` case when the data exists.
