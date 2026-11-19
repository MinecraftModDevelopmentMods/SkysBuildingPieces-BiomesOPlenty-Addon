"""Point the native item definitions at the add-on's existing models."""
import argparse
import json
from pathlib import Path

from native_assets import format_json


def model(name):
    return {'type': 'minecraft:model', 'model': 'skysbuildingpiecesbop:item/' + name}


def generate(resources, write):
    assets = resources / 'assets/skysbuildingpiecesbop'
    definitions = assets / 'items'
    expected = set()
    for path in sorted((assets / 'models/item').glob('*.json')):
        name = path.stem
        if name == 'flower_basket_full':
            continue
        definition = model(name)
        if name in ('flower_basket', 'legacy_bop_flower_basket'):
            # A saved Items list containing any compound means the basket is full.
            definition = {'type': 'minecraft:condition', 'property': 'minecraft:component',
                          'predicate': 'minecraft:custom_data', 'value': '{Items:[{}]}',
                          'on_true': model('flower_basket_full'), 'on_false': model(name)}
        destination = definitions / path.name
        expected.add(destination)
        content = format_json({'model': definition}) + '\n'
        if write:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(content, encoding='utf-8', newline='\n')
        elif not destination.is_file() or destination.read_text(encoding='utf-8') != content:
            raise ValueError('Item definition is out of date: ' + destination.name)
    if set(definitions.glob('*.json')) != expected:
        raise ValueError('The item definitions do not match their models')
    print(f'{len(expected)} item definitions checked.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=Path('src/main/resources'))
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    generate(args.resources, args.write)
