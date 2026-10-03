# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew build                                                                  # Build the project
./gradlew test                                                                   # Run all tests
./gradlew test --tests "com.training.document.service.DocumentServiceTest"      # Run a single test class
./gradlew test --tests "*.DocumentServiceTest.save_generatesDrnAndSetsCreatedAtEqualToModifiedAt"  # Run one test method
./gradlew bootRun                                                                # Run locally (file-based H2 DB under ./data)
./gradlew clean build                                                           # Clean + build
```

**Stack:** Java 25 (Gradle toolchain; wrapper is Gradle 9.7.0), Spring Boot 4.1.0, H2 (file-based for `bootRun`/prod,
in-memory for tests), Spring Data JPA, Bean Validation, Log4j2 (via Lombok `@Log4j2`).

This is a standalone training project — it does not depend on, or share code with, any other `document` service in
this workspace. Context path is `/api/document`, port 8080.

## Jackson 3 (important gotcha)

Spring Boot 4.1.0 pulls in **Jackson 3**, whose classes live under the `tools.jackson.*` group/package instead of
`com.fasterxml.jackson.*` (jackson-annotations is the exception — it stayed at `com.fasterxml.jackson.annotation`).
Concretely:
- `ObjectMapper`, `JsonNode`, exception types (`DatabindException`, `InvalidFormatException`, etc.) → `tools.jackson.databind.*`
- The base exception type is `tools.jackson.core.JacksonException`, and `Reference.getFieldName()` was renamed to
  `getPropertyName()`.
- MockMvc autoconfiguration also moved: `@AutoConfigureMockMvc` is now
  `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (from the `spring-boot-starter-webmvc-test`
  dependency), not `org.springframework.boot.test.autoconfigure.web.servlet`.

Double-check these package paths against the actual jars in `~/.gradle/caches` before assuming a Jackson-2-era import
is correct — the compiler error is the fastest way to find the new location.

## Architecture Overview

Single-entity CRUD/search service, layered the standard Spring Boot way:

```
com.training.document/
├── controller/DocumentController   # REST endpoints, thin — delegates to DocumentService
├── service/
│   ├── DocumentService(.Impl)      # business logic, transaction boundaries
│   └── DrnGenerator                # generates the human-readable document reference number
├── entity/
│   ├── Document                    # the sole JPA entity
│   └── DocType / DocStatus         # enums
├── repository/
│   ├── DocumentRepository          # JpaRepository + JpaSpecificationExecutor
│   └── DocumentSpecifications      # Specification builders for dynamic search
├── dto/                            # request/response DTOs + DocumentMapper (manual, no MapStruct)
└── exception/                      # DocumentNotFoundException + GlobalExceptionHandler
```

### Document lifecycle

- **DRN (Document Reference Number):** format `<docType-code>-<6-digit zero-padded sequence number>`, e.g.
  `rt-000001`. Generated in `DrnGenerator` from a single DB sequence (`document_drn_seq`) shared across all
  `docType`s — the numeric part just keeps incrementing regardless of `docType`, it is not reset per type. The
  sequence is created lazily (`@PostConstruct`, `CREATE SEQUENCE IF NOT EXISTS`) under the schema configured by
  `spring.jpa.properties.hibernate.default_schema` (`DB_SCHEMA`, defaults to `document`), and read via
  `SELECT NEXTVAL('<schema>.document_drn_seq')`. The `NEXTVAL(...)` function form is used deliberately instead of
  H2's `NEXT VALUE FOR` syntax, because `NEXTVAL(...)` is the one form both H2 and PostgreSQL (the production DB —
  see `deployment/config/application.properties`) support identically. Because the numeric part is fixed-width,
  `DrnGenerator` throws `IllegalStateException` once the sequence passes 999,999 rather than silently emitting a
  7-digit DRN — there's no retry loop or uniqueness pre-check needed, since the sequence itself guarantees
  uniqueness.
- **DocType** constants are deliberately lowercase (`rt`, `tp`), not the Java-convention `UPPER_SNAKE_CASE` — this
  lets `@Enumerated(EnumType.STRING)` persist the code as-is, and Jackson's default enum handling serialize/deserialize
  it as-is, with no converter or custom `@JsonValue`/`@JsonCreator` needed.
- **Timestamps:** `createdAt`/`modifiedAt` are set in `Document.prePersist()` (both to `now()` on insert) and
  `Document.preUpdate()` (`modifiedAt` only). These are JPA lifecycle callbacks — `@PrePersist` fires synchronously
  when `persist()` is called, but `@PreUpdate` only fires at **flush** time (dirty checking). This matters:
  `DocumentServiceImpl.updateStatus()` calls `documentRepository.saveAndFlush(...)`, not `save(...)`, specifically so
  the flush (and thus `@PreUpdate`) happens *before* the response DTO is built — otherwise the returned `modifiedAt`
  would still show the stale pre-update value.
- **Status transitions** (`SUBMITTED` / `APPROVED` / `REJECTED`) are unconstrained — any status can move to any other
  status via the update-status endpoint. There is no state machine.

### Validation & error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) is the single source of the API's error contract:
- `MethodArgumentNotValidException` (bean-validation failures on `@Valid @RequestBody` DTOs) → HTTP 400,
  body is `ValidationErrors{errors: [{code, message}]}` collecting **every** field violation at once (not
  fail-fast), one `ValidationError` per violation. `code` is always `invalid.<fieldName>`.
- `jakarta.validation.ConstraintViolationException` (method-level validation, e.g. `@Validated` beans) → HTTP 400,
  same `ValidationErrors` shape. `code` is `invalid.<field>`, where `<field>` comes from the violation's property
  path (`PROPERTY` nodes, e.g. `items[0].name`), falling back to the parameter name for plain method parameters.
- `HttpMessageNotReadableException` (malformed JSON, or a value that doesn't fit an enum, e.g. `"docType":"xx"`) is
  also normalized into the same `ValidationErrors` shape (400) rather than bubbling into a generic 500 — it walks
  the Jackson `JacksonException` cause chain to recover the offending field name when possible.
- `DocumentNotFoundException` → HTTP 400 (deliberately not 404) with a single `ValidationError`-shaped body (
  `code: "not.found"`).

All of these code paths log at `info` level, per the logging requirement (validation failures and successful
modifications must be visible at info).

### Search

`GET /document/search?drn=&docCode=&viewId=&page=&size=` (query params) and `POST /document/search` (same fields
as a JSON body) both bind to `SearchDto` and call `DocumentService.searchByDrn(SearchDto)`. It uses
`DocumentSpecifications.drnContains`
(case-insensitive `LIKE %drn%`), AND-ed with exact-match `docCodeEquals` / `viewIdEquals` when those optional params
are non-blank, via `JpaSpecificationExecutor`, returning a Spring Data `Page<DocumentResponse>`. The sort is **fixed
server-side**
(`modifiedAt` desc, then `docType` asc) inside `DocumentServiceImpl.searchByDrn` — client-supplied `page`/`size` are
honored, but there is deliberately no client-controlled sort parameter, so the ordering guarantee can't be bypassed.

## Testing Patterns

- `DocumentServiceTest` (`@SpringBootTest` + `@Transactional`, auto-rollback per test) exercises the service layer
  directly against the real (in-memory H2) `DocumentRepository`.
- `DocumentControllerTest` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`) drives the HTTP layer
  with `MockMvc`.
- Both test classes autowire a `JdbcTemplate` to insert `document` rows directly with explicit `modified_at` values
  when a test needs deterministic ordering (e.g. search ordering/paging tests) — going through the service would
  always stamp "now", making tie-break ordering by `docType` unreliable to assert on with real clock timestamps.
- The test datasource URL (`src/test/resources/application.yml`) includes `INIT=CREATE SCHEMA IF NOT EXISTS document`
  even though it doesn't set `hibernate.default_schema` — `DrnGenerator` still resolves its schema-qualified sequence
  name to `document` (the `@Value` default), so the schema has to exist in the in-memory test DB for
  `DrnGenerator.createSequence()` to succeed on context startup.
