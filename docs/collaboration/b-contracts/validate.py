"""Read-only checks for B's review package, not runtime or business acceptance tests.

Dependency: openapi-spec-validator==0.7.2 (install in a temporary virtualenv).
Run from any working directory; all repository paths resolve from this file.
"""

from pathlib import Path
import re
import sys

import yaml
from jsonschema import Draft202012Validator
from openapi_spec_validator import OpenAPIV31SpecValidator


ROOT = Path(__file__).resolve().parents[3]
HTTP_METHODS = {"get", "put", "post", "delete", "patch", "head", "options", "trace"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def walk_refs(value, spec):
    if isinstance(value, dict):
        if "$ref" in value:
            ref = value["$ref"]
            require(ref.startswith("#/"), f"Unexpected external reference: {ref}")
            target = spec
            for token in ref[2:].split("/"):
                target = target[token.replace("~1", "/").replace("~0", "~")]
        for item in value.values():
            walk_refs(item, spec)
    elif isinstance(value, list):
        for item in value:
            walk_refs(item, spec)


def check():
    spec = yaml.safe_load((ROOT / "docs/api/openapi.yaml").read_text(encoding="utf-8-sig"))
    package = Path(__file__).with_name("README.md").read_text(encoding="utf-8")
    sql = (ROOT / "docs/database/mysql-schema.sql").read_text(encoding="utf-8-sig")
    require(spec["openapi"] == "3.1.0", "Unexpected OpenAPI version")
    OpenAPIV31SpecValidator(spec).validate()
    walk_refs(spec, spec)
    print("PASS OpenAPI 3.1 specification and local references")

    operations = {}
    owned = {}
    for path, path_item in spec["paths"].items():
        for method, operation in path_item.items():
            if method not in HTTP_METHODS:
                continue
            op_id = operation["operationId"]
            require(op_id not in operations, f"Duplicate operationId: {op_id}")
            operations[op_id] = (method.upper(), path)
            if set(operation["tags"]) & {"Topics", "Indicators", "Achievements"}:
                owned[op_id] = (method.upper(), path)
    require(len(operations) == 69, f"Integrated operation count changed: {len(operations)}")
    require(len(owned) == 24, f"B operation count changed: {len(owned)}")
    entries = re.findall(r"^\| (\w+) \| (GET|PUT|POST|DELETE|PATCH) \| (\S+) \|", package, re.M)
    require(len(entries) == len(owned), "Review table has missing or duplicate operations")
    require({op: (method, path) for op, method, path in entries} == owned,
            "Review table differs from OpenAPI paths/methods/operationIds")
    print("PASS 69 unique operations; all 24 B operations mapped exactly")

    columns = {
        "biz_project": ["id", "code", "enabled"],
        "biz_topic": ["project_id", "lead_unit_id", "status", "enabled", "record_version", "created_by", "updated_by"],
        "biz_topic_unit_membership": ["topic_id", "unit_id", "membership_type", "enabled", "active_lead_topic_key"],
        "time_node": ["project_id", "code", "deadline", "sort_order"],
        "indicator_definition": ["achievement_type", "category", "unit_name", "match_rule"],
        "topic_indicator": ["node_id", "indicator_definition_id", "target_quantity", "publish_version"],
        "unit_indicator_allocation": ["membership_id", "unit_id", "topic_indicator_id", "publish_version"],
        "achievement": ["membership_id", "unit_id", "detail_json", "record_version", "submitted_version", "counts_to_indicator"],
        "achievement_material": ["file_id", "material_type", "material_status", "file_version"],
        "approval_record": ["business_type", "stage", "approval_level", "decision", "submitted_version"],
        "submission_snapshot": ["business_type", "business_id", "submitted_version", "payload_json"],
    }
    require(len(re.findall(r"CREATE TABLE \w+", sql)) == 27, "Baseline table count changed")
    for table, fields in columns.items():
        match = re.search(rf"CREATE TABLE {table} \((.*?)\) ENGINE", sql, re.S)
        require(match is not None, f"Missing table: {table}")
        require(f"`{table}`" in package, f"Table undocumented: {table}")
        for field in fields:
            require(re.search(rf"^  {field}\s", match[1], re.M), f"Missing column: {table}.{field}")
    print("PASS 27 baseline tables; 11 B/shared table field mappings")

    schemas = spec["components"]["schemas"]
    cases = [
        ("TopicWriteRequest", {"code": "TEST", "name": "Synthetic", "leadUnitId": "1"}, True),
        ("TopicWriteRequest", {"code": "TEST", "name": "Synthetic"}, False),
        ("TopicWriteRequest", {"code": "TEST", "name": "Synthetic", "leadUnitId": 1}, False),
        ("IndicatorTargetBatch", {"nodeId": "1", "targets": [{"indicatorDefinitionId": "1", "targetQuantity": 0}]}, True),
        ("IndicatorTargetBatch", {"nodeId": "1", "targets": [{"indicatorDefinitionId": "1", "targetQuantity": -1}]}, False),
        ("IndicatorTargetBatch", {"nodeId": "1", "targets": [{"indicatorDefinitionId": "1", "targetQuantity": 1.5}]}, False),
        ("UnitAllocationBatch", {"nodeId": "1", "allocations": [{"unitId": "1", "indicatorDefinitionId": "1", "targetQuantity": 2}]}, True),
        ("UnitAllocationBatch", {"nodeId": "1", "allocations": [{"indicatorDefinitionId": "1", "targetQuantity": 2}]}, False),
        ("AchievementActionRequest", {"action": "SUBMIT_PRE_REVIEW", "recordVersion": 1}, True),
        ("AchievementActionRequest", {"action": "SUBMIT_PRE_REVIEW"}, False),
        ("AchievementActionRequest", {"action": "FORCE_APPROVE"}, False),
        ("ReviewRequest", {"decision": "APPROVE"}, True),
        ("ReviewRequest", {"decision": "APPROVED"}, False),
        ("AchievementReviewRequest", {"decision": "APPROVE", "recordVersion": 2, "submittedVersion": 1}, True),
        ("AchievementReviewRequest", {"decision": "APPROVE", "submittedVersion": 1}, False),
    ]
    for schema, payload, expected in cases:
        require(Draft202012Validator(schemas[schema]).is_valid(payload) == expected,
                f"Unexpected schema result for {schema}: {payload}")
    print(f"PASS {len(cases)} positive/negative request-schema examples (not authorization tests)")

    for number in range(1, 14):
        require(re.search(rf"^\| D{number:02d} \|", package, re.M), f"Decision D{number:02d} missing")
    for link in re.findall(r"\]\(([^)]+)\)", package):
        if "://" not in link and not link.startswith("#"):
            require((Path(__file__).parent / link.split("#")[0]).is_file(), f"Broken local link: {link}")
    print("PASS 13 decision entries and review-package local links")
    print("LIMIT: no runtime API, MySQL migration, permission enforcement, or human approval verified")


if __name__ == "__main__":
    try:
        check()
    except Exception as exc:
        print(f"FAIL {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(1)
