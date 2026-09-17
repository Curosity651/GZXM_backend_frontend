"""B detail form: field types/enums from OpenAPI, Chinese labels from existing B forms."""
import argparse
import json
import re
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[3]
schemas = yaml.safe_load((root / 'docs/api/openapi.yaml').read_text(encoding='utf-8'))['components']['schemas']
groups = {}
for code, name in [('PAPER', 'Paper'), ('PATENT', 'Patent'), ('COPYRIGHT', 'Copyright'), ('STANDARD', 'Standard'), ('TALENT', 'Talent')]:
    source = (root / f'frontend/src/components/achievement/{name}Fields.tsx').read_text(encoding='utf-8')
    labels = dict((field, label) for label, field in re.findall(r'<Form.Item\s+label="([^"]+)"\s+name="([^"]+)"', source))
    labels.update({'remarks': '备注', 'externalSubmissionNumber': '投稿编号'})
    fields = []
    for key, schema in schemas[name + 'AchievementDetail']['properties'].items():
        label = labels.get(key)
        if label is None:
            raise ValueError(f'Missing Chinese label: {code}.{key}')
        enum = schema.get('enum')
        if not enum:
            enum = next((part['enum'] for part in schema.get('anyOf', []) if 'enum' in part and any(item not in ('', None) for item in part['enum'])), None)
        kind = 'boolean' if key.startswith('is') and key[2:3].isupper() else 'date' if key.endswith('Date') else 'text'
        fields.append({'key': key, 'label': label, 'kind': kind, 'maxLength': schema.get('maxLength', 500), **({'options': [item for item in enum if item not in ('', None)]} if enum else {})})
    groups[code] = fields
text = '// Generated from OpenAPI and existing B form labels; run generate-research-fields.py.\n'
text += 'export interface DetailField { key: string; label: string; kind: string; maxLength: number; options?: string[] }\n'
text += 'export const detailFields: Record<string, DetailField[]> = ' + json.dumps(groups, ensure_ascii=False, indent=2) + ';\n'
target = root / 'frontend/src/pages/achievement/detail-fields.ts'
parser = argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
if parser.parse_args().check:
    assert target.read_text(encoding='utf-8') == text, 'Regenerate B detail fields'
    print('PASS five B form field groups match OpenAPI')
else:
    target.write_text(text, encoding='utf-8')
