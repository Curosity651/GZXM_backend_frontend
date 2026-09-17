"""Generate B's local TypeScript types from the frozen OpenAPI; --check detects drift."""
import argparse
import json
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[3]
SPEC = yaml.safe_load((ROOT / 'docs/api/openapi.yaml').read_text(encoding='utf-8'))
SCHEMAS = SPEC['components']['schemas']
NAMES = set()


def render(schema):
    if '$ref' in schema:
        name = schema['$ref'].split('/')[-1]
        NAMES.add(name)
        return name
    for key, separator in [('allOf', ' & '), ('oneOf', ' | '), ('anyOf', ' | ')]:
        if key in schema:
            return '(' + separator.join(render(part) for part in schema[key]) + ')'
    if 'enum' in schema:
        return ' | '.join(json.dumps(value, ensure_ascii=False) for value in schema['enum'])
    kind = schema.get('type')
    if isinstance(kind, list):
        return ' | '.join(render({**schema, 'type': item}) for item in kind)
    if kind == 'array':
        return 'Array<' + render(schema['items']) + '>'
    if kind == 'object' or 'properties' in schema:
        props = schema.get('properties', {})
        required = schema.get('required', [])
        fields = [json.dumps(key) + ('' if key in required else '?') + ': ' + render(value) + ';' for key, value in props.items()]
        additional = schema.get('additionalProperties')
        if not props:
            return 'Record<string, ' + (render(additional) if isinstance(additional, dict) else 'unknown') + '>'
        if additional is True:
            fields.append('[key: string]: unknown;')
        return '{ ' + ' '.join(fields) + ' }'
    return {'string': 'string', 'integer': 'number', 'number': 'number', 'boolean': 'boolean', 'null': 'null'}.get(kind, 'unknown')


NAMES.update('Topic TopicWriteRequest TopicPage TopicMembership TimeNode IndicatorDefinition TopicIndicator IndicatorTargetBatch UnitIndicatorAllocation UnitAllocationBatch Achievement AchievementWriteRequest AchievementActionRequest AchievementReviewRequest AchievementPage AchievementProgress SubmissionSnapshot ApprovalRecord Unit'.split())
result = {}
while NAMES - result.keys():
    for name in sorted(NAMES - result.keys()):
        result[name] = render(SCHEMAS[name])
output = '// Generated from docs/api/openapi.yaml. Run generate-research-types.py; do not edit.\n\n'
output += '\n\n'.join(f'export type {name} = {result[name]};' for name in sorted(result)) + '\n'
target = ROOT / 'frontend/src/api/research/contracts.ts'
parser = argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
if args.check:
    assert target.read_text(encoding='utf-8') == output, 'Research types differ from OpenAPI; regenerate them'
    print(f'PASS {len(result)} generated B contract types')
else:
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(output, encoding='utf-8')
    print(f'Generated {len(result)} B contract types')
