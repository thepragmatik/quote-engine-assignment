#!/usr/bin/env bash
# Automated performance test for the Commission Quote stack.
# Usage: scripts/perf.sh [REQUESTS] [CONCURRENCY]
#   REQUESTS     total POSTs (default 200)
#   CONCURRENCY  parallel workers (default 8)
# Deterministic happy path by default (FAULT_RATE=0). Set FAULT_RATE env to soak.
# Design Note: this is a load probe, not a benchmark suite — it measures the
# full HTTP round trip (including container network) and asserts the spec's
# <100ms p95 NFR plus a zero-server-error budget.
set -euo pipefail
cd "$(dirname "$0")/.."
REQUESTS="${1:-200}"
CONCURRENCY="${2:-8}"
KEY="${VENDOR_API_KEY:-dev-vendor-secret}"

echo "== perf: $REQUESTS requests, concurrency $CONCURRENCY, FAULT_RATE=${FAULT_RATE:-0} =="
FAULT_RATE="${FAULT_RATE:-0}" VENDOR_API_KEY="$KEY" docker compose up -d >/dev/null

# poll health
for i in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:8000/actuator/health)" = "200" ] && break
  sleep 1
done

# warmup (3 requests, excluded from stats)
for i in 1 2 3; do
  curl -s -o /dev/null -X POST localhost:8000/api/v1/quotes -H 'Content-Type: application/json' \
    -d '{"loanAmount":250000,"loanTermInMonths":36,"riskBand":"MEDIUM"}'
done

SEQ=$(seq "$REQUESTS")
CODES=$(echo "$SEQ" | xargs -P "$CONCURRENCY" -I{} curl -s -o /dev/null -w '%{http_code} %{time_total}\n' \
  --max-time 10 -X POST localhost:8000/api/v1/quotes -H 'Content-Type: application/json' \
  -d '{"loanAmount":250000,"loanTermInMonths":36,"riskBand":"MEDIUM"}')

echo "$CODES" | awk '
  { codes[$1]++; t[NR] = $2 }
  END {
    n2 = NR
    for (i = 1; i <= n2; i++)
      for (j = i + 1; j <= n2; j++)
        if (t[j] < t[i]) { tmp = t[i]; t[i] = t[j]; t[j] = tmp }
    p50 = t[int(n2 * 0.50 + 0.5)] * 1000
    p95 = t[int(n2 * 0.95 + 0.5)] * 1000
    p99 = t[int(n2 * 0.99 + 0.5)] * 1000
    for (c2 in codes) printf "  %-4s %5d\n", c2, codes[c2]
    printf "  p50 = %6.1f ms\n  p95 = %6.1f ms  (NFR < 100 ms)\n  p99 = %6.1f ms\n", p50, p95, p99
    exit (p95 < 100 ? 0 : 1)
  }'
echo "perf: DONE"
