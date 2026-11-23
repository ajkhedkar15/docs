# plan.md: myAuditPlan (mAP) backend build plan

The single plan for building the mAP backend (`aur_map_api_svc`). Give this file to GitHub Copilot (Chat or agent mode) together with the API spec, and it should generate code that looks like the code we already have.

We work **spec-first** (Spec-Driven Development, section 0): the OpenAPI spec is written and agreed before any code, and the code is built from it.

> **How to use it with Copilot**
> 1. Make sure the endpoint is already in `api/openapi.yml` and approved (section 0). No spec, no code.
> 2. Open `plan.md` and `api/openapi.yml` in the editor (or add both with `#file`).
> 3. Describe the business logic; Copilot replies with its understanding and plan, and builds only after you say "go" (0.6).
> 4. Review against the **Definition of Done** (section 15) before you raise the PR.
>
> If this file and older docs disagree (for example `copilot/backend-copilot-instructions.md`), **this file wins**. If this file and `api/openapi.yml` disagree on an endpoint or a field, **the spec wins**: fix the spec first, in the same PR.

---

## 0. Spec-Driven Development (SDD)

**The rule: the API spec comes first.** Every new endpoint, field, parameter, status code or error is written in `api/openapi.yml`, reviewed and merged (or at least approved) **before** backend or UI code is written. Code that does something the spec doesn't describe is a bug, even if it works.

Why: UI and backend can work in parallel from day one, Copilot gets an exact target instead of guessing, reviews happen on a 30-line YAML diff instead of 600 lines of Java, and the contract can't drift silently.

### 0.1 Workflow for every story

| Step | Who | What | Done when |
|---|---|---|---|
| 1. Design | Backend dev (+ UI dev) | Change `api/openapi.yml`: paths, params, schemas, **examples**, every error response | Spec PR opened |
| 2. Lint | CI | Redocly lint (`.github/workflows/api-spec.yml`) | Green check |
| 3. Review | 1 backend + 1 UI reviewer | Names, shapes, status codes, breaking or not (0.4) | Both approve, PR merged |
| 4. Mock | UI dev | Run a mock server from the spec, build the screen against it: `npx @stoplight/prism-cli mock api/openapi.yml` | UI unblocked |
| 5. Build | Backend dev | Implement from the spec with Copilot + this plan (0.6) | Code compiles, tests pass |
| 6. Verify | CI | Contract tests check real responses against the spec (0.5) | Green check |
| 7. Release | Backend dev | Bump `info.version` in the spec, note changes in the PR | Merged to `Develop` |

If you find during step 5 that the spec is wrong or incomplete: **stop, fix the spec first** (small spec PR, same reviewers), then continue. Never "fix it in code now and update the spec later".

### 0.2 What every operation in the spec must have
- `operationId` (verb + noun, e.g. `searchAEs`): it becomes the controller method name.
- `tags`: one tag per screen (`Attribute Tagging`).
- `summary` (one line) and `description` (rules: sorting, paging, filter semantics, who may call it).
- Every parameter with type, `required`, constraints (`minimum`, `maximum`, `maxLength`, `enum`, `pattern`) and an example.
- Request and response bodies as named `components/schemas` (no inline anonymous objects), with `required` fields and **examples**.
- Every response the endpoint can return: `200/201/204` and each of `400`, `403`, `404`, `409`, `500` that applies, all errors as `ProblemDetail`.
- Pagination, sort order and empty-result behaviour written down in the description.

### 0.3 Spec style
- JSON fields in `camelCase`; paths in `kebab-case` plural nouns (`/saved-filters`, `/aes/{aeId}`).
- Ids are strings in the API.
- Dates/times: `string` + `format: date-time` (UTC, ISO-8601).
- Reuse shared schemas (`PagedResponse`, `ProblemDetail`, `Person`) instead of copying them.
- Enums listed in the spec; the Java enum must have exactly the same values.

### 0.4 Changing an existing endpoint
| Change | Breaking? | What to do |
|---|---|---|
| Add an optional field to a response | No | Spec PR, bump minor `info.version` |
| Add an optional request field / query param | No | Same |
| Add a new endpoint | No | Same |
| Rename/remove a field, change a type, make a field required, change a status code or URL | **Yes** | Agree with the UI team first; ship UI and API together, or add a new version (`/api/v2/...`) and deprecate the old one (`deprecated: true`) |

### 0.5 Keeping code and spec in step
- **Spec is the source.** The Swagger annotations on controllers (4.1) describe the same thing for Swagger UI; if they ever disagree, the spec wins and the annotations get fixed.
- **Copy of the spec in the service:** keep `src/test/resources/openapi.yml` in `aur_map_api_svc`, updated in the same PR as the code change.
- **Contract tests:** every controller test validates the real request/response against that file, so a field renamed in code but not in the spec fails the build:

```xml
<dependency>
    <groupId>com.atlassian.oai</groupId>
    <artifactId>swagger-request-validator-mockmvc</artifactId>
    <version>2.44.1</version>
    <scope>test</scope>
</dependency>
```
```java
@WebMvcTest(AttributeTaggingController.class)
class AttributeTaggingControllerTest {

    private static final OpenApiValidationMatchers SPEC =
            OpenApiValidationMatchers.openApi().isValid("openapi.yml");

    @Autowired MockMvc mvc;
    @MockBean FilterService filterService;
    @MockBean AEService aeService;

    @Test
    void getFilters_validScreen_matchesSpec() throws Exception {
        when(filterService.getFilters(any())).thenReturn(new FilterResponse("attributeTagging", List.of()));

        mvc.perform(get("/api/v1/attribute-tagging/filters"))
           .andExpect(status().isOk())
           .andExpect(SPEC);                       // fails if the JSON doesn't match openapi.yml
    }
}
```

### 0.6 Working with Copilot: understand → plan → approve → build

Developers describe **business logic**, not code. Copilot (driven by `.github/copilot-instructions.md`) always works in this order:

| Step | Copilot | Developer |
|---|---|---|
| 1. Understanding | Restates the rules as a numbered list, names the spec operation(s), inputs, outputs, error cases, and lists questions/assumptions. If it isn't in the spec, stops and proposes the spec change. | Checks it understood the business correctly; answers the questions. |
| 2. Plan | Lists files to create/change by package, queries, DB scripts, and the 3–5 tests it will write. No code. | Reviews; asks for changes or replies **"go"**. |
| 3. Build | Writes the agreed code and tests, then a summary with a curl to try. | Runs it, reviews against the Definition of Done (15), raises the PR. |

Setup in `aur_map_api_svc`:
- `.github/copilot-instructions.md` ← copy of `copilot/backend-copilot-instructions.md` (Copilot reads it automatically).
- `.github/prompts/feature.prompt.md` ← copy of `copilot/prompts/feature.prompt.md` (a fill-in-the-blanks request; in VS Code run it with `/feature`, in IntelliJ paste it into Copilot Chat).
- `docs/plan.md` ← this file; `api/openapi.yml` and `src/test/resources/openapi.yml` ← the spec.

Use **agent mode** (or attach `#file:docs/plan.md #file:api/openapi.yml` in chat) so Copilot can read the plan and the spec.

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
├── web             REST controllers, one per screen / resource
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
├── external        clients for other systems (REST/SOAP calls out); one interface + impl per system
├── config          @Configuration classes and AppProperties (app.yml binding)
└── util            small static helpers with no Spring state
```

Rules:
- Packages by **layer**, as above (controllers live in `web`). `filter` is **only** for servlet filters (`OncePerRequestFilter`); screen dropdown code lives in `enums`. No `common`, `misc` or `helper` dumping grounds.
- `util` classes are `final` with a private constructor and only `static` methods. If a helper needs a Spring bean, it is a service, not a util.

## 4. Layer by layer

### 4.1 Controller
- One controller per screen/resource in package `web`, e.g. `AttributeTaggingController`, mapped to `/api/v1/<screen>`.
- Method name = the spec's `operationId`; Swagger annotations (`@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`) copy the spec's summary, descriptions and responses.
- Logs `operationStarted` / `operationCompleted` like services do (4.8), using an injected `LoggerService`.
- **Thin.** Each method: take the request, call **one** service method, return the DTO. No business logic, no repository calls, no `try/catch` (the `GlobalExceptionHandler` does errors).
- Method names, paths, parameters and response types follow `api/openapi.yml` exactly.
- Validate input with Bean Validation (`@Valid`, `@NotBlank`, `@Min`, `@Max`) and `@Validated` on the class.
- Return the DTO directly (Spring sends 200). Use `ResponseEntity` only when the status or headers change (201, 204).

```java
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/attribute-tagging", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Attribute Tagging", description = "Find Auditable Entities and tag attributes to them")
public class AttributeTaggingController {

    private final FilterService filterService;
    private final AEService aeService;
    private final LoggerService logger;

    @Operation(summary = "Get filter dropdowns")
    @ApiResponse(responseCode = "200", description = "Dropdowns and their values")
    @GetMapping("/filters")
    public FilterResponse getFilters() {
        final String operation = "AttributeTaggingController -> getFilters";
        logger.operationStarted(operation);
        FilterResponse response = filterService.getFilters(Screen.ATTRIBUTE_TAGGING.getValue());
        logger.operationCompleted(operation);
        return response;
    }

    @Operation(summary = "Search Auditable Entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of AE cards"),
            @ApiResponse(responseCode = "400", description = "Unknown filter or bad paging",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/aes/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PagedResponse<AEResponse> searchAEs(
            @Valid @RequestBody FilterRequest request,
            @Parameter(description = "Page number, starts at 1") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "Items per page, 1 to 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {   // → RequestDetailsHolder later
        final String operation = "AttributeTaggingController -> searchAEs";
        logger.operationStarted(operation);
        PagedResponse<AEResponse> response = aeService.search(request, page, pageSize, userId);
        logger.operationCompleted(operation);
        return response;
    }
}
```

The full version with every Swagger annotation is `AttributeTaggingController` in `code/` (PR #12).

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
  4. **`Specification`** for searches where the user picks any combination of filters (built inside the service, see 11.2).
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

### 4.8 Logging: `LoggerService`

All logging goes through our own `LoggerService` (package `logger`, impl in `logger.impl`). Only `LoggerServiceImpl` uses SLF4J (`@Slf4j`) directly.

**Levels:**
| Method | Level | Use for |
|---|---|---|
| `operationStarted(op)` | INFO | First line of every public controller and service method |
| `operationCompleted(op)` | INFO | Last line before every `return` |
| `info(op, msg)` | INFO | Important business events only (saved, lock taken/released) |
| `debug(op, msg)` | DEBUG | Everything else: parameters, counts, keys, branches taken |
| `operationFailed(op, reason)` | ERROR | Expected failures (4xx), no stack trace; called by `GlobalExceptionHandler` |
| `error(op, ex)` | ERROR | Unexpected exceptions (5xx) with stack trace; called by `GlobalExceptionHandler` |

```java
public interface LoggerService {
    void operationStarted(String operation);
    void operationCompleted(String operation);
    void info(String operation, String message);
    void debug(String operation, String message);
    void operationFailed(String operation, String reason);
    void error(String operation, Throwable ex);
}

@Slf4j
@Service
public class LoggerServiceImpl implements LoggerService {

    @Override public void operationStarted(String operation)   { log.info("{} : operation started", operation); }
    @Override public void operationCompleted(String operation) { log.info("{} : operation completed", operation); }
    @Override public void info(String operation, String message)  { log.info("{} : {}", operation, message); }
    @Override public void debug(String operation, String message) { log.debug("{} : {}", operation, message); }
    @Override public void operationFailed(String operation, String reason) {
        log.error("{} : operation failed : {}", operation, reason);
    }
    @Override public void error(String operation, Throwable ex) {
        log.error("{} : operation failed with exception", operation, ex);   // last arg → stack trace
    }
}
```

**Every public method looks like this:**
```java
@Override
@Transactional(readOnly = true)
public PagedResponse<AEResponse> search(FilterRequest request, int page, int pageSize, String currentUser) {
    final String operation = "AEService -> search";
    logger.operationStarted(operation);

    ...                                                                    // the work
    logger.debug(operation, "found " + result.getTotalElements() + " AEs");

    logger.operationCompleted(operation);
    return PagedResponse.of(result, items);
}
```

Rules:
- Operation name: `"<Class without Impl> -> <method>"`, in a `final String operation` at the top of the method.
- Services get `logger` from `AbstractBaseService`; controllers, filters and `GlobalExceptionHandler` inject `LoggerService`.
- **No `try/catch` just to log.** When an exception is thrown, `operationCompleted` is skipped and `GlobalExceptionHandler` logs the failure once (`operationFailed` for `MapException`, `error` for anything else). The log reads *started → failed*.
- Never log passwords, tokens, full request bodies, or personal data beyond the user id.
- Levels per environment in `application-<env>.yml`: `com.ss.corp.map.api: DEBUG` locally and in dev, `INFO` in QA and prod.

**Later: request context.** When `RequestContextFilter` (package `filter`) and `RequestDetailsHolder` (package `context`, thread-local with `userId` + `correlationId`) are added, `LoggerServiceImpl` prefixes every line with `[userId] [correlationId]`; nothing else changes. The filter must clear the thread-local in a `finally` block (threads are pooled).

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
- Current user comes from the `X-User-Id` header for now, later from `RequestDetailsHolder` (SSO); never from the request body.

## 7. Configuration and environments

- `application.yml` holds defaults; `application-local.yml`, `application-dev.yml`, `application-qa.yml`, `application-prod.yml` hold what differs. Pick with `SPRING_PROFILES_ACTIVE`.
- App settings live under `app:` and are read only through `AppProperties` (4.2.1). No `@Value` in services.
- **No secrets in git** (DB passwords, keys, tokens). Locally: environment variables or an untracked `application-local.yml`. Deployed (ECS): environment variables / secrets from the task definition (AWS Secrets Manager or SSM).
- `spring.jpa.hibernate.ddl-auto: validate` everywhere except local (never `update`/`create` against shared databases).
- `spring.jpa.open-in-view: false`.

## 8. Database changes

- Every schema change is a SQL script in the repo's `database/` folder, named `V<yyyyMMddHHmm>__<what>.sql` (e.g. `V202611221030__add_ae_lock.sql`), in the **same PR** as the entity change.
- Scripts are additive and re-runnable where possible (`IF NOT EXISTS`); never edit a script that already ran in a shared environment, add a new one.
- Add an index for every new column you filter or sort on; note it in the PR.
- Table and column names: `snake_case`, schema `aur_map_app`.

## 9. Security basics

- Validate every input (Bean Validation on DTOs and params, spec constraints mirrored in code).
- Never build SQL by concatenating input; use derived queries, JPQL parameters or `Specification`. Native queries use `:params` only.
- The current user comes from the request context/SSO, never from the body or a query parameter.
- Errors never expose stack traces, SQL or class names (4.7).
- Authorisation rules (owner-only, lock holder only) are checked in the service and return 403/409 as the spec says.
- CORS: only the UI's origins, configured once in `config`.
- Run the SonarQube for IDE analysis on changed files before the PR; fix blockers and criticals.

## 10. Performance

- Every list endpoint is paged (6); never return an unbounded list of entities.
- Read methods are `@Transactional(readOnly = true)`.
- Avoid N+1 queries: fetch what a page needs in one or two queries (`IN (...)`, `EXISTS`), never a query per row in a loop.
- Use projections / `select distinct x` for dropdown values instead of loading whole entities.
- Data that is the same for every user (filters, attribute lists) is a caching candidate: `@Cacheable` once the cache infra exists, no code changes elsewhere.

## 11. What is built so far (Attribute Tagging, APIs 1 and 2)

Code lives in `code/src/main/java/com/ss/corp/map/api/` (PR #11 merged, PR #12 open).

### 11.1 Tables

| Entity | Table | Key fields |
|---|---|---|
| `AuditableEntity` | `aur_map_app.auditable_entity` | `aeId` (unique business key), `aeName`, `aeDesc`, `aeStatus`, `aeOwner text[]`, `businessArea`, `division`, `modifiedOn`, `modifiedBy` |
| `AuditableEntityAttribute` | `aur_map_app.auditable_entity_attributes` | `aeId`, `attrType`, `attrId`, `attrName`, `attrComment`, `attrStatus`; unique `(ae_id, attr_type, attr_id)` |

All 11 attribute types share the one attributes table, told apart by `attr_type` (`est`, `risk`, `legalEntity`, `regulation`, `thirdParty`, `application`, `model`, `archerRiskUnit`, `costCenter`, `location`, `product`). DB fixes these entities expect are in `code/README.md`.

### 11.2 Screen filters: one enum per screen

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
1. Paging limits are checked in the controller (`@Min`/`@Max`).
2. `Screen.fromValue(request.screen())`; reject keys not in that screen's enum.
3. Build a `Specification` in private methods of the service (no separate builder class):
   - always `aeStatus = true`
   - AE column keys → `root.get(field).in(values)` (`businessDivision` maps to field `division`)
   - `aeOwner` → OR of `array_position(ae_owner, owner) IS NOT NULL` via `cb.function(...)` (works on any Hibernate 6)
   - attribute keys → `EXISTS` subquery on `AuditableEntityAttribute` (same `aeId`, `attrType = key`, `attrName in values`, `attrStatus = true`)
4. `aeRepository.findAll(spec, PageRequest.of(page - 1, pageSize, Sort.by("aeName", "aeId")))`.
5. Map each entity with `AEResponse.from(ae, currentUser)`; return `PagedResponse.of(result, items)` (`result` is the Spring `Page`).

### 11.3 Open items

- Spec: add `screen` to `FilterRequest`/`FilterResponse` in `api/openapi.yml`, and settle the screen name (`attribute-tagging` in paths vs `attributeTagging` in `Screen`). **Do this first (section 0).**
- Add `src/test/resources/openapi.yml` and the contract tests (0.5) for APIs 1 and 2.
- `logger` package and started/completed logging in the existing controller and services (4.8).
- `MapException` family + `GlobalExceptionHandler` (4.7); `ValidationException` to extend `MapException`.
- Current user comes from the `X-User-Id` header for now; move to `RequestDetailsHolder` when the request-context filter exists.
- `recommendationCount` and `lockedBy` on `AEResponse` are placeholders (0 / null) until those features exist.
- `AbstractBaseService` + `AppProperties` (`app.paging.max-page-size`).

## 12. Next APIs (from the spec), in suggested order

| # | Endpoint | Notes |
|---|---|---|
| 3 | `GET /aes/{aeId}` | AE + 11 attribute-type sections; `isOwner`, `lock`, `canEdit`. `NotFoundException` if missing/inactive. |
| 4 | `POST`/`DELETE /aes/{aeId}/lock` | Take/renew/release the edit lock; 409 if someone else holds it. |
| 5 | `PUT /aes/{aeId}/attributes` | Save the full tagged list; owner + lock holder only (403/409). `@Transactional`. |
| 6 | `GET /attribute-types/{type}/attributes` and `.../{attributeId}/children` | All Available list and children. Cache later. |
| 7 | `GET`/`POST /saved-filters`, `DELETE /saved-filters/{id}` | Max 3 per user; stores a `FilterRequest`. |

Each one = controller method + service interface method + impl + repository methods + DTO(s) with `from`/`toEntity`, following sections 0 and 4–6, **after** the endpoint's spec is approved.

## 13. Testing

**Few and focused: 3 to 5 tests per feature**, enough to prove it works as the business expects. Not 10–15.

| Kind | How many | What |
|---|---|---|
| Service unit test (JUnit 5 + Mockito, repositories mocked) | 2–3 | The happy path + the 1–2 most important rules or errors (e.g. not owner → 403, missing → 404) |
| Controller contract test (`@WebMvcTest` + spec validator, 0.5) | 1–2 | Happy path JSON validated against `openapi.yml`; one 4xx if the endpoint has input rules |
| Repository test (`@DataJpaTest`, Testcontainers PostgreSQL) | 0–1 | Only for a new native query or Specification (H2 can't do our arrays) |

- Names: `methodName_condition_expectedResult`; Arrange / Act / Assert; one behaviour per test.
- Don't test getters, records, enums or Spring wiring.

## 14. Git and PR workflow

- Branch from `Develop`: `feature/<story-id>-<short-name>`, `bugfix/<story-id>-<short-name>`.
- Spec change first (its own small PR in the docs repo, or the first commit of the PR), then code.
- Commit messages: imperative, what and why (`Add AE search API`, `Fix owner filter for multiple owners`).
- PR description: link to the story, the spec operation(s) implemented, screenshots of Swagger/Postman, DB scripts added.
- At least one reviewer; reviewer checks the Definition of Done below. Squash-merge into `Develop`.
- Never commit `.idea/`, `target/`, local yml with secrets.

## 15. Definition of Done (checklist before a PR)

- [ ] The endpoint was in `api/openapi.yml` and approved **before** coding (section 0)
- [ ] Endpoint, fields, status codes and examples match the spec; `src/test/resources/openapi.yml` updated
- [ ] Contract test per response code passes (0.5); breaking changes agreed with the UI team (0.4)
- [ ] Controller is thin: one service call, no logic, no repository
- [ ] Controller depends on the service **interface**
- [ ] No entity in any controller signature or response; conversion via `Dto.from(...)` / `toEntity()`
- [ ] Repository uses derived names where readable, `@Query` otherwise, native SQL only for PostgreSQL features
- [ ] `@Transactional(readOnly = true)` on reads, `@Transactional` on writes
- [ ] Bad input → `ValidationException`/Bean Validation (400); missing → 404; no `try/catch` in controllers
- [ ] Service impl extends `AbstractBaseService`; uses its `logger` and `appProperties` (no `@Value`, no `@Slf4j`)
- [ ] Every public controller/service method logs `operationStarted` first and `operationCompleted` last; details at `debug`
- [ ] Failures throw a `MapException` subclass; no logging `try/catch` in controllers/services
- [ ] Constructor injection, no field `@Autowired`, no `@Data` on entities
- [ ] No new dependency without a reason; no unused code, flags or parameters (KISS/YAGNI)
- [ ] 3–5 tests: service happy path + key rules, one contract test (13)
- [ ] DB script in `database/` for any schema change; indexes for new filters (8)
- [ ] No secrets in code or yml; SonarQube for IDE clean on changed files (9)
