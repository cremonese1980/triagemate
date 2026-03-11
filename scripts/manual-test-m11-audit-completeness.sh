#!/usr/bin/env bash
# M11 — Audit completeness manual verification
#
# Prerequisites:
#   docker compose up --build
#   AI must be enabled (AI_ENABLED=true)
#   For success audit: AI provider must be reachable (ollama or anthropic)
#   For error audit: tight budget to force BUDGET_EXCEEDED
#
# Usage:
#   bash scripts/manual-test-m11-audit-completeness.sh

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
echo "  M11 — Audit Completeness Verification"
echo "============================================="
echo ""

# Step 1: Check services
echo -e "${YELLOW}[1/6] Checking services...${NC}"
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

# Step 2: Count existing audit rows
echo -e "${YELLOW}[2/6] Counting existing audit rows...${NC}"
BEFORE_COUNT=$(docker exec "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_DB}" -t -c \
    "SELECT COUNT(*) FROM ai_decision_audit;" | tr -d ' ')
echo "  Existing rows: ${BEFORE_COUNT}"
echo ""

# Step 3: Send event for success path (AI should process normally)
echo -e "${YELLOW}[3/6] Sending event for AI success path...${NC}"
SUCCESS_REQ_ID="m11-success-$(date +%s)"
RESPONSE=$(curl -sf -X POST "${INGEST_URL}/api/ingest/messages" \
    -H "Content-Type: application/json" \
    -H "X-Request-Id: ${SUCCESS_REQ_ID}" \
    -d '{
        "channel": "device-telemetry",
        "content": "Normal device telemetry event for M11 success audit test"
    }')
echo "  Response: ${RESPONSE}"
echo "  Request-Id: ${SUCCESS_REQ_ID}"
echo ""

# Step 4: Wait for processing
echo -e "${YELLOW}[4/6] Waiting 8s for AI processing...${NC}"
sleep 8

# Step 5: Query success audit row
echo -e "${YELLOW}[5/6] Querying ai_decision_audit for completeness...${NC}"
echo ""

echo "--- Success audit rows (latest 3) ---"
docker exec "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_DB}" -c \
    "SELECT decision_id, event_id, provider, model, model_version,
            prompt_version, confidence, suggested_classification,
            accepted_by_validator, latency_ms, cost_usd,
            input_tokens, output_tokens, error_type
     FROM ai_decision_audit
     WHERE error_type IS NULL
     ORDER BY created_at DESC
     LIMIT 3;"
echo ""

echo "--- Error audit rows (latest 3) ---"
docker exec "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_DB}" -c \
    "SELECT decision_id, event_id, error_type, error_message, created_at
     FROM ai_decision_audit
     WHERE error_type IS NOT NULL
     ORDER BY created_at DESC
     LIMIT 3;"
echo ""

# Step 6: Verify field completeness
echo -e "${YELLOW}[6/6] Checking field completeness...${NC}"

echo "--- Success row field check (non-null expected fields) ---"
docker exec "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_DB}" -c \
    "SELECT
       CASE WHEN decision_id IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS decision_id,
       CASE WHEN event_id IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS event_id,
       CASE WHEN provider IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS provider,
       CASE WHEN model IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS model,
       CASE WHEN confidence IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS confidence,
       CASE WHEN latency_ms IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS latency_ms,
       CASE WHEN error_type IS NULL THEN 'OK' ELSE 'UNEXPECTED' END AS no_error
     FROM ai_decision_audit
     WHERE error_type IS NULL
     ORDER BY created_at DESC
     LIMIT 1;"
echo ""

echo "--- Error row field check ---"
docker exec "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_DB}" -c \
    "SELECT
       CASE WHEN decision_id IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS decision_id,
       CASE WHEN event_id IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS event_id,
       CASE WHEN error_type IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS error_type,
       CASE WHEN error_message IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS error_message,
       CASE WHEN provider IS NULL THEN 'OK' ELSE 'UNEXPECTED' END AS no_provider
     FROM ai_decision_audit
     WHERE error_type IS NOT NULL
     ORDER BY created_at DESC
     LIMIT 1;"

echo ""
echo "============================================="
echo -e "${GREEN}M11 verification complete.${NC}"
echo ""
echo "Expected results for success row:"
echo "  - provider, model, prompt_version, confidence, latency_ms: NOT NULL"
echo "  - suggested_classification, accepted_by_validator: populated"
echo "  - error_type: NULL"
echo "  - decision_id, event_id: correlated to original event"
echo ""
echo "Expected results for error row:"
echo "  - error_type, error_message: NOT NULL"
echo "  - decision_id, event_id: correlated to original event"
echo "  - provider, confidence: NULL (no AI call made)"
echo "============================================="
