"""Resolve fixed BOP palettes against the verified Minecraft 1.14 resources."""

import argparse
import copy
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile
from native_assets import NativeAssets, format_json


NAMESPACE = 'skysbuildingpiecesbop'
SHAPE_STATES = {'slab': 2, 'vertical_slab': 4, 'horizontal_step': 8, 'vertical_step': 4,
                'step': 12, 'corner': 8, 'stairs': 8, 'wall': 1, 'pane': 1}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(format_json(value) + '\n', encoding='utf-8', newline='\n')


def prepare(options):
    resources = options.resources
    assets = resources / 'assets' / NAMESPACE
    catalogue = assets / 'catalogue'
    materials = json.loads((catalogue / 'materials.json').read_text(encoding='utf-8'))
    palettes = json.loads((catalogue / 'palettes.json').read_text(encoding='utf-8'))
    replacements = json.loads((catalogue / 'replacements.json').read_text(encoding='utf-8'))['materials']
    core_materials = json.loads((options.core_source / 'src/main/resources/assets/skysbuildingpieces/catalogue/materials.json')
                               .read_text(encoding='utf-8'))
    definitions = {material['id']: material for material in core_materials}
    definitions.update({material['id']: material for material in materials})
    texture_names = {'minecraft:blocks/slime': 'minecraft:block/slime_block',
                     'minecraft:blocks/glass_lime': 'minecraft:block/lime_stained_glass',
                     'biomesoplenty:items/brown_dye': 'minecraft:item/brown_dye'}
    with ZipFile(options.minecraft_client) as minecraft, ZipFile(options.bop) as bop:
        expected = dict(line.split('=', 1) for line in Path('gradle/core-dependency.properties')
                        .read_text().splitlines() if '=' in line)['bop_sha256']
        if hashlib.sha256(options.bop.read_bytes()).hexdigest().upper() != expected:
            raise ValueError('The BOP input does not match the locked artifact')
        native = NativeAssets({'minecraft': minecraft, 'biomesoplenty': bop})
        # Resolve retained materials first; explicit replacements can then use
        # their complete current definition without altering old palette slots.
        for material in materials:
            material.setdefault('legacy_blocks', copy.deepcopy(material['blocks']))
            material.setdefault('legacy_textures', copy.deepcopy(material['textures']))
            if material['id'] in replacements:
                continue
            original = material['legacy_blocks']['full']
            if original['id'].startswith('mineralogy:'):
                fallback = copy.deepcopy(original['fallback'])
                fallback['properties'] = {'variant': str(fallback.pop('meta', 0))}
                material['blocks'] = {'full': {'id': original['id'], 'fallback': fallback}}
                continue
            name = material['id'].split(':', 1)[1]
            base = name.removeprefix('planks_') if name.startswith('planks_') else {
                'mud_brick_block': 'mud_bricks'}.get(name, name)
            full = base + '_planks' if name.startswith('planks_') else base
            identifier = 'biomesoplenty:' + full
            if not native.exists(identifier):
                raise ValueError('Retained BOP material needs an explicit native mapping: ' + identifier)
            blocks = {'full': {'id': identifier}}
            shape_base = base if name.startswith('planks_') else {'mud_bricks': 'mud_brick'}.get(base, base)
            for shape in ('slab', 'stairs', 'wall', 'fence', 'fence_gate'):
                candidate = 'biomesoplenty:' + shape_base + '_' + shape
                if native.exists(candidate):
                    blocks[shape] = {'id': candidate}
            material['blocks'] = blocks
        for material in materials:
            replacement = replacements.get(material['id'])
            if replacement:
                target = definitions[replacement]
                material['replacement'] = replacement
                material['blocks'] = copy.deepcopy(target['blocks'])
            old = material['legacy_textures']
            old_faces = {'all': old} if isinstance(old, str) else old
            if material['blocks']['full']['id'].startswith('mineralogy:'):
                current = {role: texture.replace(':blocks/', ':block/') for role, texture in old_faces.items()}
            else:
                faces = native.face_textures(material['blocks']['full'])
                current = {role: faces[{'top': 'up', 'bottom': 'down', 'side': 'north', 'all': 'particle'}[role]]
                           for role in old_faces}
            for role, texture in old_faces.items():
                target = current[role]
                if texture in texture_names and texture_names[texture] != target:
                    raise ValueError('Conflicting texture conversion: ' + texture)
                texture_names[texture] = target
            material['textures'] = current['all'] if 'all' in current else current

    # Later ports already use singular texture paths. Apply the same explicit
    # material replacement to those models as to the original catalogue paths.
    texture_names.update({name.replace(':blocks/', ':block/').replace(':items/', ':item/'): target
                          for name, target in list(texture_names.items())})
    changes = {}
    for model in (assets / 'models').rglob('*.json'):
        data = json.loads(model.read_text(encoding='utf-8'))
        for role, texture in data.get('textures', {}).items():
            data['textures'][role] = texture_names.get(texture, texture.replace(':blocks/', ':block/').replace(':items/', ':item/'))
        # Only a face on the block boundary may be hidden by the next block.
        for element in data.get('elements', []):
            for direction, face in element['faces'].items():
                axis, edge, boundary = {'down': (1, 'from', 0), 'up': (1, 'to', 16),
                                       'north': (2, 'from', 0), 'south': (2, 'to', 16),
                                       'west': (0, 'from', 0), 'east': (0, 'to', 16)}[direction]
                if element[edge][axis] != boundary:
                    face.pop('cullface', None)
        changes[model] = data
    palette_by_id = {palette['id']: palette for palette in palettes}
    for state_file in (assets / 'blockstates').glob('*.json'):
        data = json.loads(state_file.read_text(encoding='utf-8'))
        palette = palette_by_id.get(state_file.stem)
        variants = {}
        for selector, model in data['variants'].items():
            if selector == 'inventory':
                continue
            properties = dict(part.split('=', 1) for part in selector.split(',') if part and part != 'normal')
            if palette and int(properties.get('meta', 0)) >= len(palette['materials']) * SHAPE_STATES[palette['shape']]:
                continue
            for alternative in model if isinstance(model, list) else [model]:
                name = alternative['model']
                namespace, path = name.split(':', 1) if ':' in name else ('minecraft', name)
                alternative['model'] = namespace + ':' + (path if path.startswith(('block/', 'item/')) else 'block/' + path)
            for water in ('false', 'true') if palette else (None,):
                native = dict(properties)
                if palette and palette['shape'] in ('wall', 'pane') and len(palette['materials']) == 1:
                    native.pop('meta', None)
                if water is not None:
                    native['waterlogged'] = water
                variants[','.join(key + '=' + native[key] for key in sorted(native))] = copy.deepcopy(model)
        changes[state_file] = {'variants': variants}
    for palette in palettes:
        first = palette['materials'][0].split(':', 1)[1] + '_' + palette['shape']
        changes[assets / 'models/item' / (palette['id'] + '.json')] = {'parent': NAMESPACE + ':item/' + first}
    hidden = {
        'legacy_bop_stone': NAMESPACE + ':item/retired_limestone',
        'legacy_bop_crag_rock': NAMESPACE + ':item/retired_gabbro',
        'legacy_bop_stone_formations': NAMESPACE + ':item/stone_formations',
        'legacy_bop_jelled_poison': NAMESPACE + ':item/jelled_poison',
        'legacy_bop_tree_moss': 'biomesoplenty:item/willow_vine',
        'legacy_turnip_seeds': 'minecraft:item/beetroot_seeds',
        'legacy_pinecone': 'minecraft:item/brown_dye',
        'legacy_dull_flower_band': 'minecraft:item/oxeye_daisy',
        'legacy_bop_flower_basket': NAMESPACE + ':item/flower_basket',
        'retired_rocks': NAMESPACE + ':item/retired_gabbro',
    }
    for name, parent in hidden.items():
        changes[assets / 'models/item' / (name + '.json')] = {'parent': parent}
    for language in (assets / 'lang').glob('*.lang'):
        entries = {}
        for line in language.read_text(encoding='utf-8').splitlines():
            if not line.strip() or line.lstrip().startswith('#'):
                continue
            key, value = line.split('=', 1)
            key = key.removesuffix('.name')
            if key.startswith('tile.' + NAMESPACE + '.'):
                key = 'block.' + key[5:]
            entries[key] = value
        changes[language.with_name(language.stem.lower() + '.json')] = entries
    if options.write:
        write(catalogue / 'materials.json', materials)
        for path, data in changes.items():
            write(path, data)
        for language in (assets / 'lang').glob('*.lang'):
            language.unlink()
        for old, new in (('blocks', 'block'), ('items', 'item')):
            for texture in (assets / 'textures' / old).glob('*.png'):
                destination = assets / 'textures' / new / texture.name
                destination.parent.mkdir(parents=True, exist_ok=True)
                texture.replace(destination)
    print('Validated', len(materials), 'materials and', len(changes), 'model/state/language resources; write=', options.write)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--core-source', type=Path, required=True)
    parser.add_argument('--minecraft-client', type=Path, required=True)
    parser.add_argument('--bop', type=Path, required=True)
    parser.add_argument('--resources', type=Path, default=Path('src/main/resources'))
    parser.add_argument('--write', action='store_true')
    prepare(parser.parse_args())
