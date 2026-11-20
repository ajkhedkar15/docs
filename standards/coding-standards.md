# myAuditPlan coding standards

These apply to every myAuditPlan repo: the Spring Boot backend, the React UI, and the API spec. Copilot follows the same rules through `.github/copilot-instructions.md` in each repo.

**The three rules that matter most**
1. **The API spec (`api/openapi.yml`) is the contract.** Change it first, in the same PR as the code.
2. **Keep layers clean.** Controllers are thin, services hold the rules, repositories only touch data, and the UI never decides permissions.
3. **Every change is tested and reviewed.** Small PRs, green checks, one approval.

---

## 1. Git and pull requests

### Branches
- `main` is always deployable. Nobody pushes to it directly.
- Branch names: `<type>/<ticket>-<short-description>`, for example `feature/MAP-123-edit-lock`, `fix/MAP-140-filter-or-logic`, `chore/MAP-150-upgrade-spring`.
- Types: `feature`, `fix`, `chore`, `refactor`, `docs`, `test`.

### Commits (Conventional Commits)
```
feat(tagging): release the edit lock when switching AE
fix(filters): OR values within one filter
refactor(service): move recommendation rules into RecommendationService
```
- Format: `<type>(<area>): <what changed>`, in the imperative, at most 72 characters.
- One logical change per commit. No "WIP" or "fix" commits on `main` (squash-merge).

### Pull requests
- **Small:** aim for under 400 changed lines. Split big work into steps.
- **The description says** what changed, why, how it was tested, and links the ticket. Use the PR template.
- **Before asking for review:** it builds, tests pass, lint is clean, and there's no commented-out code, stray `TODO` or debug logging.
- **To merge:** all checks green and **one approval**. Squash-merge, then delete the branch.
- **API changes:** the spec change is in the same PR, and the reviewer checks it in Swagger.

### Review checklist
- [ ] Does it do what the ticket asks, and nothing more?
- [ ] Does the code match the spec (paths, fields, status codes)?
- [ ] Are the layers respected (no logic in controllers, no queries in services)?
- [ ] Are permissions enforced on the server (owner, lock), not only hidden in the UI?
- [ ] Are there tests for the new behaviour and the error cases?
- [ ] Are names clear, and is there no duplicated logic?
- [ ] No secrets, personal data or stack traces in code, logs or responses?

---

## 2. API spec

- One file: `api/openapi.yml`. One **tag** per screen (`AttributeTagging`), paths under `/<screen>/…`.
- Screen-specific schema names start with the screen (`AttributeTaggingFilterType`). Never reuse an existing schema name.
- JSON fields in `camelCase`; paths in `kebab-case`; enum values in `camelCase` (for types) or `UPPER_CASE` (for statuses).
- Every operation has an `operationId`, a `summary`, an example, and its 4xx responses.
- Errors are ProblemDetail (`status`, `title`, `detail`, `errors[]`).
- Lists are paged: `page` from 1, `pageSize` with a maximum, response `{ page, pageSize, totalRecords, totalPages, items }`. Empty list = 200.
- Never put the user in a query parameter or body. It comes from the header (now) or the token (later).
- Run `npx @redocly/cli lint map` before pushing. The GitHub check runs it too.

---

## 3. Java / Spring Boot backend

### Project setup
- Java 17+ (prefer 21), Spring Boot 3, Maven.
- **Formatting is automatic:** Spotless with google-java-format (or palantir-java-format), run on build. Don't argue about formatting in reviews.
- **Static checks:** SonarLint in the IDE; Sonar (or Checkstyle + SpotBugs) in CI. New code: no blocker or critical issues.
- Controller interfaces and DTOs are generated from the spec (`com.map.api`, `com.map.dto`). Never edit them.

### Packages (by layer)
```
com.map
├── api          generated interfaces          ├── entity      JPA entities
├── dto          generated DTOs                ├── filter      filter engine
├── web          controllers, exception handler├── security    CurrentUser
├── service      business rules, transactions  ├── exception   domain exceptions
├── repository   data access                   └── config      Spring configuration
```
- **Dependencies only point down:** `web` → `service` → `repository`. A repository never calls a service; a service never returns an entity to a controller.
- A new screen adds classes to these packages. There are no per-screen packages.

### Naming
| Kind | Rule | Example |
|---|---|---|
| Classes | PascalCase nouns; screen prefix when screen-specific | `AttributeTaggingController`, `LockService` |
| Methods | camelCase verbs | `acquireOrRenew`, `findRequiredFields` |
| Constants | UPPER_SNAKE | `LOCK_TIMEOUT` |
| Booleans | `is…`, `has…`, `can…` | `isOwner`, `hasChildren`, `canEdit` |
| Data loaded from the foundational schema | `Foundational…` prefix | `FoundationalAuditableEntityRow` |
| MAP's own data | no prefix | `AuditableEntity`, `AuditableEntityRepository` |
| Avoid | `Manager`, `Helper`, `Util`, `Impl` for single implementations, abbreviations | |

### Code
- **Constructor injection only** (`@RequiredArgsConstructor` with `private final` fields). No field `@Autowired`.
- **Records** for immutable data (rows, results, value objects).
- **No nulls for collections:** return `List.of()`, never `null`. Use `Optional` only as a return type, never for fields or parameters.
- **Methods are short** (aim for under 30 lines) and do one thing. Name the thing instead of writing a comment.
- **No magic values:** use enums and constants (`AttributeType`, `LockService.TIMEOUT = Duration.ofMinutes(15)`).
- **Configuration** goes in `application.yml` and is read with `@ConfigurationProperties`. No hard-coded URLs, timeouts or limits.
- **Comments explain why,** not what. Public service methods get a one-line Javadoc when the name isn't enough.
- **Lombok:** only `@RequiredArgsConstructor`, `@Getter`, `@Slf4j`, `@Builder`. No `@Data` on entities.

### Controllers (`web`)
- Implement the generated interface. Get the user from `CurrentUser`, call **one** service method, return.
- No `if`s, queries, mapping logic or `try/catch`.

### Services (`service`)
- All business rules and checks live here: ownership, the lock, validation, recommendations.
- `@Transactional` on the public method that starts the unit of work. `readOnly = true` for reads. Never call a `@Transactional` method from the same class and expect a new transaction.
- Throw domain exceptions (`NotOwnerException`, `AeLockedException`). Never return error codes or `null` to mean "not found".
- Map entities to DTOs here (MapStruct or a small mapper class). Controllers never see entities.

### Repositories (`repository`)
- Spring Data JPA for simple CRUD. `JdbcTemplate` for bulk loads, upserts and reporting queries.
- Select **only the columns you need**. Use projections or records for read-only queries.
- Filtering by multi-value data uses `EXISTS`, not joins. No `JOIN FETCH` with paging. No N+1: load related data for a page with `WHERE id IN (:ids)`.
- **Never build SQL by concatenating strings** with user input. Always use bind parameters.

### Errors
- One `@RestControllerAdvice` maps exceptions to ProblemDetail: 400 validation, 401 no user, 403 not owner, 404 not found, 409 lock or limit conflict, 500 anything else.
- 500 responses show a generic message and a reference id. The stack trace goes to the log, never to the client.

### Logging
- SLF4J (`@Slf4j`), with parameterised messages: `log.info("Lock taken: aeId={}, user={}", aeId, userId)`.
- **Levels:** `ERROR` needs action; `WARN` is unexpected but handled; `INFO` is business events (lock taken, update saved, load finished with counts); `DEBUG` is detail for troubleshooting.
- Put a request id in the MDC for every request and include it in the log pattern.
- **Never log** personal data beyond user ids, tokens, full request bodies or stack traces at `INFO`.

### Security
- The user always comes from `CurrentUser`. Check ownership and the lock **in the service**, even if the UI hides the button.
- `dev-auth` (`X-User-Id`) is for development only. Production must not start with that profile.
- Validate every input (bean validation on generated DTOs plus business checks in the service).
- No secrets in code or `application.yml`. Use environment variables or the secret store.
- Keep dependencies up to date (Dependabot or Renovate). No known critical vulnerabilities on `main`.

### Performance
- Cache data that is the same for every user (filters, All Available) with `@Cacheable`, and evict when the data changes.
- Turn on response compression.
- Page every list. Check slow queries with `EXPLAIN ANALYZE` and add indexes for filter and join columns.

---

## 4. PostgreSQL

- **Every schema change is a Flyway migration** in `db/migration`: `V<n>__<description>.sql` for tables, `R__<name>.sql` for views (re-applied when changed). Never use `ddl-auto` outside local development. Never edit a migration that has already run; add a new one.
- **Names:** `snake_case`, tables singular (`auditable_entity`, `ae_attribute`), views start with `v_` (`v_foundational_auditable_entity`), indexes `ix_<table>_<columns>`, unique constraints `uq_<table>_<columns>`.
- Every table has a primary key. Timestamps are `timestamptz`. Statuses are `varchar` with a check constraint, or an enum table.
- **No hard deletes for business data:** use `status = 'INACTIVE'` and keep the history.
- Add `created_on`, `created_by`, `modified_on` and `modified_by` to tables users change.
- Index every column used in `WHERE`, `JOIN` or `ORDER BY` on large tables.
- The application user can't change the schema. Only migrations can.

---

## 5. React / TypeScript UI

### Project setup
- TypeScript `strict: true`. **ESLint** (typescript-eslint, react-hooks, jsx-a11y) and **Prettier**, run on save and in CI. No warnings on `main`.
- API types are generated from `api/openapi.yml` with `openapi-typescript`. Never hand-write API types.

### Folder structure
```
src/
├── api/        generated types, API client, query keys
├── auth/       current user (later JWT)
├── shared/     reusable components (FilterBar, chips, dialogs) and hooks
└── features/
    └── attribute-tagging/   pages, components, hooks for this screen
```
- One component per file. The file name matches the component: `AeCard.tsx`, `useAeSearch.ts`.
- Feature code can import from `shared` and `api`. `shared` never imports from `features`.

### Code
- **No `any`.** Use `unknown` and narrow, or the generated types.
- Function components and hooks only. Props are typed with a `type Props = { … }`.
- **Components stay small:** split when a component passes about 150 lines or does more than one job. Put logic in custom hooks (`useEditLock`, `useAeSearch`).
- **Server data** lives in TanStack Query, with keys from one factory. **UI state** lives in `useState` or `useReducer`. **Filter state** lives in the URL. Don't copy server data into local state.
- All HTTP goes through the API client, which adds the user header and parses ProblemDetail.
- **Permissions:** show or hide edit controls only from `canEdit`, `isOwner` and `lock` returned by the API. Never work them out in the UI.
- Constants and labels (for example the filter `type` → label map) live in one file per feature.
- No inline styles except for dynamic values. Use the project's styling approach consistently.

### UX and accessibility
- Every async view has loading, empty and error states.
- Long lists and large dropdowns are virtualized.
- Everything works with the keyboard. Inputs have labels, and icons-only buttons have `aria-label`.
- Show the API's error `detail` to the user. Map `errors[]` onto form fields.

---

## 6. Testing

| Level | Backend | UI |
|---|---|---|
| Unit | JUnit 5 + AssertJ + Mockito: services, mappers, rules | Vitest + React Testing Library: hooks, pure functions, components |
| Integration | `@SpringBootTest` with **Testcontainers PostgreSQL**: repositories, migrations, the load and recommendation SQL | MSW to mock the API |
| API slice | `@WebMvcTest`: controller wiring and error mapping | |
| End to end | | Playwright: the main flows (filter, open AE, edit, update) |

- **Every bug fix comes with a test** that would have caught it.
- Test names describe behaviour: `updateRejectsAttributeWithChildren`, `showsBannerWhenLockedByAnotherOwner`.
- Arrange / act / assert, one behaviour per test. No `Thread.sleep`; use a fixed `Clock` for time-based logic such as lock expiry.
- **Coverage target:** at least 80% of lines in `service` and `filter` (backend), and in hooks and `shared` (UI). Coverage is a guide, not the goal.
- Don't mock what you own when an in-memory or Testcontainers version is cheap.

---

## 7. CI (every PR)

| Repo | Checks |
|---|---|
| Backend | build, Spotless check, unit and integration tests, Sonar quality gate, dependency vulnerability scan |
| UI | type check (`tsc --noEmit`), ESLint, Prettier check, tests, build |
| docs (spec) | Redocly lint |

A PR can't be merged until all checks pass.
