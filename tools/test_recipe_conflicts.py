"""Check the retired-rock cuts against Mineralogy's actual crafting recipes."""

import hashlib
import json
import os
from pathlib import Path
import unittest

from prepare_1_14_recipes import native_conflict, prepare

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tools/fixtures/mineralogy-1.19.4-crafting.json'
FIXTURE_SHA256 = '62AC8B6CC818D17B22A8631CCF7A504B39387E5C8143BE4B6D342F114E0A03E2'


class MineralogyRecipeChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        data = FIXTURE.read_bytes()
        if hashlib.sha256(data).hexdigest().upper() != FIXTURE_SHA256:
            raise AssertionError('The locked native crafting fixture changed')
        cls.snapshot = json.loads(data)
        cls.recipes = list(cls.snapshot['recipes'].values())
        cls.tags = cls.snapshot['tags']

    def test_native_object_tag_entries_are_resolved_without_losing_conflicts(self):
        recipe = {'type': 'minecraft:crafting_shaped', 'pattern': ['X'],
                  'key': {'X': {'tag': 'test:rock'}}, 'result': {'item': 'test:other'}}
        tags = {'test:rock': [{'id': '#test:nested', 'required': False}],
                'test:nested': [{'id': 'mineralogy:gabbro', 'required': False}]}
        self.assertTrue(native_conflict(['X'], 'mineralogy:gabbro', ('test:piece', 1), [recipe], tags))
        self.assertFalse(native_conflict(['X'], 'mineralogy:shale', ('test:piece', 1), [recipe], tags))
        tags['test:nested'] = [{'required': False}]
        with self.assertRaises(ValueError):
            native_conflict(['X'], 'mineralogy:gabbro', ('test:piece', 1), [recipe], tags)

    def test_native_tagged_stairs_reproduce_the_original_conflict(self):
        for ingredient in self.snapshot['covered_full_items']:
            with self.subTest(ingredient=ingredient):
                self.assertTrue(native_conflict(['X  ', 'XX ', 'XXX'], ingredient,
                                               ('skysbuildingpiecesbop:old_stairs', 4),
                                               self.recipes, self.tags))

    def test_standalone_and_mineralogy_keep_the_same_recipe_ids(self):
        resources = ROOT / 'src/main/resources'
        default_core = ROOT.parent.parent / 'SkysBuildingPieces1211NEO/SkysBuildingPieces/src/main/resources'
        core = Path(os.environ.get('BUILDING_PIECES_CORE_RESOURCES', str(default_core)))
        normal, _ = prepare(resources, core, self.recipes, self.tags)
        fallback, _ = prepare(resources, core, self.recipes, self.tags, True)
        self.assertEqual(normal.keys(), fallback.keys())

    def test_retired_materials_use_the_replacement_materials_recipes(self):
        resources = ROOT / 'src/main/resources'
        default_core = ROOT.parent.parent / 'SkysBuildingPieces1211NEO/SkysBuildingPieces/src/main/resources'
        core = Path(os.environ.get('BUILDING_PIECES_CORE_RESOURCES', str(default_core)))
        recipes, advancements = prepare(resources, core, self.recipes, self.tags)
        materials = json.loads((resources / 'assets/skysbuildingpiecesbop/catalogue/materials.json').read_text())
        retired = [material['id'].replace(':', '/') + '/' for material in materials if material.get('replacement')]
        self.assertTrue(retired)
        for prefix in retired:
            self.assertFalse(any(identity.startswith(prefix) for identity in recipes), prefix)
            self.assertFalse(any(identity.startswith('recipes/' + prefix) for identity in advancements), prefix)

    def test_packaged_cuts_do_not_conflict_with_native_recipes(self):
        checked = 0
        for path in (ROOT / 'src/main/resources/data/skysbuildingpieces/recipe/biomesoplenty').rglob('*.json'):
            value = json.loads(path.read_text())
            recipe = value['recipes'][0] if value.get('type') == 'skysbuildingpiecesbop:material_recipe' else value
            if 'pattern' not in recipe or recipe.get('key', {}).keys() != {'X'}:
                continue
            ingredient = recipe['key']['X'].get('item', '')
            if ingredient not in self.snapshot['covered_full_items']:
                continue
            result = recipe['result']
            with self.subTest(recipe=path.relative_to(ROOT).as_posix()):
                self.assertFalse(native_conflict(recipe['pattern'], ingredient,
                                                (result['id'], result.get('count', 1)),
                                                self.recipes, self.tags))
            checked += 1
        self.assertGreaterEqual(checked, 7)


if __name__ == '__main__':
    unittest.main()
