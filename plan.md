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
├── exception       MapException + subclasses, GlobalExceptionHandler
├── logger          LoggerService interface
│   └── impl        LoggerServiceImpl
├── context         RequestDetails + RequestDetailsHolder (thread-local per request)
├── filter          servlet filters only (RequestContextFilter)
├── config          @Configuration classes and AppProperties (app.yml binding)
└── util            small static helpers with no Spring state
```

Rules:
- Packages by **layer**, as above. `filter` is **only** for servlet filters (`OncePerRequestFilter`); screen dropdown code lives in `enums`. No `common`, `misc` or `helper` dumping grounds.
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

    @GetMapping("/filters")
    public FilterResponse getFilters() {
        return filterService.getFilters(Screen.ATTRIBUTE_TAGGING.getValue());
    }

    @PostMapping("/aes/search")
    public PagedResponse<AEResponse> searchAEs(
            @Valid @RequestBody FilterRequest request,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return aeService.search(request, page, pageSize, RequestDetailsHolder.getUserId());
    }
}
```

### 4.2 Service
- **One interface + one implementation** per area: `AEService` in `service`, `AEServiceImpl` in `service.impl`.
- The interface carries the Javadoc (what it does, params, what it throws). The impl has `@Override` and no repeated Javadoc.
- Every impl **extends `AbstractBaseService`** (see 4.2.1), which gives it `logger` (`LoggerService`) and `appProperties`. Annotations on the impl: `@Service`, `@RequiredArgsConstructor`. Don't add `@Slf4j` or your own logger.

```java
@Service
@RequiredArgsConstructor
public class FilterServiceImpl extends AbstractBaseService implements FilterService {

    private final AuditableEntityRepository aeRepository;          // own dependencies: constructor injection

    @Override
    @Transactional(readOnly = true)
    public FilterResponse getFilters(String screen) {
        String operation = "FilterService -> getFilters";
        logger.operationStarted(operation);                         // from AbstractBaseService
        ...
        logger.operationCompleted(operation);
        return response;
    }
}
```
- `@Transactional(readOnly = true)` on reads, `@Transactional` on writes, on the impl's public methods.
- Business rules and validation that Bean Validation can't express live here; throw `ValidationException(field, message)` for bad input (→ 400) and `NotFoundException` for a missing record (→ 404).
- Converts entities to DTOs before returning (see 4.5). Never returns an entity.
- Private helper methods are fine and preferred over new classes, until a helper is needed by two services.

#### 4.2.1 `AbstractBaseService`
The one base class for services (`service` package). It holds what every service needs and nothing else: the `LoggerService` and the app settings from `application.yml`.

```java
public abstract class AbstractBaseService {

    protected LoggerService logger;
    protected AppProperties appProperties;

    /** Setter injection so subclasses keep @RequiredArgsConstructor for their own dependencies. */
    @Autowired
    public final void setLogger(LoggerService logger) {
        this.logger = logger;
    }

    @Autowired
    public final void setAppProperties(AppProperties appProperties) {
        this.appProperties = appProperties;
    }
}
```

Rules:
- Only cross-cutting things go here (`LoggerService`, `AppProperties`). No business methods, no repositories, no helper logic: those go in the service or in `util`.
- Settings are read through `appProperties`, never with `@Value` or `Environment` in a service.
- These setters are the **only** place setter/`@Autowired` injection is allowed.
- In unit tests call `setLogger(mock)` and `setAppProperties(testProperties)` after building the service with its mocks.

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

All our exceptions extend one unchecked base that carries the HTTP status, so the handler needs one method for all of them.

```java
@Getter
public abstract class MapException extends RuntimeException {
    private final HttpStatus status;
    private final String field;                      // optional: which input was wrong

    protected MapException(HttpStatus status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }
}

public class ValidationException extends MapException {      // 400
    public ValidationException(String field, String message) { super(HttpStatus.BAD_REQUEST, field, message); }
}
public class NotFoundException extends MapException {        // 404
    public NotFoundException(String what, String id) { super(HttpStatus.NOT_FOUND, null, what + " not found: " + id); }
}
public class ForbiddenException extends MapException {       // 403, e.g. not the AE owner
    public ForbiddenException(String message) { super(HttpStatus.FORBIDDEN, null, message); }
}
public class ConflictException extends MapException {        // 409, e.g. lock held by someone else
    public ConflictException(String message) { super(HttpStatus.CONFLICT, null, message); }
}
```

One `@RestControllerAdvice` turns every error into the RFC 7807 `ProblemDetail` the spec defines, and logs it through `LoggerService`:

```java
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final LoggerService logger;

    @ExceptionHandler(MapException.class)
    public ProblemDetail handleMap(MapException ex, HttpServletRequest request) {
        logger.operationFailed(request.getRequestURI(), ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        if (ex.getField() != null) {
            pd.setProperty("errors", List.of(Map.of("field", ex.getField(), "message", ex.getMessage())));
        }
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)        // @Valid on a body
    public ProblemDetail handleBody(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList());
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)           // @Min/@Max on params
    public ProblemDetail handleParams(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        pd.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList());
        return pd;
    }

    @ExceptionHandler(Exception.class)                               // anything unexpected
    public ProblemDetail handleOther(Exception ex, HttpServletRequest request) {
        logger.error(request.getRequestURI(), ex);                   // stack trace in the log only
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }
}
```

Rules:
- Throw a `MapException` subclass for every expected failure. Never throw `RuntimeException`/`IllegalArgumentException` for user errors.
- No `try/catch` in controllers or services just to log or re-wrap; the handler logs once. Catch only when you can actually recover.
- Never return stack traces or SQL messages to the client.
- New error kinds = a new subclass with its status; the handler doesn't change.

### 4.8 Logging: `LoggerService` + request context

We log through our own `LoggerService` (package `logger`, impl in `logger.impl`), so every line carries who made the request and its correlation id, taken from a per-request thread-local.

**Request context (package `context`):**
```java
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RequestDetails {
    private String userId;
    private String correlationId;
}

public final class RequestDetailsHolder {
    private static final ThreadLocal<RequestDetails> HOLDER = ThreadLocal.withInitial(RequestDetails::new);

    private RequestDetailsHolder() { }

    public static RequestDetails get()           { return HOLDER.get(); }
    public static void set(RequestDetails d)     { HOLDER.set(d); }
    public static String getUserId()             { return HOLDER.get().getUserId(); }
    public static String getCorrelationId()      { return HOLDER.get().getCorrelationId(); }
    public static void clear()                   { HOLDER.remove(); }
}
```

**Filled by one servlet filter (package `filter`), and always cleared:**
```java
@Component
@RequiredArgsConstructor
public class RequestContextFilter extends OncePerRequestFilter {

    private final LoggerService logger;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .orElse(UUID.randomUUID().toString());
        RequestDetailsHolder.set(RequestDetails.builder()
                .userId(request.getHeader("X-User-Id"))          // replace with SSO principal when available
                .correlationId(correlationId)
                .build());
        response.setHeader("X-Correlation-Id", correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            RequestDetailsHolder.clear();                       // threads are pooled: never skip this
        }
    }
}
```

**`LoggerService`:**
```java
public interface LoggerService {
    void debug(String message);
    void info(String message);
    void operationStarted(String operation);
    void operationCompleted(String operation);
    void operationFailed(String operation, String reason);
    void error(String operation, Throwable ex);
}

@Slf4j
@Service
public class LoggerServiceImpl implements LoggerService {

    @Override public void debug(String message) { log.debug("[{}] [{}] {}", user(), cid(), message); }
    @Override public void info(String message)  { log.info("[{}] [{}] {}", user(), cid(), message); }

    @Override public void operationStarted(String operation) {
        log.info("[{}] [{}] {} : started", user(), cid(), operation);
    }
    @Override public void operationCompleted(String operation) {
        log.info("[{}] [{}] {} : completed", user(), cid(), operation);
    }
    @Override public void operationFailed(String operation, String reason) {
        log.warn("[{}] [{}] {} : failed : {}", user(), cid(), operation, reason);
    }
    @Override public void error(String operation, Throwable ex) {
        log.error("[{}] [{}] {} : error", user(), cid(), operation, ex);
    }

    private String user() { return RequestDetailsHolder.getUserId(); }
    private String cid()  { return RequestDetailsHolder.getCorrelationId(); }
}
```

Rules:
- Services use `logger` from `AbstractBaseService`; controllers, filters and the exception handler inject `LoggerService`. Only `LoggerServiceImpl` uses `@Slf4j` directly.
- Operation names: `"<Class> -> <method>"`, e.g. `"AEService -> search"`. Started/completed around each public service method; failures are logged by `GlobalExceptionHandler`, not by the service.
- Parameterised SLF4J messages (`{}`), never `String.format` or concatenation for log lines.
- `operationFailed` is `warn` (expected 4xx), `error` is for unexpected failures with the stack trace.
- Read the current user from `RequestDetailsHolder`, never from the request body.
- Never log personal data beyond the user id. Never log request bodies at `info`.
- `@Async` or new threads don't inherit the thread-local: pass `RequestDetails` explicitly if you ever need it there.

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
| Exception | `<Kind>Exception extends MapException` | `ConflictException` |
| Constants | `UPPER_SNAKE` | `MAX_PAGE_SIZE` |

"AE" stays upper case in class names (`AEService`, `AEResponse`), lower camel in fields (`aeId`).

## 6. API conventions

- Base path `/api/v1`, then the screen (`/attribute-tagging`), then the resource.
- **Paging:** `page` starts at **1** in the API (`PageRequest.of(page - 1, ...)` inside), default `pageSize` 20, max 100. Responses use `PagedResponse<T>` `{ page, pageSize, totalRecords, totalPages, items }`.
- **Sorting** must be stable: always add the id as a tiebreaker (`Sort.by("aeName", "aeId")`).
- **Filters:** `{ "screen": "...", "filters": { "<key>": ["value", ...] } }`. OR within a key, AND across keys. `{}` = everything. Unknown key → 400.
- No free-text search on the server.
- Current user comes from `RequestDetailsHolder` (filled by `RequestContextFilter` from the header/SSO), never from the request body. Every response carries `X-Correlation-Id`.

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
- Controller, `logger`, `context`, `RequestContextFilter`, `MapException` family, `GlobalExceptionHandler`, `config` classes: not written yet. `ValidationException` in `code/` must be changed to extend `MapException`.
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
- [ ] Service impl extends `AbstractBaseService`; uses its `logger` and `appProperties` (no `@Value`, no `@Slf4j`)
- [ ] Failures throw a `MapException` subclass; no logging `try/catch` in controllers/services
- [ ] Constructor injection, no field `@Autowired`, no `@Data` on entities
- [ ] No new dependency without a reason; no unused code, flags or parameters (KISS/YAGNI)
- [ ] Tests for the new service rules
