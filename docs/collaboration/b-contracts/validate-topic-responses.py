"""Validate actual MySQL/MockMvc responses exported by TopicIntegrationTest."""
from pathlib import Path
import json
import sys

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import OpenAPIV31SpecValidator

root = Path(__file__).resolve().parents[3]
spec = yaml.safe_load((root / "docs/api/openapi.yaml").read_text(encoding="utf-8"))
OpenAPIV31SpecValidator(spec).validate()
path = root / "backend/target/topic-contract-responses.json"
if not path.is_file():
    sys.exit("FAIL: run the full passing Maven TopicIntegrationTest before validating responses")
samples = json.loads(path.read_text(encoding="utf-8"))
operations = {
    operation["operationId"]: operation
    for item in spec["paths"].values()
    for method, operation in item.items()
    if method in {"get", "post", "put", "patch", "delete"} and "Topics" in operation.get("tags", [])
}
if set(samples) != set(operations) or len(samples) != 8:
    sys.exit("FAIL: actual response set must cover all eight Topics operations")
for name, operation in operations.items():
    sample = samples[name]
    response = operation["responses"][sample["status"]]
    schema = response["content"]["application/json"]["schema"]
    # Keep local references rooted at the original document's components.
    validator = Draft202012Validator({**schema, "components": spec["components"]}, format_checker=FormatChecker())
    errors = list(validator.iter_errors(sample["body"]))
    if errors:
        sys.exit(f"FAIL {name}: {errors[0].message}")
    print(f"PASS {name} HTTP {sample['status']} response schema")
print("PASS OpenAPI 3.1 and all 8 real response examples; run Maven separately to verify business assertions")
