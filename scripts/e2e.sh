#!/usr/bin/env bash
# E2E verification for the Commission Quote stack (plan M5.2/M8.2).
# Deterministic happy path first (FAULT_RATE=0), then a fault soak at 0.2.
set -u
cd "$(dirname "$0")/.."
PASS=0; FAIL=0
ok()   { echo "PASS: $1"; PASS=$((PASS+1)); }
bad()  { echo "FAIL: $1"; FAIL=$((FAIL+1)); }

cleanup() { docker compose down -v >/dev/null 2>&1; }
trap cleanup EXIT

echo "== Phase 1: deterministic happy path (FAULT_RATE=0) =="
echo 'FAULT_RATE=0' > .env
docker compose up --build -d || { bad "compose up"; exit 1; }
sleep 10
docker compose ps
curl -s -o /dev/null -w '%{http_code}' http://localhost:8000/actuator/health | grep -q 200 \
  && ok "backend healthy" || bad "backend health"

RESP=$(curl -s -X POST localhost:8000/api/v1/quotes -H 'Content-Type: application/json' \
  -d '{"loanAmount":250000.00,"loanTermInMonths":36,"riskBand":"MEDIUM"}')
echo "$RESP"
echo "$RESP" | grep -q '"commissionRate":0.0231' && ok "rate 0.0231" || bad "rate: $RESP"
echo "$RESP" | grep -q '"totalCommission":5775.00' && ok "total 5775.00" || bad "total: $RESP"

echo "$RESP" | grep -qE '"quoteId":"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"' \
  && ok "quoteId is UUID" || bad "quoteId: $RESP"

for svc in backend mock-vendor; do
  uid=$(docker compose exec "$svc" id -u 2>/dev/null)
  [ "$uid" = "10001" ] && ok "$svc uid 10001" || bad "$svc uid: $uid"
done

echo "== Phase 2: validation errors =="
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST localhost:8000/api/v1/quotes \
  -H 'Content-Type: application/json' -d '{"loanAmount":999.99,"loanTermInMonths":36,"riskBand":"LOW"}')
[ "$code" = "400" ] && ok "below-min amount -> 400" || bad "expected 400 got $code"

echo "== Phase 3: fault soak (FAULT_RATE=0.2) =="
docker compose down >/dev/null 2>&1
echo 'FAULT_RATE=0.2' > .env
docker compose up -d || { bad "compose up soak"; exit 1; }
sleep 10
CODES=$(for i in $(seq 1 10); do
  curl -s -o /dev/null -w '%{http_code} ' --max-time 9 -X POST localhost:8000/api/v1/quotes \
    -H 'Content-Type: application/json' -d '{"loanAmount":250000,"loanTermInMonths":36,"riskBand":"LOW"}'
done)
echo "codes: $CODES"
echo "$CODES" | grep -qE '200' && ok "soak contains 200s" || bad "no 200s in soak"
echo "$CODES" | grep -qE '000' && bad "a request hung/timed out" || ok "zero hangs (max-time 9s cap)"

echo "== Summary =="
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ] && echo "E2E: ALL PASS" || echo "E2E: FAILURES PRESENT"
exit "$FAIL"
