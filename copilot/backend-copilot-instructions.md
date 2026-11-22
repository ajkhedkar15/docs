# Copilot instructions: mAP backend (`aur_map_api_svc`)

> Copy this file into the service repo as `.github/copilot-instructions.md`. Copilot Chat reads it on every request.
> Also keep `docs/plan.md` (the full build plan) and the OpenAPI spec in the repo so Copilot can read them.

You are helping a developer on the myAuditPlan (mAP) backend: Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL (schema `aur_map_app`), Lombok, base package `com.ss.corp.map.api`.

The detailed rules are in **`docs/plan.md`**, and the API contract is in **`api/openapi.yml`** (copy also at `src/test/resources/openapi.yml`). Read both before you answer a feature request. If something here and `plan.md` disagree, `plan.md` wins. If either disagrees with the spec, the spec wins.

---

## How you work: understand → plan → wait → build → test

The developer describes the **business logic** they want ("a user can lock an AE for 30 minutes so nobody else edits it…"). You never start by writing code. Always follow these steps in order.

### Step 1: Say what you understood
Reply with a short **Understanding** section:
- The business rule(s) in your own words, as a numbered list.
- Which endpoint(s) in `api/openapi.yml` this is, by `operationId`, method and path.
- Inputs, outputs and every error case (status + when).
- **Questions / assumptions**: anything unclear, missing from the spec, or contradicting it. If the endpoint or a field is **not in the spec**, say so and stop: the spec has to be updated and approved first (plan.md section 0). Offer the YAML change for the spec instead of code.

### Step 2: Propose the plan
Reply with a **Plan** section, still no code:
- Files to **create** and files to **change**, with full package paths, grouped by layer (`web`, `service` + `service.impl`, `repository`, `entity`, `model`, `enums`, `exception`, `database/` script).
- For each file: one line on what goes in it (method signatures are welcome).
- Queries you'll add (derived name, `@Query` JPQL, or native) and any DB script or index.
- The tests you'll write (step 4): their names and what each checks.
- Anything risky (breaking change, performance, data migration).

End with: **"Reply 'go' to build this, or tell me what to change."**

### Step 3: Wait
Do **not** write code until the developer agrees ("go", "yes", "approved", or a changed plan you then confirm). If they change something, update the plan and ask again. Keep the plan short: it's for a quick review, not documentation.

### Step 4: Build
Write the code exactly as agreed, following `plan.md`:
- Code for every file in the plan, complete (imports included), one file per code block with its path as the heading.
- Then the tests (below).
- Then a short **Summary**: what was built, anything you deviated from in the plan and why, and the manual check to run (curl or Swagger UI request).

Don't add features, fields, endpoints, flags or "nice to haves" that weren't in the agreed plan.

---

## Tests: few and focused

We don't want 10–15 tests per feature. Write **3 to 5** that prove the feature works:

| Kind | How many | What |
|---|---|---|
| Service unit test (JUnit 5 + Mockito) | 2–3 | The happy path, plus the 1–2 most important business rules or error cases (e.g. "not owner → ForbiddenException") |
| Controller contract test (`@WebMvcTest` + spec validator) | 1–2 | One happy path whose JSON is validated against `openapi.yml`; one 400/4xx if the endpoint has input rules |
| Repository test (`@DataJpaTest`, Testcontainers PostgreSQL) | 0–1 | Only if you wrote a new native query or Specification |

- Names: `methodName_condition_expectedResult`.
- Arrange / Act / Assert, one behaviour per test, no logic in tests.
- Mock repositories in service tests; never hit a real DB there.
- No tests for getters, records, enums, or Spring wiring.

---

## Code rules (summary of plan.md; follow plan.md for details)

**Layers**
- `web`: thin controller, method name = spec `operationId`, Swagger annotations (`@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`) copied from the spec, Bean Validation (`@Valid`, `@Min`, `@Max`) with `@Validated` on the class. One service call per method. No logic, no repositories, no `try/catch`.
- `service`: an interface; `service.impl`: `XxxServiceImpl extends AbstractBaseService implements XxxService`, `@Service`, `@RequiredArgsConstructor`. Business rules and `@Transactional` (`readOnly = true` on reads) live here.
- `repository`: interfaces extending `JpaRepository` (+ `JpaSpecificationExecutor` for dynamic search). Prefer derived method names; `@Query` JPQL when needed; native SQL only for PostgreSQL features (arrays, `unnest`), always with `:params`.
- `entity`: `@Entity`, schema `aur_map_app`, Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, never `@Data`. Never returned from a controller.
- `model`: DTOs as Java records matching the spec schemas field by field. **No mapper classes**: conversion on the DTO with `static XxxResponse from(Entity ...)` and `Entity toEntity()`.
- `enums`: enums with a `key`/`value` used by the API and `fromValue(String)` that throws `ValidationException` for unknown values.
- `exception`: throw `MapException` subclasses (`ValidationException` 400, `ForbiddenException` 403, `NotFoundException` 404, `ConflictException` 409). `GlobalExceptionHandler` turns them into `ProblemDetail`.

**Logging: every public controller and service method**
```java
final String operation = "AEService -> search";
logger.operationStarted(operation);          // INFO, first line
...
logger.debug(operation, "found " + count);   // details at DEBUG
logger.operationCompleted(operation);        // INFO, last line before return
return result;
```
- `info(op, msg)` only for important business events; everything else `debug`.
- Errors are logged once by `GlobalExceptionHandler` (ERROR). No `try/catch` just to log.
- Services get `logger` and `appProperties` from `AbstractBaseService`; controllers inject `LoggerService`. No `@Slf4j` outside `LoggerServiceImpl`.

**General**
- KISS, SOLID, YAGNI: the simplest code that does what the spec and the agreed plan say.
- Constructor injection only (`@RequiredArgsConstructor` + `private final`).
- Settings from `AppProperties`, never `@Value` in services. No secrets in code or yml.
- Paging is 1-based in the API (`PageRequest.of(page - 1, …)`), stable sort with the id as tiebreaker.
- Imports: Spring's `org.springframework.data.domain.Page`, never `org.hibernate.query.Page`.
- Use only dependencies already in `pom.xml`; if one is needed, say so in the plan.

---

## Example

**Developer:** "When a user opens an AE, show its details. Only active AEs. Tell the UI if the user owns it."

**You (step 1 + 2):**
> **Understanding**
> 1. Return one AE's details by `aeId`: `getAE` → `GET /api/v1/attribute-tagging/aes/{aeId}`.
> 2. Only active AEs (`ae_status = true`); inactive or missing → 404.
> 3. `isOwner` = the current user is in `ae_owner`.
>
> Questions: the spec's `AEDetailResponse` also has `lock` and `canEdit`. Lock isn't built yet. Return `lock = null`, `canEdit = isOwner` for now?
>
> **Plan**
> - `model/AEDetailResponse.java`: record per spec + `static from(AuditableEntity, List<AuditableEntityAttribute>, String user)`
> - `service/AEService.java`: add `AEDetailResponse getAE(String aeId, String currentUser)`
> - `service/impl/AEServiceImpl.java`: implement; `NotFoundException` if missing
> - `repository/AERepository.java`: use existing `findByAeIdAndAeStatusTrue`
> - `repository/AuditableEntityAttributeRepository.java`: use existing `findByAeIdAndAttrStatusTrue`
> - `web/AttributeTaggingController.java`: add `getAE`
> - Tests: `getAE_activeAe_returnsDetailWithOwnerFlag`, `getAE_missingAe_throwsNotFound`, `getAE_contract_matchesSpec`
>
> Reply 'go' to build this, or tell me what to change.

**Developer:** "go, and yes to your assumption."

**You (step 4):** the code, the 3 tests, and the summary.
