"""Resolve model parents and textures against the actual installed target resources."""
import argparse
import json
from pathlib import Path
from zipfile import ZipFile


def validate(resources, dependencies):
    files = {}
    for dependency in dependencies:
        if dependency.is_dir():
            files.update({path.relative_to(dependency).as_posix(): path.read_bytes()
                          for path in dependency.rglob('*') if path.is_file()})
        else:
            with ZipFile(dependency) as archive:
                files.update({name: archive.read(name) for name in archive.namelist()
                              if name.startswith('assets/') and not name.endswith('/')})
    files.update({path.relative_to(resources).as_posix(): path.read_bytes()
                  for path in resources.rglob('*') if path.is_file()})
    errors = set()
    checked = set()

    def location(identifier, directory, extension):
        namespace, value = identifier.split(':', 1) if ':' in identifier else ('minecraft', identifier)
        return f'assets/{namespace}/{directory}/{value}{extension}'

    def model(identifier, parents=()):
        path = location(identifier, 'models', '.json')
        if path in parents:
            raise ValueError('Circular model parent: ' + path)
        if path not in files:
            errors.add('Missing model: ' + path)
            return {}, []
        data = json.loads(files[path])
        textures, elements = {}, []
        parent = data.get('parent', '')
        if parent and not parent.startswith(('builtin/', 'minecraft:builtin/')):
            textures, elements = model(parent, parents + (path,))
        textures = dict(textures, **data.get('textures', {}))
        elements = data.get('elements', elements)
        return textures, elements

    def texture(value, variables, source, visited=()):
        if value.startswith('#'):
            key = value[1:]
            if key in visited or key not in variables:
                errors.add(f'Unresolved texture {value} in {source}')
                return
            texture(variables[key], variables, source, visited + (key,))
        elif location(value, 'textures', '.png') not in files:
            errors.add(f'Missing texture {value} in {source}')

    own = resources / 'assets/skysbuildingpiecesbop'
    for path in sorted((own / 'models').rglob('*.json')):
        identifier = 'skysbuildingpiecesbop:' + path.relative_to(own / 'models').with_suffix('').as_posix()
        variables, elements = model(identifier)
        for value in variables.values():
            texture(value, variables, identifier)
        for element in elements:
            for face in element.get('faces', {}).values():
                texture(face['texture'], variables, identifier)
        for override in json.loads(path.read_text(encoding='utf-8')).get('overrides', []):
            model(override['model'])
        checked.add(identifier)
    for path in sorted((own / 'blockstates').glob('*.json')):
        state = json.loads(path.read_text(encoding='utf-8'))
        entries = list(state.get('variants', {}).values())
        entries += [entry['apply'] for entry in state.get('multipart', [])]
        for alternatives in entries:
            for entry in alternatives if isinstance(alternatives, list) else [alternatives]:
                model(entry['model'])
    if errors:
        raise ValueError('\n'.join(sorted(errors)))
    return len(checked)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=Path('src/main/resources'))
    parser.add_argument('--dependency', type=Path, action='append', required=True)
    args = parser.parse_args()
    print('Native models and textures verified:', validate(args.resources, args.dependency))
