"""Read target-native models and keep generated JSON readable."""

import json

def read_json(archive, name):
    return json.loads(archive.read(name))

def format_json(value, depth=0):
    """Keep coordinates and individual faces readable without expanding every number."""
    if isinstance(value, list):
        if not any(isinstance(item, (dict, list)) for item in value):
            return json.dumps(value, ensure_ascii=False)
        indent = '  ' * (depth + 1)
        return '[\n' + ',\n'.join(indent + format_json(item, depth + 1) for item in value) \
            + '\n' + '  ' * depth + ']'
    if isinstance(value, dict):
        if not value or 'texture' in value and all(key in (
                'uv', 'texture', 'cullface', 'tintindex', 'rotation') for key in value):
            return json.dumps(value, ensure_ascii=False)
        indent = '  ' * (depth + 1)
        return '{\n' + ',\n'.join(indent + json.dumps(key) + ': ' + format_json(item, depth + 1)
                                 for key, item in value.items()) + '\n' + '  ' * depth + '}'
    return json.dumps(value, ensure_ascii=False)

class NativeAssets:
    def __init__(self, archives):
        self.archives = archives
        self.models = {}

    def archive_path(self, identifier, directory):
        namespace, path = identifier.split(':', 1)
        return self.archives[namespace], f'assets/{namespace}/{directory}/{path}.json'

    def exists(self, identifier):
        archive, path = self.archive_path(identifier, 'blockstates')
        return path in archive.namelist()

    def model(self, identifier):
        if ':' not in identifier:
            identifier = 'minecraft:' + identifier
        if identifier in self.models:
            return self.models[identifier]
        archive, path = self.archive_path(identifier, 'models')
        data = read_json(archive, path)
        parent = self.model(data['parent']) if 'parent' in data else {}
        combined = {**parent, **data}
        combined['textures'] = {**parent.get('textures', {}), **data.get('textures', {})}
        self.models[identifier] = combined
        return combined

    def face_textures(self, state):
        archive, path = self.archive_path(state['id'], 'blockstates')
        variants = read_json(archive, path)['variants']
        properties = state.get('properties', {})
        matches = []
        for conditions, model in variants.items():
            choices = dict(part.split('=', 1) for part in conditions.split(',') if part)
            if all(properties.get(key, value) == value for key, value in choices.items()):
                matches.append(model)
        if not matches:
            raise ValueError('No native model for ' + repr(state))
        model = matches[0][0] if isinstance(matches[0], list) else matches[0]
        data = self.model(model['model'])

        def resolve(texture):
            seen = set()
            while texture.startswith('#'):
                if texture in seen:
                    raise ValueError('Circular texture reference: ' + texture)
                seen.add(texture)
                texture = data['textures'][texture[1:]]
            return texture if ':' in texture else 'minecraft:' + texture

        faces = {}
        for element in data.get('elements', []):
            for direction, face in element['faces'].items():
                faces.setdefault(direction, resolve(face['texture']))
        faces['particle'] = resolve(data['textures'].get('particle', '#all'))
        return faces
