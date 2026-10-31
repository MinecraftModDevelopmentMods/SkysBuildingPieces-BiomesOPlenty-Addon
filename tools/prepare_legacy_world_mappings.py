"""Describe native BOP states which crossed the 1.13 flattening boundary."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOGUE = ROOT / 'src/main/resources/assets/skysbuildingpiecesbop/catalogue'
source = json.loads((ROOT / 'src/testFixtures/bop-1.12.2-registry-states.json').read_text())
pieces = json.loads((CATALOGUE / 'legacy_states.json').read_text())
covered = {(row['id'], row['meta']) for row in pieces}
plants = json.loads((CATALOGUE / 'legacy_plants.json').read_text())
wood_replacements = {'sacred_oak': 'oak', 'eucalyptus': 'oak', 'pine': 'jungle',
                     'mangrove': 'birch', 'ebony': 'dark_oak', 'giant_flower': 'oak'}
native_woods = {'cherry', 'umbran', 'fir', 'ethereal', 'magic', 'palm', 'redwood',
                'willow', 'hellbark', 'jacaranda', 'mahogany', 'dead'}


def wood(name, shape):
    name = wood_replacements.get(name, name)
    namespace = 'biomesoplenty' if name in native_woods else 'minecraft'
    return f'{namespace}:{name}_{shape}'


def equivalent(row):
    name = row['id'].split(':')[1]
    props = row['properties']
    variant = props.get('variant')
    target_properties = {}
    target = None
    if name.startswith('log_'):
        axis = props['axis']
        target = wood(variant, 'wood' if axis == 'none' else 'log')
        target_properties['axis'] = 'y' if axis == 'none' else axis
    elif name.startswith('leaves_'):
        leaf = {'bamboo': 'jungle', 'flowering': 'flowering_oak',
                'red_big_flower': 'pink_cherry', 'yellow_big_flower': 'yellow_autumn'}.get(variant, variant)
        target = wood(leaf, 'leaves') if leaf in wood_replacements else f'biomesoplenty:{leaf}_leaves'
        target = target.replace('biomesoplenty:jungle_', 'minecraft:jungle_')
        target_properties = {'persistent': str(props['decayable'] == 'false').lower(), 'distance': '7'}
    elif name.endswith('_door_block'):
        target = wood(name[:-11], 'door')
        target_properties = props.copy()
    elif name == 'grass':
        target = 'minecraft:mycelium' if variant == 'mycelial_netherrack' else 'minecraft:grass_block'
        target_properties['snowy'] = props['snowy']
    elif name == 'dirt':
        target = 'minecraft:coarse_dirt' if props['coarse'] == 'true' else 'minecraft:dirt'
    elif name == 'white_sandstone':
        target = 'biomesoplenty:' + {'default': 'white_sandstone', 'chiseled': 'chiseled_white_sandstone',
                                      'smooth': 'cut_white_sandstone'}[variant]
    elif name == 'white_sandstone_stairs':
        target = row['id']
        target_properties = props.copy()
    elif name in {'other_slab', 'double_other_slab'}:
        base = 'white_sandstone' if variant == 'white_sandstone' else 'mud_brick'
        target = 'biomesoplenty:' + (base if name.startswith('double_') and base == 'white_sandstone'
                                      else 'mud_bricks' if name.startswith('double_') else base + '_slab')
        if name == 'other_slab':
            target_properties['type'] = props['half']
    elif name == 'bamboo_thatching':
        target = 'biomesoplenty:willow_planks'
    elif name == 'mud_brick_block':
        target = 'biomesoplenty:mud_bricks'
    elif name == 'grass_path':
        target = 'minecraft:grass_path'
    elif name.startswith('farmland_'):
        target = 'minecraft:farmland'
        target_properties['moisture'] = props['moisture']
    elif name == 'gem_ore':
        target = 'minecraft:' + {'amethyst': 'lapis_ore', 'ruby': 'redstone_ore', 'peridot': 'emerald_ore',
                                 'topaz': 'gold_ore', 'tanzanite': 'lapis_ore', 'malachite': 'emerald_ore',
                                 'sapphire': 'lapis_ore', 'amber': 'gold_ore'}[variant]
    elif name == 'hive':
        target = 'minecraft:yellow_terracotta'
    elif name == 'bamboo':
        target = 'minecraft:bamboo'
        target_properties['age'] = str(min(1, int(props['age'])))
    elif name == 'coral':
        target = 'minecraft:' + {'pink': 'brain_coral', 'orange': 'fire_coral', 'blue': 'tube_coral',
                                 'glowing': 'horn_coral', 'algae': 'seagrass'}[variant]
        if variant != 'algae':
            target_properties['waterlogged'] = 'true'
    elif name == 'seaweed':
        target = 'minecraft:kelp'
    elif name == 'mushroom':
        target = {'toadstool': 'biomesoplenty:toadstool', 'glowshroom': 'biomesoplenty:glowshroom',
                  'blue_milk_cap': 'minecraft:brown_mushroom', 'flat_mushroom': 'minecraft:brown_mushroom',
                  'portobello': 'minecraft:brown_mushroom', 'shadow_shroom': 'minecraft:red_mushroom'}[variant]
    elif name == 'double_plant':
        target = {'flax': 'minecraft:tall_grass', 'tall_cattail': 'biomesoplenty:tall_cattail',
                  'eyebulb': 'minecraft:rose_bush', 'sea_oats': 'minecraft:tall_grass'}[variant]
        target_properties['half'] = props['half']
    elif row['id'] in plants:
        target = plants[row['id']][str(row['meta'])]
        target = target.replace('minecraft:sweet_berries', 'minecraft:sweet_berry_bush')
        if 'stage' in props and target.endswith('_sapling'):
            target_properties['stage'] = props['stage']
    elif name == 'terrarium':
        target = 'minecraft:potted_' + {'bamboo': 'bamboo', 'beach': 'dead_bush', 'cactus': 'cactus',
            'dead': 'dead_bush', 'ender': 'allium', 'fern': 'fern', 'flax': 'fern', 'flower': 'dandelion',
            'glowshroom': 'brown_mushroom', 'koru': 'fern', 'mushroom': 'red_mushroom', 'mystic': 'allium',
            'nether': 'red_mushroom', 'ominous': 'dark_oak_sapling', 'origin': 'oak_sapling',
            'wasteland': 'dead_bush'}[variant]
    elif name in {'ivy', 'willow_vine'}:
        target = 'minecraft:vine' if name == 'ivy' else row['id']
        target_properties = props.copy()
    elif name in {'honey', 'blood', 'poison', 'hot_spring_water'}:
        target = 'minecraft:water'
        target_properties['level'] = props['level']
    elif name == 'sand':
        target = 'minecraft:sand'
    else:
        target = {'hard_ice': 'minecraft:packed_ice', 'crystal': 'minecraft:purpur_block',
                  'honey_block': 'minecraft:slime_block', 'blue_fire': 'minecraft:fire',
                  'bramble_plant': 'biomesoplenty:bramble', 'ash_block': 'biomesoplenty:ash_block',
                  'dried_sand': 'biomesoplenty:dried_sand', 'flesh': 'biomesoplenty:flesh',
                  'white_sand': 'biomesoplenty:white_sand', 'mud': 'biomesoplenty:mud'}.get(name)
    if target is None:
        if name == 'biome_block':
            return {'unsupported': 'BOP internal biome marker has no playable equivalent'}
        raise ValueError('An explicit replacement is still needed: ' + str(row))
    return {'id': target, 'properties': target_properties}


result = []
for row in source:
    if (row['id'], row['meta']) not in covered:
        result.append(dict(row, target=equivalent(row)))
(CATALOGUE / 'legacy_world_states.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print('BOP_WORLD_MAPPING_TABLE', len(result), 'plus', len(pieces), 'catalogue states')
