"""Validate all three step 5 responses exported from real MySQL/MockMvc tests."""
from pathlib import Path
import json
import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import OpenAPIV31SpecValidator

root = Path(__file__).resolve().parents[3]
spec = yaml.safe_load((root / "docs/api/openapi.yaml").read_text(encoding="utf-8"))
OpenAPIV31SpecValidator(spec).validate()
samples = json.loads((root / "backend/target/allocation-contract-responses.json").read_text(encoding="utf-8"))
expected = {"listUnitAllocations", "saveUnitAllocations", "publishUnitAllocations"}
assert set(samples) == expected, "Must cover all three step 5 operations"
for item in spec["paths"].values():
    for method, operation in item.items():
        if method not in {"get", "put", "post"} or operation.get("operationId") not in expected:
            continue
        name = operation["operationId"]
        sample = samples[name]
        response = operation["responses"][sample["status"]]
        if sample["status"] == "204":
            assert "body" not in sample and "content" not in response
        else:
            schema = response["content"]["application/json"]["schema"]
            Draft202012Validator({**schema, "components": spec["components"]}, format_checker=FormatChecker()).validate(sample["body"])
        print(f"PASS {name} HTTP {sample['status']}")
print("PASS all three real step 5 responses; business tests and A/C review remain separate evidence")
