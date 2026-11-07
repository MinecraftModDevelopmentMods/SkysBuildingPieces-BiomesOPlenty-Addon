"""Build exact metadata mappings from a real 1.12 registry export and the fixed catalogue."""
import argparse
import json
from pathlib import Path


def generate(source, materials):
    result = {}
    for material in materials:
        for role, binding in material['legacy_blocks'].items():
            if role not in ('full', 'slab', 'stairs', 'fence', 'fence_gate'):
                continue
            if isinstance(binding, str):
                binding = {'id': binding, 'meta': 0}
            name = binding['id']
            if not name.startswith('biomesoplenty:'):
                continue
            for row in source:
                if row['id'] != name:
                    continue
                metadata = row['meta']
                if role == 'full' and metadata != binding.get('meta', 0):
                    continue
                if role == 'slab' and metadata & 7 != binding.get('meta', 0):
                    continue
                converted = dict(row, material=material['id'], role=role)
                key = (name, metadata)
                if key in result and result[key] != converted:
                    raise ValueError('Conflicting native palette mapping: ' + str(key))
                result[key] = converted
                if role == 'slab':
                    double_id = 'biomesoplenty:double_' + name.split(':', 1)[1]
                    for double in source:
                        if double['id'] == double_id and double['meta'] & 7 == metadata & 7:
                            key = (double_id, double['meta'])
                            result[key] = dict(double, material=material['id'], role='full')
    return [result[key] for key in sorted(result)]


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source_export', type=Path)
    args = parser.parse_args()
    catalogue = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/skysbuildingpiecesbop/catalogue'
    rows = generate(json.loads(args.source_export.read_text(encoding='utf-8')),
                    json.loads((catalogue / 'materials.json').read_text(encoding='utf-8')))
    (catalogue / 'legacy_states.json').write_text(json.dumps(rows, indent=2) + '\n', encoding='utf-8')
    print('Exact native state mappings:', len(rows))
