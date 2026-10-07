#!/usr/bin/env python3
"""Check a running shortlink app; optionally inspect the local Compose Redis."""
import argparse
from datetime import datetime, timedelta, timezone
import json
import subprocess
import time
from urllib.error import HTTPError
from urllib.request import Request, build_opener, HTTPRedirectHandler

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None

opener = build_opener(NoRedirect())

def request(origin, path, method="GET", payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = Request(origin + path, data=data, method=method,
                  headers={"Content-Type": "application/json"} if data else {})
    try:
        response = opener.open(req, timeout=15)
    except HTTPError as error:
        response = error
    with response:
        body = response.read()
        return response.code, response.headers, json.loads(body) if body else None

def check(condition, message):
    if not condition:
        raise RuntimeError(message)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--redis-check", action="store_true")
    args = parser.parse_args()
    origin = args.base_url.rstrip("/")
    status, _, body = request(origin, "/health")
    check(status == 200 and body["status"] == "UP", "Health check failed")
    destination = "https://example.com/smoke?source=shortlink#test"
    status, _, link = request(origin, "/api/links", "POST", {"originalUrl": destination})
    check(status == 201, "Link creation failed")
    path = "/s/" + link["shortCode"]
    for _ in range(3):
        status, headers, _ = request(origin, path)
        check(status == 302 and headers["Location"] == destination, "Redirect failed")
        check(headers["Cache-Control"] == "no-store", "Redirect cache header missing")
    status, _, _ = request(origin, path, "HEAD")
    check(status == 302, "HEAD redirect failed")
    status, _, stats = request(origin, "/api/links/" + link["shortCode"] + "/stats")
    check(status == 200 and stats["clickCount"] == 3 and stats["lastClickedAt"], "Click tracking failed")
    status, _, _ = request(origin, "/s/ZZZZZZZZZZZZ")
    check(status == 404, "Invalid-code handling failed")
    status, _, _ = request(origin, "/api/links", "POST", {"originalUrl": "javascript:alert(1)"})
    check(status == 400, "URL validation failed")
    expiry = datetime.now(timezone.utc) + timedelta(seconds=10)
    expiry_text = expiry.isoformat().replace("+00:00", "Z")
    status, _, temporary = request(origin, "/api/links", "POST", {"originalUrl": destination, "expiresAt": expiry_text})
    check(status == 201, "Expiring-link creation failed")
    status, _, _ = request(origin, "/s/" + temporary["shortCode"])
    check(status == 302, "Temporary link failed before expiry")
    time.sleep(max(0, (expiry - datetime.now(timezone.utc)).total_seconds()) + 0.2)
    status, _, _ = request(origin, "/s/" + temporary["shortCode"])
    check(status == 410, "Expired link still redirects")
    status, _, expired_stats = request(origin, "/api/links/" + temporary["shortCode"] + "/stats")
    check(status == 200 and expired_stats["expired"] and expired_stats["clickCount"] == 1,
          "Expired-link analytics failed")
    if args.redis_check:
        key = "shortlink:v1:link:" + str(link["id"])
        raw = subprocess.check_output(["docker", "compose", "exec", "-T", "redis", "redis-cli", "--raw", "GET", key], text=True)
        check(json.loads(raw)["originalUrl"] == destination, "Redis routing entry missing")
        ttl = int(subprocess.check_output(["docker", "compose", "exec", "-T", "redis", "redis-cli", "TTL", key], text=True).strip())
        check(0 < ttl <= 3600, "Redis TTL is invalid for default configuration")
    print("PASS: creation, redirects, click tracking, HEAD, errors, expiry and analytics" +
          (", plus live Redis entry/TTL" if args.redis_check else ""))

if __name__ == "__main__":
    main()
