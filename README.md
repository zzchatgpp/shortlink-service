# Shortlink Service

A Java 17 / Spring Boot URL shortener with MySQL persistence, Redis routing
cache, optional expiry, HTTP 302 redirects and click analytics.

## Features

- Case-sensitive Base62 codes derived from unique database IDs.
- Validated HTTP/HTTPS destinations and optional future expiry dates.
- 302 redirects; clear 404 and 410 problem-details errors.
- Atomic click counting and UTC timestamps; HEAD requests do not count.
- Redis cache-aside lookups with bounded TTLs and database fallback.
- Database-backed per-link analytics, including retained stats for expired links.
- Swagger UI and a generated OpenAPI specification.
- JUnit, Mockito, H2 integration and concurrent-click tests.
- Multi-stage Docker build, non-root runtime and health checks.

## Run everything with Docker Compose

Requires Docker Desktop or Docker Engine with Compose v2.

```bash
cp .env.example .env
```

Edit .env and replace both example passwords. Then:

```bash
docker compose up --build -d --wait --wait-timeout 240
docker compose ps
```

The first build downloads images and dependencies and runs the test suite.
MySQL is initialized automatically; Flyway creates the application table.
The app waits for MySQL and Redis health checks before starting. MySQL data is
retained in the mysql_data volume. Redis is a disposable routing cache.

- Swagger: http://localhost:8080/swagger-ui/index.html
- Liveness: http://localhost:8080/health
- OpenAPI: http://localhost:8080/v3/api-docs

Only the app is published to localhost. MySQL and Redis are reachable internally
using the Compose service names. If port 8080 is already used, change APP_PORT
and APP_BASE_URL together in .env.

```bash
docker compose logs --tail=100 app
docker compose down
```

The normal down command preserves the database volume. .env is excluded from
Git and Docker build context. It is read by Compose; Spring Boot does not load
.env when running directly from IntelliJ.

## API

| Method | Path | Purpose | Main statuses |
| --- | --- | --- | --- |
| POST | /api/links | Create a short link | 201, 400, 415 |
| GET | /s/{code} | Redirect and count a visit | 302, 404, 410 |
| GET | /api/links/{code}/stats | Read current analytics | 200, 404 |

```bash
curl -i -X POST http://localhost:8080/api/links \
  -H 'Content-Type: application/json' \
  -d '{"originalUrl":"https://example.com/articles/java"}'
```

Use the returned shortCode (preserving case):

```bash
curl -i http://localhost:8080/s/1
curl http://localhost:8080/api/links/1/stats
```

Replace 1 with your actual code. Omit expiresAt or set it to null for no expiry.
Optional expiry uses ISO-8601 with a timezone, must be in the future, and cannot
exceed 9999-12-31T23:59:59Z. URLs must be absolute HTTP/HTTPS, without embedded
credentials, and at most 2048 characters. GET redirects count individually;
HEAD requests and analytics reads do not count.

## Cache and persistence

Redis caches ID, destination and expiry under shortlink:v1:link:<numeric ID>.
TTL is min(configured maximum, remaining link lifetime), with a one-hour default.
A cache hit skips the destination SELECT; GET click tracking still writes to
MySQL. Expired or corrupt cache entries are discarded. Redis failures fall back
to MySQL. MySQL remains authoritative for counters and analytics.

The fixed Base62 alphabet is 0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.
Published IDs must not be reset or reused. Codes are identifiers, not secrets.
Flyway owns table creation; Hibernate validates the resulting schema.

## Run in IntelliJ / terminal

Use JDK 17. Create an empty shortlink_db database in MySQL 8+, then configure
DB_USERNAME, DB_PASSWORD and optionally DB_URL in the launch environment.

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

The default profile is mysql. Redis defaults to localhost:6379. The optional
bootstrap profile starts only the web shell without the business API/database.
Detailed learning instructions are in [the build walkthrough](docs/BUILD_WALKTHROUGH.md).

## Configuration

| Variable | Purpose / default |
| --- | --- |
| DB_URL | MySQL JDBC URL; defaults to local shortlink_db |
| DB_USERNAME / DB_PASSWORD | Database credentials |
| APP_BASE_URL | Public origin used for generated links; defaults to local port |
| PORT | Application port; 8080 by default |
| REDIS_HOST / REDIS_PORT | Redis server; localhost / 6379 |
| REDIS_USERNAME / REDIS_PASSWORD | Optional Redis authentication |
| REDIS_SSL | Enable TLS when required; false by default |
| CACHE_ENABLED | Enable Redis caching; true by default |
| CACHE_TTL_SECONDS | Positive maximum cache lifetime; 3600 by default |

The Compose JDBC connection disables TLS on its private local network. For a
hosted database, supply the JDBC connection and TLS settings required by that
provider rather than copying the local Compose URL.

## Tests

```bash
./mvnw test
./mvnw package
```

Tests do not require live MySQL/Redis. See [the test summary](docs/TESTING.md).
The Docker build also runs tests. A smoke-test script and GitHub Actions
workflow verify the actual Compose stack when run on a Docker-capable machine:

```bash
python3 scripts/smoke_test.py --redis-check
```

## Deployment status

Docker setup is prepared at Step 11. GitHub repository creation/upload and
live MySQL/Redis/Docker verification are pending. Railway deployment is Step 12.
The /health endpoint checks process liveness, not database/cache readiness.
