#!/usr/bin/env bash
# Production smoke test for SpendSmart.
#
# Verifies the whole deployed path in one flow: static frontend, SPA fallback rewrite, /api proxy rewrite,
# session cookie, CSRF, registration, persistence, logout, login and account self-deletion. Run it against
# the PUBLIC frontend URL (the Vercel deployment), because that is the origin real users hit:
#
#   ./scripts/smoke-test.sh https://spendsmart-expenses-tau.vercel.app
#
# Requirements: bash + curl (Git Bash on Windows provides both). No jq, no Docker, no Node.
# It registers a throwaway account (smoke+<epoch>@example.com), exercises the API through the browser's
# origin and deletes the account again, so repeated runs leave no test data behind.
#
# Exit code 0 = every check passed. Non-zero = at least one check failed (details on stdout).

set -u

BASE_URL="${1:-${SPENDSMART_BASE_URL:-}}"
if [ -z "$BASE_URL" ]; then
  echo "usage: $0 <base-url>   (e.g. $0 https://spendsmart-expenses-tau.vercel.app)" >&2
  exit 2
fi
BASE_URL="${BASE_URL%/}"

JAR="$(mktemp)"
STAMP="$(date +%s)"
EMAIL="smoke+${STAMP}@example.com"
PASSWORD="Smoke-Test-${STAMP}!"
DESCRIPTION="smoke-test-${STAMP}"
FAILURES=0

cleanup() { rm -f "$JAR"; }
trap cleanup EXIT

pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; FAILURES=$((FAILURES + 1)); }

# check <description> <expected-status> <actual-status> [extra-grep "<pattern>" "<label>"]
check() {
  local desc="$1" expected="$2" actual="$3" extra_pattern="${4:-}" extra_label="${5:-}" body="${6:-}"
  if [ "$actual" = "$expected" ]; then
    if [ -n "$extra_pattern" ] && ! printf '%s' "$body" | grep -q "$extra_pattern"; then
      fail "$desc (status $actual ok, but missing: $extra_label)"
      return 1
    fi
    pass "$desc ($actual)"
    return 0
  fi
  fail "$desc (expected $expected, got $actual)"
  printf '%s\n' "$body" | head -c 400 | sed 's/^/      body: /'; echo
  return 1
}

# http <METHOD> <path> [json-body] -> sets STATUS and BODY; reuses the cookie jar; adds the CSRF header
# from the jar's XSRF-TOKEN cookie for unsafe methods, exactly like the frontend API client does.
http() {
  local method="$1" path="$2" data="${3:-}"
  local csrf args=(-sS -o - -w '\n%{http_code}' -X "$method" -b "$JAR" -c "$JAR" --max-time 30)
  if [ "$method" != "GET" ]; then
    csrf="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR" | tail -1)"
    [ -n "$csrf" ] && args+=(-H "X-XSRF-TOKEN: $csrf")
  fi
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data "$data")
  local out
  out="$(curl "${args[@]}" "$BASE_URL$path")" || out=$'\n000'
  STATUS="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
}

echo "Smoke-testing $BASE_URL ..."
echo

# 1. Static frontend and SPA fallback ---------------------------------------
out="$(curl -sS -o /dev/null -w '%{http_code} %{content_type}' --max-time 30 "$BASE_URL/")" || out="000"
check "frontend serves index.html at /" "200 text/html" "$out"

out="$(curl -sS -o /dev/null -w '%{http_code} %{content_type}' --max-time 30 "$BASE_URL/register")" || out="000"
check "SPA fallback: /register returns the app shell (vercel.ts rewrite)" "200 text/html" "$out"

# 2. API reachable through the browser's origin (the vercel.ts /api proxy) ---
http GET /api/auth/csrf
check "GET /api/auth/csrf through the public origin" "204" "$STATUS"
if awk '$6 == "XSRF-TOKEN" { found = 1 } END { exit !found }' "$JAR"; then
  pass "XSRF-TOKEN cookie set"
else
  fail "XSRF-TOKEN cookie set"
fi

# 3. Registration establishes a real session ---------------------------------
http POST /api/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"displayName\":\"Smoke Test\",\"defaultCurrency\":\"USD\",\"locale\":\"en-US\",\"timeZone\":\"UTC\"}"
check "POST /api/auth/register" "201" "$STATUS" "\"email\":\"$EMAIL\"" "registered email echoed" "$BODY"
if awk '$6 == "SPENDSMART_SESSION" { found = 1 } END { exit !found }' "$JAR"; then
  pass "SPENDSMART_SESSION cookie set"
else
  fail "SPENDSMART_SESSION cookie set"
fi

http GET /api/auth/me
check "GET /api/auth/me as the new user" "200" "$STATUS" "\"email\":\"$EMAIL\"" "email in payload" "$BODY"

# 4. Financial data persists --------------------------------------------------
http GET /api/categories
check "GET /api/categories" "200" "$STATUS"
CATEGORY_ID="$(printf '%s' "$BODY" | tr '}' '\n' | awk '
  /"type":"EXPENSE"/ { if (match($0, /"id":"[0-9a-fA-F-]{36}"/)) { print substr($0, RSTART + 6, 36); exit } }')"
if [ -n "$CATEGORY_ID" ]; then
  pass "expense category found ($CATEGORY_ID)"
else
  fail "expense category found"
fi

if [ -n "$CATEGORY_ID" ]; then
  http POST /api/transactions "{\"type\":\"EXPENSE\",\"amount\":\"7.25\",\"currency\":\"USD\",\"categoryId\":\"$CATEGORY_ID\",\"description\":\"$DESCRIPTION\",\"transactionDate\":\"$(date -u +%F)\"}"
  check "POST /api/transactions" "201" "$STATUS" "\"description\":\"$DESCRIPTION\"" "created transaction echoed" "$BODY"
  TX_ID="$(printf '%s' "$BODY" | sed -n 's/.*"id":"\([0-9a-fA-F-]\{36\}\)".*/\1/p' | head -1)"

  http GET "/api/transactions?q=$DESCRIPTION"
  check "GET /api/transactions?q= finds it after reload" "200" "$STATUS" "$TX_ID" "transaction id in page" "$BODY"

  http GET "/api/dashboard"
  check "GET /api/dashboard reads persisted data" "200" "$STATUS"
fi

# 5. Logout invalidates the session; login restores it ------------------------
http POST /api/logout
check "POST /api/auth/logout" "204" "$STATUS"
http GET /api/auth/me
check "GET /api/auth/me after logout" "401" "$STATUS"

http POST /api/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
check "POST /api/auth/login" "200" "$STATUS" "\"email\":\"$EMAIL\"" "email in payload" "$BODY"
http GET /api/auth/me
check "GET /api/auth/me after login" "200" "$STATUS"

# 6. Unauthenticated access is rejected --------------------------------------
JAR_BACKUP="$JAR"; JAR="$(mktemp)"   # empty jar = no session, no CSRF cookie
http GET /api/transactions
check "GET /api/transactions without a session" "401" "$STATUS"
rm -f "$JAR"; JAR="$JAR_BACKUP"

# 7. Cleanup: delete the throwaway account (requires password + typed DELETE) --
http POST /api/account/delete "{\"password\":\"$PASSWORD\",\"confirmation\":\"DELETE\"}"
check "POST /api/account/delete (cleans up the smoke account)" "204" "$STATUS"
http GET /api/auth/me
check "GET /api/auth/me after account deletion" "401" "$STATUS"

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "SMOKE TEST PASSED — frontend, /api proxy, auth, persistence and cleanup all verified."
  exit 0
fi
echo "SMOKE TEST FAILED — $FAILURES check(s) failed. See output above."
exit 1
