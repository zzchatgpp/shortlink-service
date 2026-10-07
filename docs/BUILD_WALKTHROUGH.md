# Shortlink Service — Step 10 of 12

Java 17 / Spring Boot 3.5 / Maven / MySQL / Redis.

This checkpoint includes project setup, MySQL configuration, the ShortLink JPA
entity, a Spring Data repository, a Flyway migration, Base62 conversion, the create-link API, redirects, click tracking, Redis caching, analytics, Swagger documentation and tests.
Core service test review is complete. Containerization, GitHub upload and
Railway deployment remain in Steps 11 and 12.

## 1. Open in IntelliJ IDEA

Extract the archive and open the `shortlink-service` folder. Select JDK 17 as
the Project SDK and let Maven load dependencies from `pom.xml`.

## 2. Create an empty MySQL database

With MySQL 8+ running, connect using MySQL Workbench or the MySQL CLI:

```sql
CREATE DATABASE IF NOT EXISTS shortlink_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Use an account permitted to create tables and read/write this database.
For local learning you may use your existing root account. Use a dedicated
application account for deployment.

## 3. Configure the database connection

In IntelliJ, open Run > Edit Configurations. Select or create a Spring Boot
configuration with main class `com.mohammed.shortlink.ShortlinkServiceApplication`.
Add these environment variables, substituting your actual local credentials:

- `DB_USERNAME=root`
- `DB_PASSWORD=<your actual MySQL password>`
- Optional: `DB_URL=jdbc:mysql://localhost:3306/shortlink_db?connectionTimeZone=UTC`

If your local account intentionally has no password, set DB_PASSWORD to an
empty value explicitly. Do not put angle brackets around your actual password.

Remove `bootstrap` from Active profiles. `mysql` is now the default profile.
You may set Active profiles to `mysql` explicitly.

For the terminal, set the same variables in your shell and run:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

Spring Boot does not automatically load a `.env` file. Configure environment
variables in IntelliJ or export them in the terminal before launching.

## 4. Check the database

At startup Flyway runs `V1__create_short_links.sql` automatically. Hibernate
then validates that the table matches the entity; it does not alter the table.

```sql
USE shortlink_db;
SHOW TABLES;
DESCRIBE short_links;
SELECT * FROM short_links;
```

Expect `short_links` and `flyway_schema_history`. `short_links` starts empty and receives rows when you call POST /api/links. Do not manually execute the migration or
create the table before Flyway runs. Subsequent startups reuse the existing
schema. Add a new migration for future changes instead of editing applied ones.

Swagger: http://localhost:8080/swagger-ui/index.html. Use POST /api/links to create a link.

## Entity fields

| Java field | Database column | Purpose |
| --- | --- | --- |
| id | id | Auto-increment ID; Source of the Base62 short code |
| originalUrl | original_url | Destination URL, up to 2048 characters |
| createdAt | created_at | UTC creation time |
| expiresAt | expires_at | Optional UTC expiry; null means no expiry |
| clickCount | click_count | Increments for each active GET redirect |
| lastClickedAt | last_clicked_at | Null until the first click |

There is no separate short-code column: later steps derive codes from IDs and
decode codes back to IDs for lookup. The same URL may be saved more than once,
with a different ID each time. Timestamps use Java Instant and UTC JDBC handling.

## Repository

`ShortLinkRepository extends JpaRepository<ShortLink, Long>` inherits methods
such as save(), findById(), existsById() and deleteById(). No custom SQL is
needed for basic CRUD operations.

## Base62 conversion (Step 3)

`Base62.encode(id)` converts a positive database ID to a short code.
`Base62.decode(code)` converts it back to the same ID.

```java
String code = Base62.encode(125L); // "21"
long id = Base62.decode("21");    // 125
```

The alphabet is `0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`.
Codes are case-sensitive: `a` means 10 and `A` means 36. Do not lowercase codes.
Encoding uses division and remainder by 62, then reverses the collected digits.
Decoding uses `id = id * 62 + digit` for each character.

Examples: 1 -> `1`, 10 -> `a`, 61 -> `Z`, 62 -> `10`, 125 -> `21`,
3844 -> `100`. The largest positive Java long uses 11 characters.

Database IDs begin at 1, so this utility rejects zero and negative IDs. The
decoder rejects null/empty input, non-Base62 characters, leading zeros, codes
longer than 11 characters, and values beyond Long.MAX_VALUE. It checks overflow
before multiplication. The redirect controller returns HTTP 404 for malformed or overflowing codes.

Different positive IDs have different canonical Base62 encodings. This relies
on retaining the same alphabet and not resetting/reusing database IDs for
published links. Codes are predictable identifiers, not access secrets.

## Create-link API (Step 4)

Start the app with the MySQL configuration above. The endpoint is:

```http
POST /api/links
Content-Type: application/json
```

Request with an optional future expiry:

```json
{
  "originalUrl": "https://example.com/articles/java?source=demo",
  "expiresAt": "2030-01-01T00:00:00Z"
}
```

For no expiry, omit expiresAt or set it to null. Use an ISO-8601 timestamp with
an offset or Z, for example `2030-01-01T00:00:00Z`.

```bash
curl -i -X POST http://localhost:8080/api/links \
  -H 'Content-Type: application/json' \
  -d '{"originalUrl":"https://example.com/articles/java"}'
```

Successful requests return HTTP 201 with a Location header containing shortUrl:

```json
{
  "id": 1,
  "shortCode": "1",
  "shortUrl": "http://localhost:8080/s/1",
  "originalUrl": "https://example.com/articles/java",
  "createdAt": "2026-10-07T10:30:00Z",
  "expiresAt": null
}
```

The ID, code and timestamp vary. Open the returned shortUrl in a browser to
redirect to the destination. Check the saved row with:

```sql
SELECT * FROM short_links ORDER BY id DESC;
```

Validation requires a nonblank absolute HTTP/HTTPS URL, a parseable host,
no embedded username/password, a valid port (1–65535 when specified), and at
most 2048 characters. URL spaces must be percent-encoded. International domain
names should use their ASCII/punycode form. Validation parses the URL locally;
it does not make a network request or check whether the destination is online.
The destination is stored as supplied, including query parameters and fragments.

Optional expiry must be in the future and no later than 9999-12-31T23:59:59Z,
which keeps it within the MySQL DATETIME storage range. It is checked during request validation
and again immediately before saving. Invalid requests return HTTP 400 with a
problem-details body; validation errors include field messages:

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "errors": {"originalUrl": "originalUrl is required"}
}
```

Set APP_BASE_URL to the app's public origin when deploying, for example
`https://your-app.up.railway.app`. It must not contain a path, query, fragment,
or embedded credentials. Locally it defaults to http://localhost:<server port>.
The configured origin is used instead of trusting the incoming Host header.

Flow: Controller validates the request -> Service checks expiry -> Repository
saves the row -> Base62 encodes its ID -> Controller returns HTTP 201.

## Redirects (Step 5)

```http
GET /s/{code}
```

Create a link using POST /api/links, then open its returned shortUrl in a browser.
To inspect the redirect without following it, replace the example code with
your actual shortCode:

```bash
curl -i http://localhost:8080/s/1
```

A valid, active link returns:

```http
HTTP/1.1 302 Found
Location: https://example.com/articles/java
Cache-Control: no-store
```

| Situation | HTTP status | Behavior |
| --- | --- | --- |
| Active link or no expiry | 302 Found | Location header points to the original URL |
| Unknown or malformed code | 404 Not Found | Link not found problem-details response |
| Expiry at or before the current time | 410 Gone | Link expired problem-details response |

The service decodes Base62 to a database ID, finds the row, checks its optional
expiry against a UTC clock, and returns the destination. It preserves query
parameters and fragments; Unicode path characters are percent-encoded in the
Location header. Expired rows are retained and not redirected.

All redirect-route outcomes include Cache-Control: no-store, instructing caches
not to reuse responses. This keeps future visits passing through the service
for expiry checks and click tracking. Spring MVC also supports HEAD on this
GET route; HEAD requests do not change analytics.

Example expired-link response:

```json
{
  "title": "Link expired",
  "status": 410,
  "detail": "This short link has expired"
}
```

For a manual expiry check, create a link with a timestamp a few minutes in the
future. Open it before expiry, then again after expiry. After expiry it returns
410 instead of redirecting. Tests use a fixed clock so the exact boundary is
verified without waiting.

## Click tracking (Step 6)

Each active GET request to /s/{code} increments click_count and updates
last_clicked_at in the same database statement before returning HTTP 302.
The service transaction must commit before the controller returns the redirect.
Repeated visits count individually. HEAD requests, unknown/malformed codes and
expired links do not change click counts or timestamps.

The database performs `click_count = click_count + 1` atomically, so concurrent
requests do not overwrite one another's counts. last_clicked_at keeps the latest
visit time even if concurrent requests update the database out of order. An
expiry condition is included in the update, covering expiry that passes after
the initial lookup. If the update affects no row, no redirect is returned.

To check tracking, create a link and open its shortUrl several times. Inspect:

```sql
SELECT id, original_url, click_count, last_clicked_at
FROM short_links
ORDER BY id DESC;
```

To count a GET redirect without visiting the external destination:

```bash
curl -i http://localhost:8080/s/1
```

Replace 1 with your actual code. Using `curl -I` sends HEAD instead and does not
increment the count. last_clicked_at is stored in UTC and remains null until
the first counted visit. MySQL timestamps have microsecond precision.
No additional database migration is needed because these fields already exist.
The analytics endpoint exposes these values through GET /api/links/{code}/stats.

## Redis caching (Step 7)

Start a local Redis instance on port 6379. If Docker Desktop is installed and
running, use:

```bash
docker run -d --name shortlink-redis -p 127.0.0.1:6379:6379 redis:7.4-alpine
docker exec shortlink-redis redis-cli PING
```

Expect PONG. If this container already exists, use `docker start shortlink-redis`
instead of creating it again. If you already run Redis locally, use that server.
Full app/MySQL/Redis Docker Compose setup comes in Step 11.

The defaults connect to localhost:6379 without authentication. Optional
IntelliJ environment variables:

| Variable | Default / purpose |
| --- | --- |
| REDIS_HOST | localhost |
| REDIS_PORT | 6379 |
| REDIS_USERNAME | Empty; set for ACL authentication if required |
| REDIS_PASSWORD | Empty; set for authenticated servers |
| REDIS_SSL | false; enable when your server requires TLS |
| CACHE_ENABLED | true; false disables cache operations |
| CACHE_TTL_SECONDS | 3600; must be positive |

Connection and command timeouts are one second. Keep your MySQL credentials
configured as before. Redis stores routing information only, not click counters.
The cached JSON contains the database ID, original URL and optional expiry.

Cache-aside flow:

1. Decode the short code to its ID.
2. Try Redis using `shortlink:v1:link:<numeric database ID>`.
3. On a miss, load the link from MySQL, validate expiry, and cache routing data.
4. On a hit, skip the MySQL link SELECT; do not refresh the TTL.
5. For GET requests, atomically update click counts in MySQL before returning 302.

A GET cache hit still writes click tracking to MySQL. The optimization removes
repeated destination lookups. HEAD cache hits do not update click tracking.
MySQL remains authoritative for analytics and the atomic expiry check on GET.

TTL is the smaller of the configured maximum and the remaining link lifetime.
Links without expiry use the configured maximum. Expired links and links with
less than one millisecond remaining are not cached. Cached expiry is checked
again when reading, so a late-expiring Redis key cannot bypass link expiry.
Bad JSON, mismatched IDs and invalid cached URLs are discarded. Redis failures
fall back to database lookup; failed cache writes/evictions do not fail redirects.

To inspect the cache after visiting a short URL:

```bash
docker exec shortlink-redis redis-cli --scan --pattern 'shortlink:v1:link:*'
docker exec shortlink-redis redis-cli GET shortlink:v1:link:1
docker exec shortlink-redis redis-cli TTL shortlink:v1:link:1
```

Replace numeric ID 1 with the ID in the create-link response, not its Base62
code. TTL should be positive and at most 3600 seconds with the default setting,
or lower for a sooner link expiry. `-2` means the key is absent/expired.
If using a separately installed Redis, run redis-cli directly.

Disable caching with CACHE_ENABLED=false when you want to run without Redis.
Do not cache or manually change click counters in Redis; they remain in MySQL.
There is no schema migration for this step.

## Analytics endpoint (Step 8)

```http
GET /api/links/{code}/stats
```

Use the shortCode returned by POST /api/links, preserving its case. For example:

```bash
curl -i http://localhost:8080/api/links/1/stats
```

Replace 1 with your actual short code. You can also use this endpoint in Swagger
UI. Example response after three counted visits:

```json
{
  "id": 1,
  "shortCode": "1",
  "shortUrl": "http://localhost:8080/s/1",
  "originalUrl": "https://example.com/articles/java",
  "createdAt": "2026-10-07T10:30:00Z",
  "expiresAt": null,
  "clickCount": 3,
  "lastClickedAt": "2026-10-07T10:35:00Z",
  "expired": false
}
```

The endpoint always reads stored statistics from MySQL and does not read or
write the Redis routing cache. Responses include Cache-Control: no-store to
avoid cached statistics. Visiting the analytics endpoint does not increment
clickCount. HEAD requests to the redirect endpoint also leave it unchanged.

| Field | Meaning |
| --- | --- |
| id / shortCode / shortUrl | Link identifiers and the generated redirect URL |
| originalUrl | Original destination |
| createdAt | Creation time |
| expiresAt | Optional expiry time; null means no expiry |
| clickCount | Number of recorded GET redirects |
| lastClickedAt | Latest recorded visit time; null before the first click |
| expired | Whether the optional expiry is at or before the current UTC time |

Expired links return HTTP 200 with expired=true and their retained statistics.
Their redirect endpoint still returns HTTP 410. Unknown, malformed or overflowing
codes return HTTP 404 using the existing problem-details handler.
This step needs no database migration.

Try POST /api/links -> GET /s/{code} twice -> GET /api/links/{code}/stats.
The count is 2. Repeat the stats request and it remains 2.

## Swagger / OpenAPI documentation (Step 9)

Run the application with MySQL configured (and optionally Redis), then open:

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml

The UI groups endpoints into Links and Redirects. Each operation includes a
summary, behavior, response statuses and headers, schemas and example JSON.
The request model explains required originalUrl, its 2048-character limit,
HTTP/HTTPS restrictions, optional expiry and timestamp format. Response models
explain code case sensitivity, nullable timestamps, click counts and expiry.

| Operation | Purpose | Main documented statuses |
| --- | --- | --- |
| POST /api/links | Create a link | 201, 400, 415 |
| GET /s/{code} | Redirect and count a GET visit | 302, 404, 410 |
| GET /api/links/{code}/stats | Read current analytics | 200, 404 |

### Try it in Swagger

1. Expand POST /api/links and select Try it out.
2. Choose the Permanent link example, or select Expiring link and ensure its
   timestamp is still in the future. Execute the request.
3. Copy the returned shortCode and shortUrl. Example response IDs and timestamps
   are illustrative; actual values vary.
4. Open shortUrl in a browser to redirect to the destination.
5. Expand GET /api/links/{code}/stats, enter the returned code exactly, and execute.
6. Submit an empty originalUrl to see the documented 400 validation response.

For a raw 302 response, use curl -i as shown above. Swagger's browser-based
request may follow the redirect to the external destination, so opening the
shortUrl directly is the clearest way to try a redirect.

The specification uses a relative server URL (/) so Swagger uses the current
application origin locally and on Railway. No localhost server URL is hardcoded
into the server list. Examples use localhost for illustration. APP_BASE_URL
still controls generated short URLs and must match the public origin on deployment.

A generated specification is included at docs/openapi.json. Regenerate it by
running the tests and copying target/openapi.json to docs/openapi.json. The live
/v3/api-docs endpoint always reflects the running application's annotations.

## Service tests and final review (Step 10)

The suite includes JUnit tests, Mockito service/cache tests, H2-backed repository
and API integration tests, a parallel-click test, and OpenAPI documentation
checks. See docs/TESTING.md for the verified suite summary and remaining live
service checks.

Run all tests without starting MySQL or Redis:

```bash
./mvnw test
```

Run the core creation service tests alone:

```bash
./mvnw -Dtest=CreationServiceTest test
```

Build the executable app and run all tests:

```bash
./mvnw package
```

Core service checks verify that the database-assigned ID becomes the Base62
code, optional expiry is preserved, generated URLs use the configured origin,
a failed save produces no response/cache entry, and unknown/invalid/expired
codes never count a visit. The expiry-race check covers expiry between lookup
and the atomic click update. A cache hit skips the destination lookup while
retaining the click write.

Review also added a controlled HTTP 400 error for expiry timestamps beyond the
supported MySQL date range. This prevents accepting a Java Instant that cannot
be stored in the database. API and service tests verify rejection before writes.
Expiry remains nullable and supports dates after 2038 because the schema uses
DATETIME rather than a MySQL TIMESTAMP column.

Mockito is configured with its subclass mock maker to avoid requiring JVM agent
attachment. Tests use classes/interfaces that do not require final/static mocks.
Spring tests use the test profile: an in-memory H2 database and disabled Redis
operations. These checks do not establish live MySQL/Redis compatibility.

## Checks

```bash
./mvnw clean package
```

Base62 tests cover known conversions, case sensitivity, invalid input, the
maximum long value, overflow and round trips across the ID range.
API integration tests exercise real controller/service/repository behavior on
H2, including redirects, exact expiry boundaries, unknown/invalid codes,
creation, optional expiry, duplicate URLs, malformed JSON and
rejection without database writes. Service tests cover expiry at persistence time.
H2-backed repository tests verify persistence, nullable expiry, distinct IDs,
unknown-ID lookup, atomic click updates, expiry rejection and timestamp ordering.
A concurrent service test verifies 80 redirects across 8 worker threads produce
exactly 80 clicks. API tests verify repeated GET tracking and HEAD exclusion. H2 is test-only: runtime uses MySQL. These tests do
not verify MySQL-specific migration syntax or a live MySQL connection.

Redis tests use mocked StringRedisTemplate operations to verify JSON, cache
hits/misses, TTL limits, expiry rejection, corrupt values and outage fallback.
Service tests verify cached GET requests skip the database lookup while retaining
click writes. Tests do not require a running Redis server. Live MySQL and Redis
verification remains outstanding.

Analytics tests cover create/redirect/stats flow, empty analytics, current
counters, nullable timestamps, expiry status, unknown codes and the fact that
reading statistics never records a click or touches Redis.

Documentation checks verify all three paths, request examples, required fields,
response headers, nullable timestamps, redirect status/body and Swagger UI access.

The optional Step 1 web-only startup remains available:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=bootstrap
```

## Troubleshooting

- Missing DB_PASSWORD: set the environment variable in the launch configuration.
- Access denied: check DB_USERNAME, DB_PASSWORD and account permissions.
- Unknown database: create shortlink_db first.
- Connection refused: ensure MySQL is running on the host and port in DB_URL.
- No table created: remove the bootstrap profile.

No local credentials are committed. GitHub and Railway publishing remain
scheduled for Steps 11 and 12.
