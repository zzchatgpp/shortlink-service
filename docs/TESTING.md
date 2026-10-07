# Testing — Step 11

Verified with Java 17 using `./mvnw package`.

**Result: 128 tests passed; 0 failures, 0 errors, 0 skipped.**

Parameterized invocations count individually. Repeated checks within a test do not.

| Test class | Tests | Failures | Errors |
| --- | ---: | ---: | ---: |
| RedisLinkCacheTest | 16 | 0 | 0 |
| AnalyticsApiTest | 10 | 0 | 0 |
| CreateLinkApiTest | 22 | 0 | 0 |
| RedirectApiTest | 16 | 0 | 0 |
| OpenApiDocumentationTest | 6 | 0 | 0 |
| ShortLinkRepositoryTest | 6 | 0 | 0 |
| AnalyticsServiceTest | 1 | 0 | 0 |
| CachedRedirectTest | 5 | 0 | 0 |
| ConcurrentClickTest | 1 | 0 | 0 |
| CreationServiceTest | 12 | 0 | 0 |
| ShortLinkServiceTest | 3 | 0 | 0 |
| Base62Test | 30 | 0 | 0 |

## Verified behavior

- Base62: known encodings, round trips, case sensitivity, canonical input and overflow.
- Creation: persisted ID drives code, optional expiry, public origin, validation and database errors.
- Redirects: HTTP 302/404/410, no-store headers, preserved URLs and expiry boundaries.
- Click tracking: GET counting, HEAD exclusion, latest timestamp and expiry-aware atomic updates.
- Concurrency: 80 redirects across 8 workers produce exactly 80 clicks on H2.
- Redis logic: TTL bounds, serialization, corrupt/expired entries, cache hits/misses and outage fallback.
- Analytics: current stored counts, timestamps, expired-link stats, and no click increments on reads.
- OpenAPI: endpoint paths, status codes, schemas, examples, headers and Swagger UI serving.

## Commands

```bash
./mvnw test
./mvnw -Dtest=CreationServiceTest test
./mvnw package
```

## Test dependencies

JUnit and Mockito come from Spring Boot Test. H2 is test-only and is not packaged
in the executable application. The test profile disables Redis operations.
Cache unit tests mock the Redis client; they do not connect to a Redis server.
Mockito uses the subclass mock maker, so tests do not need JVM agent attachment.

## Remaining verification

Live MySQL migration/schema validation, Redis connectivity and TTL behavior,
Docker Compose startup, and deployed endpoint checks remain to be completed.
H2 and mock-based checks do not establish production database/cache compatibility.
Docker and GitHub Actions definitions are prepared. No Docker daemon is available
in the authoring environment, so the image build and live-stack smoke test have
not run here. GitHub repository creation/upload is pending; Railway deployment
follows in Step 12.
