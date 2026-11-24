"""Keep the known vendor notice narrow and all other diagnostics visible."""

import unittest

from bop_development_notice import BOP_ALLAY_MESSAGE, BOP_SPAWN_CONTROL_HASHES, has_controlled_bop_inputs
from native_registry_notices import audit_text


class BopDevelopmentNoticeTest(unittest.TestCase):
    def test_complete_vendor_hashes_are_required(self):
        expected = set(BOP_SPAWN_CONTROL_HASHES)
        self.assertTrue(has_controlled_bop_inputs(expected))
        self.assertTrue(has_controlled_bop_inputs(expected | {"another Sky jar"}))
        for missing in expected:
            with self.subTest(missing=missing):
                self.assertFalse(has_controlled_bop_inputs(expected - {missing}))
                self.assertFalse(has_controlled_bop_inputs((expected - {missing}) | {"different vendor jar"}))

    def test_only_one_exact_startup_notice_is_accepted(self):
        prefix = "[time] [Server thread/ERROR] [ne.ne.ne.se.ServerLifecycleHooks/]: "
        known = prefix + BOP_ALLAY_MESSAGE + "\n\t - minecraft:allay\n"
        startup = '[Server thread/INFO] [MinecraftServer/]: Preparing level "world"\nDone (1.0s)!'
        self.assertEqual(audit_text(known + startup, bop_development=True), [])
        with self.assertRaises(ValueError):
            audit_text(known + startup)
        for text in (
            known + known + startup,
            startup + "\n" + known,
            known.replace("minecraft:allay", "minecraft:bat") + startup,
            known + "\t - minecraft:bat\n" + startup,
            known.replace("ServerLifecycleHooks", "DifferentLogger") + startup,
            known + "[time] [Server thread/ERROR] [Sky/]: Broken generation\n" + startup,
        ):
            with self.subTest(text=text), self.assertRaises(ValueError):
                audit_text(text, bop_development=True)


if __name__ == "__main__":
    unittest.main()
