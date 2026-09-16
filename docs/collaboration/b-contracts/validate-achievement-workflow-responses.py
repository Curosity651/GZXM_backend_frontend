"""Validate the three real workflow API responses; file integration remains pending A."""
from pathlib import Path
import json
import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import OpenAPIV31SpecValidator

root = Path(__file__).resolve().parents[3]
spec = yaml.safe_load((root / "docs/api/openapi.yaml").read_text(encoding="utf-8"))
OpenAPIV31SpecValidator(spec).validate()
samples = json.loads((root / "backend/target/achievement-workflow-contract-responses.json").read_text(encoding="utf-8"))
expected = {"executeAchievementAction", "reviewAchievement", "listAchievementSnapshots"}
assert set(samples) == expected, "Must cover all three step 7 workflow operations"
for item in spec["paths"].values():
    for method, operation in item.items():
        if method not in {"get", "put", "post"} or operation.get("operationId") not in expected:
            continue
        name = operation["operationId"]
        sample = samples[name]
        schema = operation["responses"][sample["status"]]["content"]["application/json"]["schema"]
        Draft202012Validator({**schema, "components": spec["components"]}, format_checker=FormatChecker()).validate(sample["body"])
        print(f"PASS {name} HTTP {sample['status']}")
print("PASS three real workflow responses; material integration and A/C review are not certified")
