#!/usr/bin/env bash
# M9 — Budget exceeded fallback manual verification
#
# Prerequisites:
#   docker compose up --build
#   AI must be enabled in triage service (AI_ENABLED=true)
#   Tight budget: set TRIAGEMATE_AI_COST_MAX_DAILY_USD=0.001 in triage env
#
# Usage:
#   bash scripts/manual-test-m9-budget-exceeded.sh

set -euo pipefail

INGEST_URL="${INGEST_URL:-http://localhost:8081}"
TRIAGE_URL="${TRIAGE_URL:-http://localhost:8082}"
PG_CONTAINER="${PG_CONTAINER:-triagemate-postgres}"
PG_DB="${PG_DB:-triagemate}"
PG_USER="${PG_USER:-triagemate}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "============================================="
echo "  M9 — Budget Exceeded Fallback Verification"
echo "============================================="
echo ""

# Step 1: Check services are up
echo -e "${YELLOW}[1/5] Checking services...${NC}"
if ! curl -sf "${INGEST_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}FAIL: Ingest service not reachable at ${INGEST_URL}${NC}"
    exit 1
fi
if ! curl -sf "${TRIAGE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}FAIL: Triage service not reachable at ${TRIAGE_URL}${NC}"
    exit 1
fi
echo -e "${GREEN}OK: Both services are up${NC}"
echo ""

# Step 2: Record baseline metric
echo -e "${YELLOW}[2/5] Recording baseline budget_exceeded metric...${NC}"
BASELINE=$(curl -sf "${TRIAGE_URL}/actuator/prometheus" | grep 'triagemate_ai_budget_exceeded_total' | grep -v '^#' | awk '{print $2}')
echo "  Baseline: ${BASELINE:-0.0}"
echo ""

# Step 3: Send test event
echo -e "${YELLOW}[3/5] Sending test event to trigger budget exceeded...${NC}"
REQUEST_ID="m9-budget-test-$(date +%s)"
RESPONSE=$(curl -sf -X POST "${INGEST_URL}/api/ingest/messages" \
    -H "Content-Type: application/json" \
    -H "X-Request-Id: ${REQUEST_ID}" \
    -d '{
        "channel": "device-telemetry",
        "content": "M9 budget exceeded test event"
    }')
echo "  Response: ${RESPONSE}"
echo "  Request-Id: ${REQUEST_ID}"
echo ""

# Step 4: Wait and check Prometheus
echo -e "${YELLOW}[4/5] Waiting 5s for processing, then checking metrics...${NC}"
sleep 5

CURRENT=$(curl -sf "${TRIAGE_URL}/actuator/prometheus" | grep 'triagemate_ai_budget_exceeded_total' | grep -v '^#' | awk '{print $2}')
echo "  Budget exceeded metric: ${CURRENT:-0.0}"

FALLBACK=$(curl -sf "${TRIAGE_URL}/actuator/prometheus" | grep 'triagemate_ai_fallback_total' | grep 'reason="budget_exceeded"' | awk '{print $2}')
echo "  Fallback (budget_exceeded) metric: ${FALLBACK:-0.0}"
echo ""

# Step 5: Query audit table
echo -e "${YELLOW}[5/5] Querying ai_decision_audit for error rows...${NC}"
docker exec "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_DB}" -c \
    "SELECT event_id, decision_id, error_type, error_message, created_at
     FROM ai_decision_audit
     WHERE error_type = 'BUDGET_EXCEEDED'
     ORDER BY created_at DESC
     LIMIT 5;"
echo ""

echo "============================================="
echo -e "${GREEN}M9 verification complete.${NC}"
echo ""
echo "Expected results:"
echo "  - Budget exceeded metric > baseline"
echo "  - Fallback metric > 0"
echo "  - Audit row with error_type=BUDGET_EXCEEDED"
echo "  - Decision was produced (deterministic fallback)"
echo "============================================="
