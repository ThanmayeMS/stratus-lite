#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required for JSON parsing" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local output="$4"

  if [[ -n "$body" ]]; then
    curl --fail --silent --show-error \
      --request "$method" \
      --header "Content-Type: application/json" \
      --data "$body" \
      "$BASE_URL$path" > "$output"
  else
    curl --fail --silent --show-error \
      --request "$method" \
      "$BASE_URL$path" > "$output"
  fi
}

json_get() {
  local file="$1"
  local expression="$2"
  python3 - "$file" "$expression" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    data = json.load(handle)

value = data
for part in sys.argv[2].split("."):
    if part == "":
        continue
    if part.isdigit():
        value = value[int(part)]
    else:
        value = value[part]

print(value)
PY
}

echo "Running Stratus Lite smoke test against $BASE_URL"
echo "Resetting demo state for repeatable output"

request POST "/api/admin/reset" "" "$tmp_dir/reset.json"
echo "Demo state reset"

request GET "/api/cells" "" "$tmp_dir/cells.json"
cell_count="$(python3 - "$tmp_dir/cells.json" <<'PY'
import json, sys
print(len(json.load(open(sys.argv[1], encoding="utf-8"))))
PY
)"
echo "Cells available: $cell_count"

request POST "/api/workloads" '{
  "tenantId": "tenant-smoke",
  "region": "us-east",
  "tier": "STANDARD",
  "demand": {
    "cpuCores": 2,
    "memoryGb": 4,
    "storageGb": 50,
    "iops": 1000
  }
}' "$tmp_dir/workload.json"
workload_id="$(json_get "$tmp_dir/workload.json" "id")"
echo "Created workload: $workload_id"

request POST "/api/workloads/$workload_id/place?strategy=LEAST_ALLOCATED" "" "$tmp_dir/placement.json"
source_cell_id="$(json_get "$tmp_dir/placement.json" "selectedCellId")"
echo "Placed workload on: $source_cell_id"

request POST "/api/simulations/cell-failure" "{
  \"cellId\": \"$source_cell_id\"
}" "$tmp_dir/failure.json"
echo "Simulated failure for: $source_cell_id"

request GET "/api/rebalance/recommendations" "" "$tmp_dir/recommendations.json"
python3 - "$tmp_dir/recommendations.json" "$workload_id" "$tmp_dir/recommendation.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    recommendations = json.load(handle)

workload_id = sys.argv[2]
match = next((item for item in recommendations if item["workloadId"] == workload_id), None)
if match is None:
    raise SystemExit(f"No rebalance recommendation found for {workload_id}")

with open(sys.argv[3], "w", encoding="utf-8") as handle:
    json.dump(match, handle)
PY
target_cell_id="$(json_get "$tmp_dir/recommendation.json" "targetCellId")"
echo "Recommended target: $target_cell_id"

request GET "/api/reconciler/status" "" "$tmp_dir/reconciler-status.json"
reconciler_decision="$(json_get "$tmp_dir/reconciler-status.json" "decision")"
if [[ "$reconciler_decision" != "ACTION_REQUIRED" ]]; then
  echo "Expected reconciler decision ACTION_REQUIRED, got $reconciler_decision" >&2
  exit 1
fi
echo "Reconciler decision: $reconciler_decision"

request POST "/api/reconciler/run" "" "$tmp_dir/reconciler-run.json"
echo "Reconciler sweep recorded"

request POST "/api/rebalance/executions" "$(cat "$tmp_dir/recommendation.json")" "$tmp_dir/execution.json"
execution_id="$(json_get "$tmp_dir/execution.json" "executionId")"
execution_state="$(json_get "$tmp_dir/execution.json" "state")"
if [[ "$execution_state" != "RUNNING" ]]; then
  echo "Expected execution state RUNNING, got $execution_state" >&2
  exit 1
fi
execution_status="$(json_get "$tmp_dir/execution.json" "status")"
if [[ "$execution_status" != "ACTIVE" ]]; then
  echo "Expected execution status ACTIVE, got $execution_status" >&2
  exit 1
fi
echo "Executed migration: $workload_id -> $target_cell_id"

request GET "/api/rebalance/executions" "" "$tmp_dir/executions.json"
python3 - "$tmp_dir/executions.json" "$execution_id" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    executions = json.load(handle)

execution_id = sys.argv[2]
match = next((item for item in executions if item["id"] == execution_id), None)
if match is None:
    raise SystemExit(f"No execution history record found for {execution_id}")
if match["status"] != "ACTIVE":
    raise SystemExit(f"Expected history status ACTIVE, got {match['status']}")
PY
echo "Execution history recorded: $execution_id"

request GET "/api/insights/capacity" "" "$tmp_dir/insight.json"
risk_level="$(json_get "$tmp_dir/insight.json" "riskLevel")"
risk_score="$(json_get "$tmp_dir/insight.json" "riskScore")"
echo "Capacity risk: $risk_level ($risk_score)"

request GET "/api/metrics/operations" "" "$tmp_dir/metrics.json"
total_migrations="$(json_get "$tmp_dir/metrics.json" "totalMigrations")"
if [[ "$total_migrations" != "1" ]]; then
  echo "Expected one recorded migration, got $total_migrations" >&2
  exit 1
fi
echo "Operational metrics recorded migrations: $total_migrations"

request GET "/api/events?limit=10" "" "$tmp_dir/events.json"
event_count="$(python3 - "$tmp_dir/events.json" <<'PY'
import json, sys
print(len(json.load(open(sys.argv[1], encoding="utf-8"))))
PY
)"
echo "Recent audit events: $event_count"

echo "Smoke test passed"
