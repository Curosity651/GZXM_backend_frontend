"""Check step 4 real MySQL/MockMvc responses against the authoritative OpenAPI."""
from pathlib import Path
import json
import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import OpenAPIV31SpecValidator

root = Path(__file__).resolve().parents[3]
spec = yaml.safe_load((root / "docs/api/openapi.yaml").read_text(encoding="utf-8"))
OpenAPIV31SpecValidator(spec).validate()
samples = json.loads((root / "backend/target/indicator-contract-responses.json").read_text(encoding="utf-8"))
expected = {"listTimeNodes", "listIndicatorDefinitions", "listTopicIndicatorTargets", "saveTopicIndicatorTargets", "publishTopicIndicatorTargets"}
assert set(samples) == expected, "Must cover all five step 4 operations"
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
print("PASS all five real step 4 responses; this does not replace business tests or A/C review")
