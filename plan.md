# plan.md: myAuditPlan (mAP) backend build plan

The single plan for building the mAP backend (`aur_map_api_svc`). Give this file to GitHub Copilot (Chat or agent mode) together with the API spec, and it should generate code that looks like the code we already have.

> **How to use it with Copilot**
> 1. Open `plan.md` and `api/openapi.yml` in the editor (or add both with `#file`).
> 2. Ask for one slice at a time, for example: *"Following plan.md, implement `GET /attribute-tagging/aes/{aeId}` from openapi.yml: controller method, service interface + impl, repository methods, DTO with `from(...)`."*
> 3. Review against the **Checklist** at the end before you commit.
>
> If this file and older docs disagree (for example `copilot/backend-copilot-instructions.md`), **this file wins**. If this file and `api/openapi.yml` disagree on an endpoint or a field, **the spec wins**: fix the spec first, in the same PR.

---

## 1. Tech stack

| | |
|---|---|
| Language | Java 21 (records, switch expressions, `Stream.toList()`) |
| Framework | Spring Boot 3.x: Spring Web, Spring Data JPA, Bean Validation |
| ORM | Hibernate 6 (do **not** rely on 6.4+ only APIs such as `collectionOverlaps`) |
| Database | PostgreSQL, schema `aur_map_app` |
| Boilerplate | Lombok (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`) |
| Base package | `com.ss.corp.map.api` |
| API contract | `api/openapi.yml` (OpenAPI 3.0.3, one file) |

Use only libraries already in `pom.xml`. Do not add a dependency without saying why in the PR.

## 2. Principles

- **KISS.** The simplest code that works. No abstraction until there are two real uses for it. A `switch` beats a strategy framework.
- **SOLID**, applied lightly:
  - *Single responsibility*: a controller does HTTP, a service does business rules, a repository does data access.
  - *Open/closed*: a new screen or a new dropdown is a new enum constant, not a change to the service.
  - *Interface segregation / dependency inversion*: controllers depend on service **interfaces**, never on `Impl` classes.
- **YAGNI.** Build what the spec asks for now. No "maybe later" parameters, flags or layers.
- **Constructor injection only** (`@RequiredArgsConstructor` + `private final` fields). Never `@Autowired` on fields.
- **Entities never leave the service layer.** Controllers receive and return DTOs only.

## 3. Packages

```
com.ss.corp.map.api
├── controller      REST controllers, one per screen / resource
├── service         service interfaces
│   └── impl        their implementations (XxxServiceImpl)
├── repository      Spring Data JPA repository interfaces
├── entity          JPA entities (tables)
├── model           DTOs: request and response records
├── enums           enums and the interfaces they implement (Screen, ScreenFilter, XxxFilter)
├── exception       custom exceptions + GlobalExceptionHandler
├── config          @Configuration classes and AppProperties (app.yml binding)
└── util            small static helpers with no Spring state
```

Rules:
- Packages by **layer**, as above. No package called `filter` (it clashes with Spring/servlet filters); no `common`, `misc` or `helper` dumping grounds.
- `util` classes are `final` with a private constructor and only `static` methods. If a helper needs a Spring bean, it is a service, not a util.

## 4. Layer by layer

### 4.1 Controller
- One controller per screen/resource, e.g. `AttributeTaggingController`, mapped to `/api/v1/<screen>`.
- **Thin.** Each method: take the request, call **one** service method, return the DTO. No business logic, no repository calls, no `try/catch` (the `GlobalExceptionHandler` does errors).
- Method names, paths, parameters and response types follow `api/openapi.yml` exactly.
- Validate input with Bean Validation (`@Valid`, `@NotBlank`, `@Min`, `@Max`) and `@Validated` on the class.
- Return the DTO directly (Spring sends 200). Use `ResponseEntity` only when the status or headers change (201, 204).

```java
@RestController
@RequestMapping("/api/v1/attribute-tagging")
@RequiredArgsConstructor
@Validated
public class AttributeTaggingController {

    private final FilterService filterService;
    private final AEService aeService;
    private final CurrentUser currentUser;

    @GetMapping("/filters")
    public FilterResponse getFilters() {
        return filterService.getFilters(Screen.ATTRIBUTE_TAGGING.getValue());
    }

    @PostMapping("/aes/search")
    public PagedResponse<AEResponse> searchAEs(
            @Valid @RequestBody FilterRequest request,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return aeService.search(request, page, pageSize, currentUser.name());
    }
}
```

### 4.2 Service
- **One interface + one implementation** per area: `AEService` in `service`, `AEServiceImpl` in `service.impl`.
- The interface carries the Javadoc (what it does, params, what it throws). The impl has `@Override` and no repeated Javadoc.
- Every impl **extends `AbstractBaseService`** (see 4.2.1), which gives it `log` and `appProperties`. Annotations on the impl: `@Service`, `@RequiredArgsConstructor`. Don't add `@Slf4j` or your own logger.

```java
@Service
@RequiredArgsConstructor
public class FilterServiceImpl extends AbstractBaseService implements FilterService {

    private final AuditableEntityRepository aeRepository;          // own dependencies: constructor injection

    @Override
    @Transactional(readOnly = true)
    public FilterResponse getFilters(String screen) {
        log.debug("Loading filters for screen {}", screen);       // from AbstractBaseService
        ...
    }
}
```
- `@Transactional(readOnly = true)` on reads, `@Transactional` on writes, on the impl's public methods.
- Business rules and validation that Bean Validation can't express live here; throw `ValidationException(field, message)` for bad input (→ 400) and `NotFoundException` for a missing record (→ 404).
- Converts entities to DTOs before returning (see 4.5). Never returns an entity.
- Private helper methods are fine and preferred over new classes, until a helper is needed by two services.

#### 4.2.1 `AbstractBaseService`
The one base class for services (`service` package). It holds what every service needs and nothing else: a logger and the app settings from `application.yml`.

```java
public abstract class AbstractBaseService {

    /** Logger named after the concrete service class. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected AppProperties appProperties;

    /** Setter injection so subclasses keep @RequiredArgsConstructor for their own dependencies. */
    @Autowired
    public final void setAppProperties(AppProperties appProperties) {
        this.appProperties = appProperties;
    }
}
```

Rules:
- Only cross-cutting things go here (logger, `AppProperties`). No business methods, no repositories, no helper logic: those go in the service or in `util`.
- Settings are read through `appProperties`, never with `@Value` or `Environment` in a service.
- The setter here is the **only** place setter/`@Autowired` injection is allowed.
- In unit tests call `service.setAppProperties(testProperties)` after building the service with its mocks.

`AppProperties` (in `config`) binds the `app:` block of `application.yml`:

```java
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull Paging paging) {

    public record Paging(@Min(1) int defaultPageSize, @Min(1) int maxPageSize) { }
}
```
```yaml
app:
  paging:
    default-page-size: 20
    max-page-size: 100
```
Enable it once with `@ConfigurationPropertiesScan` on the application class. Add a nested record per area as settings appear; don't add settings nobody reads.

### 4.3 Repository
- Interfaces only, extending `JpaRepository<Entity, UUID>`; add `JpaSpecificationExecutor<Entity>` when the entity has dynamic search.
- Prefer, in this order:
  1. **Derived query methods** (Spring builds the query from the name):
     `List<AuditableEntityAttribute> findByAeIdAndAttrStatusTrue(String aeId);`
     `Optional<AuditableEntity> findByAeIdAndAeStatusTrue(String aeId);`
  2. **`@Query` with JPQL** when the name would be unreadable or you need `distinct`/projections:
     `@Query("select distinct a.aeName from AuditableEntity a where a.aeStatus = true order by a.aeName")`
  3. **`@Query(nativeQuery = true)`** only for PostgreSQL-specific SQL (arrays, `unnest`):
     `select distinct o from aur_map_app.auditable_entity, unnest(ae_owner) o where ae_status order by o`
  4. **`Specification`** for searches where the user picks any combination of filters (built inside the service, see 7.2).
- No business logic in repositories, no default methods with logic.
- Return `List`, `Optional` or `Page`. Never return `null`.

### 4.4 Entity
- One class per table, in `entity`. `@Entity`, `@Table(name = "...", schema = "aur_map_app")`.
- Lombok: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`. **No** `@Data` (breaks JPA `equals`/`hashCode`).
- `UUID` id, `Instant` timestamps, `Boolean`/`boolean` status flags.
- PostgreSQL arrays: `@JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "text[]") List<String>`.
- Avoid JPA relationships (`@ManyToOne`, `@OneToMany`) unless a feature truly needs navigation; join by business key (`aeId`) in queries instead. Keeps loading predictable.
- Small domain checks that use only the entity's own fields can live on it (e.g. `isOwnedBy(user)`).
- Entities must **not** import anything from `model` (DTOs).

### 4.5 DTO (`model`) and conversion
- DTOs are Java **records**. Names: `XxxRequest` for bodies, `XxxResponse` for outputs, plain nouns for nested parts (`FilterOption`, `Person`).
- Field names and JSON shape match `api/openapi.yml` exactly.
- Normalise nulls in the compact constructor (`list == null ? List.of() : List.copyOf(list)`).
- **No mapper classes.** Conversion lives on the DTO as static factory / builder methods:
  - Entity → DTO: `static XxxResponse from(Entity e, ...)`
  - DTO → Entity: `Entity toEntity()` using the entity's Lombok `@Builder`
  - Updating an existing entity from a request: `void applyTo(Entity e)`
- The DTO depends on the entity, never the other way round.

```java
public record AEResponse(String aeId, String aeName, String aeDescription, String businessArea,
                         Instant modifiedOn, int recommendationCount, boolean isOwner, Person lockedBy) {

    public static AEResponse from(AuditableEntity ae, String currentUser) {
        return new AEResponse(ae.getAeId(), ae.getAeName(), ae.getAeDesc(), ae.getBusinessArea(),
                ae.getModifiedOn(), 0, ae.isOwnedBy(currentUser), null);
    }
}

public record SavedFilterRequest(@NotBlank String name, @NotNull FilterRequest filter) {

    public SavedFilter toEntity(String owner) {
        return SavedFilter.builder().name(name).owner(owner).filter(filter.filters()).build();
    }
}
```

> The code in `code/` still has an `AEMapper` class from before this decision; replace it with `AEResponse.from(...)` when you touch it.

### 4.6 Enums
- `enums` holds enums and the small interfaces they implement.
- A value the API sends or receives has a `String` field (`key`/`value`) with Lombok `@Getter`; don't expose `name()` to the API.
- Lookup from API text: `static Xxx fromValue(String)` that throws `ValidationException` (→ 400) for unknown values.

### 4.7 Exceptions and errors
- `ValidationException(field, message)` → 400, `NotFoundException` → 404, `ForbiddenException` → 403, `ConflictException` (e.g. lock held) → 409.
- One `@RestControllerAdvice GlobalExceptionHandler` turns them into RFC 7807 `ProblemDetail` responses as the spec defines. Bean Validation errors (`MethodArgumentNotValidException`, `ConstraintViolationException`) → 400 with the field list.
- Unexpected exceptions → 500 with a generic message; log the stack trace, never return it.

### 4.8 Logging
- Services use the `log` from `AbstractBaseService`; other classes that need logging use `@Slf4j`. `debug` for flow ("Loading filters for screen {}"), `info` for business events (save, lock), `warn`/`error` for problems.
- Parameterised messages (`{}`), never string concatenation. Never log personal data beyond user names.

## 5. Naming

| Thing | Pattern | Example |
|---|---|---|
| Controller | `<Screen>Controller` | `AttributeTaggingController` |
| Service | `<Area>Service` / `<Area>ServiceImpl` | `AEService` / `AEServiceImpl` |
| Repository | `<Entity>Repository` | `AuditableEntityRepository` |
| Entity | table name in PascalCase, singular | `AuditableEntityAttribute` |
| DTO | `<Thing>Request` / `<Thing>Response` | `FilterRequest`, `AEResponse` |
| Screen filter enum | `<Screen>Filter` | `AttributeTaggingFilter` |
| Methods | verb first | `getFilters`, `search`, `findDistinctAeNames` |
| Constants | `UPPER_SNAKE` | `MAX_PAGE_SIZE` |

"AE" stays upper case in class names (`AEService`, `AEResponse`), lower camel in fields (`aeId`).

## 6. API conventions

- Base path `/api/v1`, then the screen (`/attribute-tagging`), then the resource.
- **Paging:** `page` starts at **1** in the API (`PageRequest.of(page - 1, ...)` inside), default `pageSize` 20, max 100. Responses use `PagedResponse<T>` `{ page, pageSize, totalRecords, totalPages, items }`.
- **Sorting** must be stable: always add the id as a tiebreaker (`Sort.by("aeName", "aeId")`).
- **Filters:** `{ "screen": "...", "filters": { "<key>": ["value", ...] } }`. OR within a key, AND across keys. `{}` = everything. Unknown key → 400.
- No free-text search on the server.
- Current user comes from the `CurrentUser` bean (header/SSO), never from the request body.

## 7. What is built so far (Attribute Tagging, APIs 1 and 2)

Code lives in `code/src/main/java/com/ss/corp/map/api/` (PR #11 merged, PR #12 open).

### 7.1 Tables

| Entity | Table | Key fields |
|---|---|---|
| `AuditableEntity` | `aur_map_app.auditable_entity` | `aeId` (unique business key), `aeName`, `aeDesc`, `aeStatus`, `aeOwner text[]`, `businessArea`, `division`, `modifiedOn`, `modifiedBy` |
| `AuditableEntityAttribute` | `aur_map_app.auditable_entity_attributes` | `aeId`, `attrType`, `attrId`, `attrName`, `attrComment`, `attrStatus`; unique `(ae_id, attr_type, attr_id)` |

All 11 attribute types share the one attributes table, told apart by `attr_type` (`est`, `risk`, `legalEntity`, `regulation`, `thirdParty`, `application`, `model`, `archerRiskUnit`, `costCenter`, `location`, `product`). DB fixes these entities expect are in `code/README.md`.

### 7.2 Screen filters: one enum per screen

```
UI sends screen ─► Screen.fromValue(screen) ─► that screen's filter enum ─► one query per value ─► response
```

```java
public interface ScreenFilter { String getKey(); }

@Getter
public enum AttributeTaggingFilter implements ScreenFilter {
    AE_ID("aeId"), AE_NAME("aeName"), AE_OWNER("aeOwner"),
    EST("est"), RISK("risk"), LEGAL_ENTITY("legalEntity"), REGULATION("regulation"),
    THIRD_PARTY("thirdParty"), APPLICATION("application"),
    BUSINESS_AREA("businessArea"), BUSINESS_DIVISION("businessDivision"),
    MODEL("model"), ARCHER_RISK_UNIT("archerRiskUnit"), COST_CENTER("costCenter"),
    LOCATION("location"), PRODUCT("product");
    private final String key;
    AttributeTaggingFilter(String key) { this.key = key; }
}

@Getter
public enum Screen {
    ATTRIBUTE_TAGGING("attributeTagging", AttributeTaggingFilter.values());
    private final String value;
    private final List<ScreenFilter> filters;           // List, not array
    Screen(String value, ScreenFilter[] filters) { this.value = value; this.filters = List.of(filters); }
    public static Screen fromValue(String value) { /* match on value, else ValidationException → 400 */ }
}
```

**Adding a screen:** create `<Screen>Filter implements ScreenFilter`, add one constant to `Screen`. Services don't change, unless the new screen reads a new AE column (one more `case`).

**API 1, `FilterServiceImpl.getFilters(screen)`:** loop over `Screen.fromValue(screen).getFilters()`, and for each key call `findOptions(key)`:
- `aeId`, `aeName`, `businessArea`, `businessDivision` → `findDistinct...()` JPQL queries on `AuditableEntity`
- `aeOwner` → native `unnest(ae_owner)` query
- anything else → `attributeRepository.findDistinctNamesByType(key)`

Result: `FilterResponse(screen, List<FilterOption(type, options)>)` in enum order. **No cache yet** (infra pending); later add `@Cacheable(cacheNames = "filters", key = "#screen")` + `@EnableCaching`, nothing else changes.

**API 2, `AEServiceImpl.search(request, page, pageSize, currentUser)`:**
1. Validate paging (page ≥ 1, 1 ≤ pageSize ≤ `appProperties.paging().maxPageSize()`).
2. `Screen.fromValue(request.screen())`; reject keys not in that screen's enum.
3. Build a `Specification` in private methods of the service (no separate builder class):
   - always `aeStatus = true`
   - AE column keys → `root.get(field).in(values)` (`businessDivision` maps to field `division`)
   - `aeOwner` → OR of `array_position(ae_owner, owner) IS NOT NULL` via `cb.function(...)` (works on any Hibernate 6)
   - attribute keys → `EXISTS` subquery on `AuditableEntityAttribute` (same `aeId`, `attrType = key`, `attrName in values`, `attrStatus = true`)
4. `aeRepository.findAll(spec, PageRequest.of(page - 1, pageSize, Sort.by("aeName", "aeId")))`.
5. Map each entity with `AEResponse.from(ae, currentUser)`; return `PagedResponse.of(result, items)` (`result` is the Spring `Page`).

### 7.3 Open items

- Spec: add `screen` to `FilterRequest`/`FilterResponse` in `api/openapi.yml`, and settle the screen name (`attribute-tagging` in paths vs `attributeTagging` in `Screen`).
- Controller, `CurrentUser`, `GlobalExceptionHandler`, `config` classes: not written yet.
- `recommendationCount` and `lockedBy` on `AEResponse` are placeholders (0 / null) until those features exist.
- Replace `AEMapper` with `AEResponse.from(...)` (4.5).
- Make `FilterServiceImpl` and `AEServiceImpl` extend `AbstractBaseService`; move `MAX_PAGE_SIZE` to `app.paging.max-page-size`.

## 8. Next APIs (from the spec), in suggested order

| # | Endpoint | Notes |
|---|---|---|
| 3 | `GET /aes/{aeId}` | AE + 11 attribute-type sections; `isOwner`, `lock`, `canEdit`. `NotFoundException` if missing/inactive. |
| 4 | `POST`/`DELETE /aes/{aeId}/lock` | Take/renew/release the edit lock; 409 if someone else holds it. |
| 5 | `PUT /aes/{aeId}/attributes` | Save the full tagged list; owner + lock holder only (403/409). `@Transactional`. |
| 6 | `GET /attribute-types/{type}/attributes` and `.../{attributeId}/children` | All Available list and children. Cache later. |
| 7 | `GET`/`POST /saved-filters`, `DELETE /saved-filters/{id}` | Max 3 per user; stores a `FilterRequest`. |

Each one = controller method + service interface method + impl + repository methods + DTO(s) with `from`/`toEntity`, following sections 4–6.

## 9. Testing

- **Service:** unit tests with JUnit 5 + Mockito, mocking repositories. One test per rule (paging limits, unknown screen, unknown filter key, owner flag).
- **Repository / Specification:** `@DataJpaTest` against PostgreSQL (Testcontainers) since we use arrays and native SQL; H2 won't do.
- **Controller:** `@WebMvcTest` with mocked services, checking status codes and JSON shape against the spec.
- Test names: `methodName_condition_expectedResult`.

## 10. Checklist before a PR

- [ ] Endpoint, fields and status codes match `api/openapi.yml` (spec updated in the same PR if it changed)
- [ ] Controller is thin: one service call, no logic, no repository
- [ ] Controller depends on the service **interface**
- [ ] No entity in any controller signature or response; conversion via `Dto.from(...)` / `toEntity()`
- [ ] Repository uses derived names where readable, `@Query` otherwise, native SQL only for PostgreSQL features
- [ ] `@Transactional(readOnly = true)` on reads, `@Transactional` on writes
- [ ] Bad input → `ValidationException`/Bean Validation (400); missing → 404; no `try/catch` in controllers
- [ ] Service impl extends `AbstractBaseService`; uses its `log` and `appProperties` (no `@Value`, no own logger)
- [ ] Constructor injection, no field `@Autowired`, no `@Data` on entities
- [ ] No new dependency without a reason; no unused code, flags or parameters (KISS/YAGNI)
- [ ] Tests for the new service rules
