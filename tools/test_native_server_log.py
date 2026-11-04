"""Negative controls for Forge 45 server completion and native settings."""

from pathlib import Path
import shutil
import tempfile
import unittest

from native_client_log import audit_server, classify_server


NOTICE = ('[Thread-1/FATAL] [net.minecraftforge.common.ForgeConfig/CORE]: '
          'Forge config just got changed on the file system!')
DEBUG = ('[Thread-1/DEBUG] [net.minecraftforge.fml.config.ConfigFileTypeHandler/CONFIG]: '
         'Config file forge-server.toml changed, sending notifies')
START = '[Server thread/INFO] [MinecraftServer/]: Preparing level "world"'
LOG = '\n'.join((START, 'Done (1.0s)!'))
DETAILS = '\n'.join((DEBUG, START, 'Done (1.0s)!'))


class NativeServerLogTest(unittest.TestCase):
    def test_clean_native_startup_is_accepted(self):
        receipt = classify_server(LOG, DETAILS, True)
        self.assertEqual(receipt['native_server_config_notices'], 0)
        self.assertEqual(receipt['notices'], [])

    def test_config_change_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(LOG, DETAILS, False)

    def test_late_notice_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server('\n'.join((START, NOTICE)), DETAILS, True)

    def test_repeated_notices_are_rejected(self):
        with self.assertRaises(ValueError):
            classify_server('\n'.join((NOTICE, NOTICE, START)), DETAILS, True)

    def test_previous_target_notices_are_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(NOTICE + '\n' + LOG, DETAILS, True)

    def test_missing_debug_evidence_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(LOG, '', True)

    def test_unrelated_error_and_stderr_are_rejected(self):
        for extra in ('[Server thread/ERROR] [AnotherMod/]: Failed',
                      '[STDERR/]: java.lang.AssertionError'):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                classify_server(LOG + '\n' + extra, DETAILS, True)

    def test_invalid_saved_property_warning_is_rejected(self):
        warning = '[Server thread/WARN] [minecraft/NBTUtil]: Unable to read property: north with value: true'
        with self.assertRaises(ValueError):
            classify_server(LOG + '\n' + warning, DETAILS, True)

    def test_wrong_logger_is_rejected(self):
        with self.assertRaises(ValueError):
            classify_server(NOTICE.replace('ForgeConfig/CORE', 'OtherConfig/CORE') + '\n' + LOG, DETAILS, True)


class NativeRegistryNoticeTest(unittest.TestCase):
    def setUp(self):
        import json
        from native_registry_notices import audit_text
        root = Path(__file__).resolve().parent.parent
        self.profile = json.loads((root / "tools/native-log-profiles.json").read_text(encoding="utf-8"))["profiles"][
            "634FA10DA5BA9FD5D2C95EF01142F91F4B85EC2A1EEB2F2292A70663CAC8C1A7"]
        self.check = audit_text
        prefix = "[time] [main/ERROR] [net.minecraftforge.registries.GameData/REGISTRIES]: "
        self.valid = "".join(prefix + "Unidentified mapping from registry " + registry + "\n" +
                             "\n".join("\t" + name + ": " + str(number) for name, number in entries.items()) + "\n\n"
                             for registry, entries in self.profile["mappings"].items())
        self.valid += prefix + "There are unidentified mappings in this world - we are going to attempt to process anyway\n"

    def test_exact_native_notices_and_a_clean_reload_pass(self):
        self.assertEqual(len(self.check(self.valid, self.profile)), 6)
        short = self.valid.replace(" [net.minecraftforge.registries.GameData/REGISTRIES]", "")
        self.assertEqual(len(self.check(short, self.profile)), 6)
        self.assertEqual(self.check("A clean native reload", None), [])

    def test_used_biomes_require_the_complete_volume_check(self):
        from native_registry_notices import audit_biome_proof
        proof = self.profile['native_biome_contract']
        marker = ('BOP_SAVED_BIOME_VOLUME_PASS source=' + proof['source']
                  + ' cells=' + str(proof['checked_cells'])
                  + ' renamed=' + str(proof['renamed_cells']))
        audit_biome_proof(self.valid + '\n[server/INFO]: ' + marker, self.profile)
        for incomplete in (self.valid, marker.replace('cells=' + str(proof['checked_cells']), 'cells=1'),
                           marker.replace('source=' + proof['source'], 'source=unknown')):
            with self.subTest(log=incomplete[-100:]), self.assertRaises(ValueError):
                audit_biome_proof(incomplete, self.profile)

    def test_every_notice_boundary_is_enforced(self):
        candidates = [
            (self.valid, None),
            (self.valid.replace("minecraft:zombie_pigman: 56", "minecraft:zombie_pigman: 57"), self.profile),
            (self.valid.replace("minecraft:zombie_pigman: 56", "minecraft:unknown: 56"), self.profile),
            (self.valid.replace("minecraft:zombie_pigman: 56", "minecraft:zombie_pigman: 56\n\tminecraft:unknown: 58"), self.profile),
            (self.valid.replace("[main/ERROR]", "[Server thread/ERROR]"), self.profile),
            (self.valid.replace("GameData/REGISTRIES", "Other/REGISTRIES"), self.profile),
            (self.valid.replace("/ERROR]", "/FATAL]"), self.profile),
            (self.valid.replace("minecraft:zombie_pigman: 56", "malformed entry"), self.profile),
            (self.valid + self.valid, self.profile),
            (self.valid + "[time] [main/ERROR] [Other/]: Failed", self.profile),
            (self.valid + "[STDERR/]: hidden stack trace", self.profile),
            (self.valid + "Unable to load model", self.profile),
            (self.valid + "cascading worldgen lag", self.profile),
            (self.valid.replace("There are unidentified mappings in this world - we are going to attempt to process anyway", "MissingModsException"), self.profile),
        ]
        for index, (text, profile) in enumerate(candidates):
            with self.subTest(boundary=index), self.assertRaises(ValueError):
                self.check(text, profile)


class NativeServerFilesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.config = self.directory / 'world/serverconfig/forge-server.toml'
        self.config.parent.mkdir(parents=True)
        defaults = Path(__file__).resolve().parent.parent / 'src/bootstrapTest/defaultconfigs'
        shutil.copyfile(defaults / 'defaultconfigs/forge-server.toml', self.config)
        logs = self.directory / 'logs'
        logs.mkdir()
        (logs / 'latest.log').write_text(LOG, encoding='utf-8')
        (logs / 'debug.log').write_text(DETAILS, encoding='utf-8')

    def test_unchanged_defaults_pass(self):
        self.assertEqual(audit_server(self.directory, 'test')['native_server_config_notices'], 0)

    def test_changed_defaults_fail(self):
        original = self.config.read_text()
        self.assertIn('removeErroringEntities = false', original)
        self.config.write_text(original.replace('removeErroringEntities = false',
                                                'removeErroringEntities = true'))
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')

    def test_missing_defaults_fail(self):
        self.config.unlink()
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')

    def test_crash_report_fails(self):
        crashes = self.directory / 'crash-reports'
        crashes.mkdir()
        (crashes / 'failure.txt').write_text('Simulated failure')
        with self.assertRaises(ValueError):
            audit_server(self.directory, 'test')


class DynamicBiomeProofTest(unittest.TestCase):
    def setUp(self):
        import json
        self.root = Path(__file__).resolve().parent.parent
        profiles = json.loads((self.root / 'tools/native-log-profiles.json').read_text())['profiles']
        self.profile = profiles['0A52E35356AE6229E1331A5E4BAAF99CBA22489EC4EC28A87F5BCE024BE918B5']

    def test_complete_dynamic_registry_proof_passes(self):
        from native_registry_notices import validate_registry_proofs
        validate_registry_proofs(self.root, self.profile)

    def test_missing_binding_or_changed_cell_count_fails(self):
        import copy
        from native_registry_notices import validate_registry_proofs
        for key in ('static_biome_contract', 'live_biome_contract'):
            profile = copy.deepcopy(self.profile)
            del profile[key]
            with self.subTest(binding=key), self.assertRaises(ValueError):
                validate_registry_proofs(self.root, profile)
        profile = copy.deepcopy(self.profile)
        profile['dynamic_biome_saved_uses'] += 1
        with self.assertRaises(ValueError):
            validate_registry_proofs(self.root, profile)

    def test_resealed_but_inconsistent_proofs_fail(self):
        import copy
        import hashlib
        import json
        from native_registry_notices import validate_registry_proofs
        mutations = (
            ('static_biome_contract', lambda proof: proof.update(source_world_sha256='wrong-world')),
            ('static_biome_contract', lambda proof: proof.update(checked_cells=1)),
            ('static_biome_contract', lambda proof: proof.update(
                unnoticed_biome_cells=proof['unnoticed_biome_cells'] + 1)),
            ('retired_poi_contract', lambda proof: proof['saved_uses'].update({'minecraft:nitwit': 1})),
            ('retired_poi_contract', lambda proof: proof.update(inspected_nbt_records=1)),
            ('live_biome_contract', lambda proof: proof.update(entries=[])),
        )
        for key, mutate in mutations:
            with self.subTest(proof=key), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                profile = copy.deepcopy(self.profile)
                for binding in ('static_biome_contract', 'retired_poi_contract', 'live_biome_contract'):
                    path = directory / profile[binding]['path']
                    path.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(self.root / profile[binding]['path'], path)
                contract = directory / profile['native_biome_contract']['path']
                contract.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(self.root / profile['native_biome_contract']['path'], contract)
                path = directory / profile[key]['path']
                proof = json.loads(path.read_text())
                mutate(proof)
                path.write_text(json.dumps(proof))
                profile[key]['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest().upper()
                with self.assertRaises(ValueError):
                    validate_registry_proofs(directory, profile)


if __name__ == '__main__':
    unittest.main()
