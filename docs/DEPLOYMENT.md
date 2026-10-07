# Railway deployment

Public API origin: https://shortlink-service-production-fc6c.up.railway.app

GitHub: https://github.com/zzchatgpp/shortlink-service

## Services

| Service | Role |
| --- | --- |
| shortlink-service | Java 17 application built with the repository Dockerfile |
| MySQL | Authoritative links, counters and timestamps; persistent volume |
| Redis | Authenticated routing cache on the private network |

The app listens on PORT=8080. The public Railway domain routes to that port.
The health check uses /health with a 120-second startup window. The runtime
uses a non-root user and an on-failure restart policy.

## Environment

SPRING_PROFILES_ACTIVE=mysql. APP_BASE_URL uses Railway's generated public
origin. DB_URL is assembled from MySQL's private host, port and database.
DB_USERNAME and DB_PASSWORD reference the MySQL service's generated variables.
REDIS_HOST, REDIS_PORT, REDIS_USERNAME and REDIS_PASSWORD reference Redis's
variables. CACHE_ENABLED=true and CACHE_TTL_SECONDS=3600.

MySQL and Redis use Railway's private network; neither is given a public TCP
proxy. The private MySQL JDBC connection disables TLS and allows the driver's
public-key retrieval for authentication. Credentials are provided by Railway
rather than files in the repository.

## Verification

The GitHub workflow runs the Java test suite, builds and starts the Compose
stack, then checks API behavior and an actual Redis cache entry and TTL.
The same API smoke script can check the Railway origin:

```bash
python3 scripts/smoke_test.py --base-url https://shortlink-service-production-fc6c.up.railway.app --expiry-seconds 60
```

The --redis-check option applies only to the local Compose stack. API checks
include creation, three counted redirects, uncounted HEAD, analytics, invalid
URL/code errors, optional expiry and retained expired-link analytics.
