#!/usr/bin/env bash
#
# Checks a running copy of the app that was built with the map screen inside it:
#
#   ./mvnw -Pwith-screen package
#   java -jar target/route-planner-0.0.1-SNAPSHOT.jar
#   scripts/check-one-app.sh
#
# It asks one address for the screen and for a plan, the way a browser would. Give the address
# as the first argument if the app is not on port 8080.

set -euo pipefail

base="${1:-http://127.0.0.1:8080}"

fail() {
    echo "FAILED: $1" >&2
    exit 1
}

page=$(curl --fail --silent --show-error "$base/") || fail "nothing answered at $base/"
grep --quiet '<div id="root">' <<<"$page" || fail "the page at $base/ is not the map screen"

page_headers=$(curl --fail --silent --show-error --output /dev/null --dump-header - "$base/")
grep --quiet --ignore-case '^cache-control: no-cache' <<<"$page_headers" \
    || fail "the browser is not told to ask again for the page, so it could show an old build"

script=$(grep --only-matching '/assets/[^"]*\.js' <<<"$page" | head -n 1 || true)
[ -n "$script" ] || fail "the map screen names no script"

script_headers=$(curl --fail --silent --show-error --output /dev/null --dump-header - \
    --header 'Accept-Encoding: gzip' "$base$script") || fail "the script at $script did not load"
grep --quiet --ignore-case '^content-encoding: gzip' <<<"$script_headers" \
    || fail "the script at $script was sent uncompressed"

plan=$(curl --fail --silent --show-error "$base/api/v1/routes/optimize" \
    --header 'Content-Type: application/json' \
    --data '{
      "start": { "name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621 },
      "stops": [
        { "name": "Takapuna", "latitude": -36.7870, "longitude": 174.7740 },
        { "name": "Devonport", "latitude": -36.8330, "longitude": 174.7955 }
      ],
      "returnToStart": true
    }') || fail "the API did not plan a route"
grep --quiet '"ordersChecked":2' <<<"$plan" || fail "the API answered, but not with a plan: $plan"

echo "The map screen and the API both answer at $base"
