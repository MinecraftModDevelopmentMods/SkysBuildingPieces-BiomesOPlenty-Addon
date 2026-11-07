"""Generate native BOP cutting recipes while keeping their existing identities.

The catalogue keeps source-world identities; recipes use the flattened item IDs.
Optional inputs and matching advancements share the same Forge conditions.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

from native_assets import format_json

PATTERNS = {
    'slab': (6, ['XXX']), 'vertical_slab': (6, ['X', 'X', 'X']),
    'step': (4, ['X ', ' X']), 'soil_step': (8, ['X ', ' X']),
    'rock_step': (12, ['X  ', ' X ', '  X']), 'corner': (4, ['XX']),
    'stairs': (4, ['X  ', 'XX ', 'XXX']), 'wall': (6, ['XX', 'XX', 'XX']),
    'pane': (16, ['XXX', 'XXX']), 'rotate': (1, ['X'])
}


def item_id(spec):
    return spec if isinstance(spec, str) else spec['id']


def exists(item):
    return {'type': 'forge:item_exists', 'item': item}


def canonical_pattern(rows):
    occupied = [(column, row) for row, line in enumerate(rows)
                for column, symbol in enumerate(line) if symbol != ' ']
    if not occupied:
        raise ValueError('Empty crafting pattern')
    left = min(column for column, _ in occupied)
    top = min(row for _, row in occupied)
    width = max(column for column, _ in occupied) - left
    return tuple(sorted((column - left, row - top) for column, row in occupied)), width


def native_conflict(rows, ingredient, output, native_recipes, tags):
    def accepts(value, visited=frozenset()):
        if isinstance(value, list):
            return any(accepts(entry, visited) for entry in value)
        if 'item' in value:
            return value['item'] == ingredient
        if 'tag' in value:
            tag = value['tag']
            if tag in visited:
                raise ValueError('Circular native ingredient tag: ' + tag)
            for entry in tags.get(tag, []):
                identity = entry.get('id') if isinstance(entry, dict) else entry
                if not isinstance(identity, str):
                    raise ValueError('Malformed native ingredient tag: ' + tag)
                nested = {'tag': identity[1:]} if identity.startswith('#') else {'item': identity}
                if accepts(nested, visited | {tag}):
                    return True
            return False
        return False
    pattern, width = canonical_pattern(rows)
    reflected = tuple(sorted((width - column, row) for column, row in pattern))
    for recipe in native_recipes:
        if recipe.get('type') == 'minecraft:crafting_shapeless':
            inputs = recipe['ingredients']
            if len(inputs) == len(pattern) and all(accepts(value) for value in inputs):
                result = recipe['result']
                result = {'item': result} if isinstance(result, str) else result
                if (result.get('id', result.get('item')), result.get('count', 1)) != output:
                    return True
            continue
        if recipe.get('type') != 'minecraft:crafting_shaped':
            continue
        other, _ = canonical_pattern(recipe['pattern'])
        if other not in (pattern, reflected):
            continue
        inputs = list(recipe['key'].values())
        if all(accepts(value) for value in inputs):
            result = recipe['result']
            result = {'item': result} if isinstance(result, str) else result
            if (result.get('id', result.get('item')), result.get('count', 1)) != output:
                return True
    return False


def prepare(resources, core_resources, native_recipes=(), tags=None, fallback=False):
    tags = {} if tags is None else tags
    recipes = {}
    advancements = {}
    for module, assets in [('biomesoplenty', 'skysbuildingpiecesbop')]:
        catalogue = resources / 'assets' / assets / 'catalogue'
        materials = json.loads((catalogue / 'materials.json').read_text(encoding='utf-8'))
        palettes = json.loads((catalogue / 'palettes.json').read_text(encoding='utf-8'))
        core_catalogue = core_resources / 'assets/skysbuildingpieces/catalogue'
        core_materials = json.loads((core_catalogue / 'materials.json').read_text(encoding='utf-8'))
        core_palettes = json.loads((core_catalogue / 'palettes.json').read_text(encoding='utf-8'))
        definitions = {material['id']: material for material in core_materials}
        definitions.update({material['id']: material for material in materials})
        owners = {material['id']: 'skysbuildingpieces' for material in core_materials}
        owners.update({material['id']: assets for material in materials})
        if fallback:
            for material in materials:
                full = material['blocks']['full']
                if isinstance(full, dict) and full['id'].startswith('mineralogy:'):
                    rock = full['id'].split(':', 1)[1]
                    material['blocks']['full'] = {'id': 'skysbuildingpiecesbop:retired_' + rock}
        available = {(material, palette['shape'].lower()) for palette in palettes + core_palettes
                     for material in palette['materials']}

        legacy_palettes = {}
        for palette in palettes:
            for slot, material in enumerate(palette['materials']):
                name = ('mineralogy_' if module == 'mineralogy' else '') + palette['id']
                shape = palette['shape'].lower()
                value = (assets + ':' + name, slot * palette['states'])
                legacy_palettes[(material, shape)] = value
                if shape == 'step':
                    legacy_palettes[(material, 'horizontal_step')] = value
                    legacy_palettes[(material, 'vertical_step')] = value

        def source_identity(material, kind, current):
            original = material['legacy_blocks'].get(kind)
            if original is not None:
                name = item_id(original)
                metadata = original.get('meta', 0) if isinstance(original, dict) else 0
                if module == 'mineralogy':
                    metadata = 0  # Native six-way rocks never used inventory subtypes.
            elif (material['id'], kind) in legacy_palettes:
                name, metadata = legacy_palettes[(material['id'], kind)]
            else:
                name, metadata = current, 0
            return name.replace(':', '/') + '_' + str(metadata)

        def piece(material, shape):
            visited = set()
            while material.get('replacement'):
                if material['id'] in visited:
                    raise ValueError('Cyclic replacement')
                visited.add(material['id'])
                material = definitions[material['replacement']]
            blocks = material['blocks']
            if shape in blocks:
                return item_id(blocks[shape])
            stored = shape
            if (material['id'], stored) not in available and shape in ('horizontal_step', 'vertical_step'):
                stored = 'step'
            if (material['id'], stored) not in available:
                return None
            prefix = 'mineralogy_' if module == 'mineralogy' else ''
            return owners[material['id']] + ':' + prefix + material['id'].split(':', 1)[1] + '_' + stored

        def add(path, rows, ingredient, output, count, extra=(), preserve=False):
            if ingredient is None or output is None or ingredient == output:
                return
            conditions = list(extra)
            if module == 'mineralogy':
                conditions.append({'type': 'forge:mod_loaded', 'modid': 'mineralogy'})
            for identity in sorted({ingredient, output}):
                if not identity.startswith('minecraft:'):
                    conditions.append(exists(identity))
            body = {'type': 'skysbuildingpieces:cutting' if preserve else 'minecraft:crafting_shaped',
                    'group': 'skysbuildingpieces/' + path.split('/')[-2],
                    'pattern': rows, 'key': {'X': {'item': ingredient}},
                    'result': {'item': output, 'count': count}}
            if preserve:
                body['preserve_tag'] = True
            if conditions:
                body['conditions'] = conditions
            if path in recipes:
                if recipes[path] != body:
                    raise ValueError('Conflicting generated recipe ID: ' + path)
                return
            recipes[path] = body
            advancement = {
                'criteria': {
                    'has_ingredient': {'trigger': 'minecraft:inventory_changed',
                                       'conditions': {'items': [{'items': [ingredient]}]}},
                    'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked',
                                       'conditions': {'recipe': 'skysbuildingpieces:' + path}}},
                'requirements': [['has_ingredient', 'has_the_recipe']],
                'rewards': {'recipes': ['skysbuildingpieces:' + path]}}
            if conditions:
                advancement['conditions'] = copy.deepcopy(conditions)
            advancements['recipes/' + path] = advancement

        for material in materials:
            if material['type'] == 'path':
                # Paths are made with a shovel, but existing pieces can still
                # be rebuilt into their larger shapes without losing material.
                base = material['id'].replace(':', '/')
                add(base + '/recombine_corner', ['X', 'X'], piece(material, 'corner'),
                    piece(material, 'horizontal_step'), 1)
                for shape in ('horizontal_step', 'vertical_step'):
                    add(base + '/recombine_' + shape, ['X', 'X'], piece(material, shape),
                        piece(material, 'slab'), 1)
                continue
            identity = material['id']
            base = identity.replace(':', '/')
            full_spec = material['blocks']['full']
            half_input = isinstance(full_spec, dict) and full_spec.get('properties', {}).get('type') == 'double'
            full = item_id(material['blocks']['slab'] if half_input else full_spec)
            slab = piece(material, 'slab')
            vertical = piece(material, 'vertical_slab')
            step = piece(material, 'horizontal_step')
            upright_step = piece(material, 'vertical_step')
            corner = piece(material, 'corner')

            def cut(shape, pattern, source, extra=()):
                source_kind = next((kind for kind, item in [
                    ('full', full), ('slab', slab), ('vertical_slab', vertical),
                    ('horizontal_step', step), ('vertical_step', upright_step)] if item == source), 'full')
                output = piece(material, shape)
                if source is None or output is None:
                    return
                count, rows = PATTERNS[pattern]
                if half_input and source == full:
                    source_kind = 'slab'
                    if pattern != 'rotate' and shape in ('stairs', 'wall', 'pane', 'slab', 'vertical_slab'):
                        count //= 2
                recipe_pattern = pattern
                recipe_source = source_identity(material, source_kind, source)
                # Both installation routes use the same cuts and recipe IDs.
                # Check the optional native input even while generating its fallback.
                comparison_source = source
                original_full = material['legacy_blocks'].get('full')
                if fallback and source == full and original_full is not None:
                    original_id = item_id(original_full)
                    if original_id.startswith('mineralogy:'):
                        comparison_source = original_id
                if native_conflict(rows, comparison_source, (output, count), native_recipes, tags):
                    if shape == 'slab' and pattern == 'slab':
                        pattern = 'step'
                        count, rows = PATTERNS[pattern]
                    elif shape == 'stairs' and pattern == 'stairs' and slab is not None:
                        source = slab
                        source_kind = 'slab'
                    else:
                        raise ValueError('No conflict-free cut: ' + identity + ' / ' + shape)
                    if native_conflict(rows, source, (output, count), native_recipes, tags):
                        raise ValueError('Fallback still conflicts: ' + identity + ' / ' + shape)
                path = base + '/' + shape + '/' + recipe_pattern + '/' + recipe_source
                add(path, rows, source, output, count, extra, preserve=pattern == 'rotate')

            if 'slab' not in material['blocks']:
                rotate_only = identity in ('minecraft:snow', 'minecraft:stone', 'minecraft:chiseled_sandstone',
                    'minecraft:smooth_sandstone', 'minecraft:chiseled_red_sandstone',
                    'minecraft:smooth_red_sandstone', 'minecraft:stonebrick_carved',
                    'minecraft:stonebrick_cracked', 'minecraft:stonebrick_mossy')
                cut('slab', 'step' if rotate_only else 'slab', full)
            if slab is None and vertical is not None:
                cut('vertical_slab', 'vertical_slab', full)
            elif identity == 'minecraft:dirt' and vertical is not None:
                cut('vertical_slab', 'vertical_slab', full, [{'type': 'forge:not', 'value': exists(slab)}])
            if not material.get('fullBlockStepRecipe', False):
                if slab is not None:
                    cut('horizontal_step', 'step', slab)
                if vertical is not None:
                    cut('horizontal_step', 'step', vertical)
            if material.get('fullBlockStepRecipe', False):
                cut('horizontal_step', 'rock_step', full)
            elif material['type'] in ('grass', 'dirt'):
                cut('horizontal_step', 'soil_step', full)
            if step is not None:
                cut('corner', 'corner', step)
            if upright_step is not None and upright_step != step:
                cut('corner', 'corner', upright_step)
                cut('vertical_step', 'rotate', step)
                cut('horizontal_step', 'rotate', upright_step)
            for shape in ('stairs', 'wall', 'pane'):
                if shape not in material['blocks']:
                    cut(shape, shape, full)
            if slab is not None and vertical is not None:
                cut('slab', 'rotate', vertical)
            if slab is not None and not half_input:
                add(base + '/slab_recombination', ['XX'], slab, full, 1)
            if corner is not None and step is not None:
                add(base + '/recombine_corner', ['X', 'X'], corner, step, 1)
            for shape, source in [('horizontal_step', step), ('vertical_step', upright_step)]:
                output = slab if slab is not None else vertical
                add(base + '/recombine_' + shape, ['X', 'X'], source, output, 1)
                # Ordinary dirt still has its vertical slab when Grass Slabs is absent.
                if identity == 'minecraft:dirt' and slab is not None and vertical is not None:
                    add(base + '/recombine_' + shape + '_without_grass_slabs', ['X', 'X'], source,
                        vertical, 1, [{'type': 'forge:not', 'value': exists(slab)}])

    for recipe in recipes.values():
        recipe['category'] = 'building'
    return recipes, advancements




def all_conditions(conditions):
    unique = list({json.dumps(condition, sort_keys=True): condition for condition in conditions}.values())
    return unique[0] if len(unique) == 1 else {'type': 'forge:and', 'values': unique}


def native_recipe(recipe):
    if recipe.get('type') == 'forge:conditional':
        for choice in recipe['recipes']:
            choice['forge:condition'] = all_conditions(choice.pop('conditions'))
            native_recipe(choice['recipe'])
    if 'conditions' in recipe:
        recipe['forge:condition'] = all_conditions(recipe.pop('conditions'))
    if 'result' in recipe and 'item' in recipe['result']:
        recipe['result'] = {'id' if key == 'item' else key: value for key, value in recipe['result'].items()}
    return recipe


def native_advancement(advancement):
    if 'advancements' in advancement:
        return {'advancements': [
            {'forge:condition': all_conditions(choice['conditions']), **native_advancement(choice['advancement'])}
            for choice in advancement['advancements']
        ]}
    if 'conditions' in advancement:
        advancement['forge:condition'] = all_conditions(advancement.pop('conditions'))
    for criterion in advancement['criteria'].values():
        for item in criterion.get('conditions', {}).get('items', []):
            if 'tag' in item:
                item['items'] = '#' + item.pop('tag')
    return advancement


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=Path('src/main/resources'))
    parser.add_argument('--core-resources', type=Path, required=True)
    parser.add_argument('--native-jar', type=Path, action='append', required=True)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    fixture = Path(__file__).parent / 'fixtures/mineralogy-1.19.4-crafting.json'
    data = fixture.read_bytes()
    if hashlib.sha256(data).hexdigest().upper() != '62AC8B6CC818D17B22A8631CCF7A504B39387E5C8143BE4B6D342F114E0A03E2':
        raise ValueError('The locked native Mineralogy crafting fixture changed')
    snapshot = json.loads(data)
    native = list(snapshot['recipes'].values())
    tags = copy.deepcopy(snapshot['tags'])
    for path in args.native_jar:
        with ZipFile(path) as archive:
            for name in archive.namelist():
                if name.startswith('data/') and '/recipe/' in name and name.endswith('.json'):
                    native.append(json.loads(archive.read(name)))
                if name.startswith('data/') and '/tags/item/' in name and name.endswith('.json'):
                    identifier = name.split('/')[1] + ':' + name.split('/tags/item/', 1)[1][:-5]
                    data = json.loads(archive.read(name))
                    if data.get('replace', False):
                        tags[identifier] = data['values']
                    else:
                        tags.setdefault(identifier, []).extend(data['values'])
    recipes, advancements = prepare(args.resources, args.core_resources, native, tags)
    fallback_recipes, fallback_advancements = prepare(args.resources, args.core_resources, native, tags, True)
    if recipes.keys() != fallback_recipes.keys():
        raise ValueError('Native and fallback recipe identities differ')
    catalogue = args.resources / 'assets/skysbuildingpiecesbop/catalogue/materials.json'
    materials = {material['id']: material for material in json.loads(catalogue.read_text(encoding='utf-8'))}
    for identity in list(recipes):
        if recipes[identity] == fallback_recipes[identity]:
            continue
        material = materials[':'.join(identity.split('/')[:2])]
        full = material['blocks']['full']['id']
        if not full.startswith('mineralogy:'):
            raise ValueError('Unexpected conditional material: ' + material['id'])
        alternatives = []
        unlocks = []
        for present, recipe, advancement in (
                (True, recipes[identity], advancements['recipes/' + identity]),
                (False, fallback_recipes[identity], fallback_advancements['recipes/' + identity])):
            condition = exists(full) if present else {'type': 'forge:not', 'value': exists(full)}
            conditions = [condition] + recipe.pop('conditions', [])
            advancement.pop('conditions', None)
            alternatives.append({'conditions': conditions, 'recipe': recipe})
            unlocks.append({'conditions': copy.deepcopy(conditions), 'advancement': advancement})
        recipes[identity] = {'type': 'forge:conditional', 'recipes': alternatives}
        advancements['recipes/' + identity] = {'advancements': unlocks}
    # Old recipe IDs belong to the core namespace even though this add-on owns
    # their resources. Advancement identities keep the add-on namespace.
    outputs = {}
    for identity, recipe in recipes.items():
        outputs['data/skysbuildingpieces/recipe/' + identity + '.json'] = recipe
    for identity, advancement in advancements.items():
        outputs['data/skysbuildingpiecesbop/advancement/' + identity + '.json'] = advancement
    outputs['data/skysbuildingpiecesbop/recipe/flower_basket.json'] = {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': [' S ', 'S S', 'SSS'],
        'key': {'S': {'tag': 'forge:rods/wooden'}},
        'result': {'item': 'skysbuildingpiecesbop:flower_basket'}}
    outputs['data/skysbuildingpiecesbop/advancement/recipes/flower_basket.json'] = {
        'criteria': {
            'has_sticks': {'trigger': 'minecraft:inventory_changed',
                           'conditions': {'items': [{'tag': 'forge:rods/wooden'}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked',
                               'conditions': {'recipe': 'skysbuildingpiecesbop:flower_basket'}}},
        'requirements': [['has_sticks', 'has_the_recipe']],
        'rewards': {'recipes': ['skysbuildingpiecesbop:flower_basket']}}
    for name in ('gabbro', 'limestone', 'limestone_smooth', 'shale', 'shale_smooth', 'siltstone', 'siltstone_smooth'):
        outputs['data/skysbuildingpiecesbop/recipe/restore_' + name + '.json'] = {
            'type': 'minecraft:crafting_shapeless', 'category': 'building', 'conditions': [exists('mineralogy:' + name)],
            'ingredients': [{'item': 'skysbuildingpiecesbop:retired_' + name}],
            'result': {'item': 'mineralogy:' + name}}
        identity = 'skysbuildingpiecesbop:restore_' + name
        ingredient = 'skysbuildingpiecesbop:retired_' + name
        outputs['data/skysbuildingpiecesbop/advancement/recipes/restore_' + name + '.json'] = {
            'conditions': [exists('mineralogy:' + name)],
            'criteria': {
                'has_ingredient': {'trigger': 'minecraft:inventory_changed',
                                   'conditions': {'items': [{'items': [ingredient]}]}},
                'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': identity}}},
            'requirements': [['has_ingredient', 'has_the_recipe']], 'rewards': {'recipes': [identity]}}
    from neo_recipe_resources import convert
    outputs = convert({relative: (native_advancement(data) if '/advancement/' in relative else native_recipe(data))
                       for relative, data in outputs.items()})
    manifest = args.resources.parent / 'recipe-book/native-1.14-generated.json'
    previous = json.loads(manifest.read_text(encoding='utf-8')) if manifest.is_file() else []
    changed = 0
    for relative in sorted(set(previous) - outputs.keys()):
        target = args.resources / relative
        if not target.resolve().is_relative_to((args.resources / 'data').resolve()):
            raise ValueError('Unsafe generated resource path: ' + relative)
        if target.is_file():
            changed += 1
            if args.write:
                target.unlink()
    for relative, data in outputs.items():
        target = args.resources / relative
        text = format_json(data) + '\n'
        if not target.is_file() or target.read_text(encoding='utf-8') != text:
            changed += 1
            if args.write:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text, encoding='utf-8', newline='\n')
    if args.write:
        manifest.write_text(format_json(sorted(outputs)) + '\n', encoding='utf-8', newline='\n')
    print(len(recipes), 'cutting recipes,', len(advancements), 'unlocks,', changed, 'resources need writing')
    if changed and not args.write:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
