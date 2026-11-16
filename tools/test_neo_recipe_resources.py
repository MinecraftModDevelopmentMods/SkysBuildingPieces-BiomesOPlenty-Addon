"""Keep optional-material recipe unlocks native to NeoForge."""
import json
from pathlib import Path
import unittest
from neo_recipe_resources import convert

class ConditionalUnlockChecks(unittest.TestCase):
    def test_checked_in_unlocks_have_no_forge_only_branches(self):
        root = Path(__file__).resolve().parents[1] / 'src/main/resources/data'
        for path in root.glob('*/advancement/**/*.json'):
            self.assertNotIn('"forge:', path.read_text(encoding='utf-8'), str(path))

    def test_child_unlocks_merge_optional_material_predicates(self):
        def unlock(item):
            return {'criteria': {'has_ingredient': {'trigger': 'minecraft:inventory_changed',
                    'conditions': {'items': [{'items': [item]}]}}},
                    'requirements': [['has_ingredient']], 'rewards': {'recipes': ['example:slab']}}
        source = unlock('mineralogy:gabbro')
        source['forge:children'] = [
            {'forge:condition': {'type': 'forge:item_exists', 'item': 'mineralogy:gabbro'}},
            {'forge:condition': {'type': 'forge:not', 'value': {'type': 'forge:item_exists',
                'item': 'mineralogy:gabbro'}}, 'child': unlock('example:retired_gabbro')}]
        path = 'data/example/advancement/recipes/slab.json'
        result = convert({path: source})
        self.assertNotIn('"forge:', json.dumps(result))
        self.assertEqual(source['rewards'], result[path]['rewards'])
        tag = next(value for key, value in result.items() if '/tags/item/' in key)
        self.assertEqual({'mineralogy:gabbro', 'example:retired_gabbro'}, {row['id'] for row in tag['values']})
        self.assertTrue(all(row['required'] is False for row in tag['values']))
        self.assertEqual(result, convert(result))
