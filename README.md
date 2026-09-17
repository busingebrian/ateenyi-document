# document

Training project: a small Spring Boot service for managing documents (save, update status, look up by id/DRN,
search). Standalone — no shared code or dependencies with any other `document` service.

## Stack

- Java 25, Gradle 9.7.0 (via wrapper)
- Spring Boot 4.1.0 (Web, Data JPA, Validation)
- H2 — file-based for normal runs, in-memory for tests
- Log4j2 (via Lombok `@Log4j2`)

## Running

```bash
./gradlew bootRun
```

The app starts on port `8080` under context path `/api/document`, backed by a file-based H2 database under
`./data/document` (override with the `DB_FILE_PATH` environment variable).

## Testing

```bash
./gradlew test
```

## Domain model

| Field       | Type                              | Notes                                                    |
|-------------|------------------------------------|-----------------------------------------------------------|
| `id`        | UUID                                | auto-generated                                             |
| `drn`       | String                              | generated: `<docType>-<6 random alphanumeric chars>`       |
| `docType`   | enum: `rt`, `tp`                    | 2-char code, both on the wire and in the DB                |
| `status`    | enum: `SUBMITTED`, `APPROVED`, `REJECTED` |                                                       |
| `content`   | String                              | max length 2000                                            |
| `createdAt` | timestamp                           | set once, on creation                                       |
| `modifiedAt`| timestamp                           | equals `createdAt` on creation; updated on every change    |

## API

Base path: `/api/document/document`

| Method | Path              | Description                                                        |
|--------|-------------------|----------------------------------------------------------------------|
| POST   | `/`               | Save a new document. Requires `docType`, `status`, `content`.       |
| PATCH  | `/status`         | Update a document's status. Requires `id`, `status`.                |
| GET    | `/{id}`           | Get a document by id.                                                |
| GET    | `/drn/{drn}`      | Get a document by its exact DRN.                                    |
| GET    | `/search?drn=`    | Search documents whose DRN contains the given value. Paged (`page`, `size`); always ordered by `modifiedAt` desc, then `docType` asc. |

### Validation errors

A validation failure returns HTTP 400 with every violation at once:

```json
{
  "errors": [
    { "code": "invalid.docType", "message": "docType is required" },
    { "code": "invalid.content", "message": "content is required" }
  ]
}
```

A lookup for a document that doesn't exist returns HTTP 404.

## Docker

```bash
./gradlew build
docker build -t document .
docker run -p 8080:8080 -v $(pwd)/data:/app/data document
```
