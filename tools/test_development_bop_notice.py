"""The development allowance must not hide other errors or affect packaged games."""

import unittest
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
from unittest.mock import patch

import development_bop_notice as notices

from development_bop_notice import CONTROL, checked_notice
from native_registry_notices import audit_text


class DevelopmentBopNoticeTest(unittest.TestCase):
    def setUp(self):
        self.notice = ("[time] [Server thread/ERROR] [ne.ne.ne.se.ServerLifecycleHooks/]: "
                       + CONTROL["message"] + "\n\t - minecraft:allay\n")

    def test_exact_pair_is_separate_from_other_log_lines(self):
        clean, count = checked_notice("before\n" + self.notice + "after\n")
        self.assertEqual((clean, count), ("before\nafter\n", 1))
        self.assertEqual(checked_notice("clean\n"), ("clean\n", 0))

    def test_full_logger_and_crlf_are_recognised(self):
        full = self.notice.replace("ne.ne.ne.se", "net.neoforged.neoforge.server")
        self.assertEqual(checked_notice(full.replace("\n", "\r\n")), ("", 1))

    def test_changed_repeated_or_other_entity_notices_fail(self):
        for notice in (self.notice * 2,
                       self.notice.replace("minecraft:allay", "minecraft:zombie"),
                       self.notice.replace("ServerLifecycleHooks", "AnotherLogger"),
                       self.notice.replace("Server thread", "Render thread"),
                       self.notice + "\t - minecraft:zombie\n"):
            with self.subTest(notice=notice), self.assertRaises(ValueError):
                checked_notice(notice)

    def test_other_errors_and_changed_message_still_fail_strict_audit(self):
        for text in (self.notice + "[time] [Server thread/ERROR] [Other/]: failed\n",
                     self.notice.replace("any restrictions.", "different restrictions.")):
            with self.subTest(text=text), self.assertRaises(ValueError):
                audit_text(checked_notice(text)[0])

    def test_packaged_audit_does_not_accept_this_notice(self):
        with self.assertRaises(ValueError):
            audit_text(self.notice)




class DevelopmentBopInputsTest(unittest.TestCase):
    def make_runtime(self, root, content_lock=False):
        tools = root / "tools"
        tools.mkdir()
        (root / "gradle").mkdir()
        (root / "gradle.properties").write_text("neo_version=26.1.2.94\n", encoding="utf-8")
        game = root / "game"
        (game / "mods").mkdir(parents=True)
        vendors = {}
        for index, dependency in enumerate(("bop", "glitchcore", "terrablender")):
            payload = (dependency + " verified control").encode()
            vendors[dependency] = hashlib.sha256(payload).hexdigest().upper()
            (game / "mods" / ("managed-" + str(index) + ".jar")).write_bytes(payload)
        lock = root / "gradle" / ("content-dependencies.properties" if content_lock
                                  else "core-dependency.properties")
        separator = "." if content_lock else "_"
        lock.write_text("\n".join(dependency + separator + "sha256=" + checksum
                                  for dependency, checksum in vendors.items()), encoding="utf-8")
        (game / "mods/probe.jar").write_bytes(b"probe")
        (game / "artifact-inputs.json").write_text(json.dumps([
            {"name": "probe.jar", "sha256": notices.digest(game / "mods/probe.jar")}]), encoding="utf-8")
        control = dict(CONTROL, vendor_hashes=vendors)
        return tools, game, lock, control

    def test_gradle_vendor_jars_are_checked_even_when_the_manifest_only_names_the_probe(self):
        for content_lock in (False, True):
            with self.subTest(content_lock=content_lock), tempfile.TemporaryDirectory() as temporary:
                tools, game, _, control = self.make_runtime(Path(temporary), content_lock)
                with patch.object(notices, "TOOLS", tools), patch.object(notices, "CONTROL", control):
                    checked = notices.check_inputs(game)
                self.assertEqual({row["dependency"] for row in checked}, set(control["vendor_hashes"]))
                self.assertEqual(len(checked), 3)

    def test_changed_repeated_or_conflicting_inputs_are_rejected(self):
        for change in ("changed", "repeated", "manifest", "lock", "loader"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as temporary:
                tools, game, lock, control = self.make_runtime(Path(temporary))
                vendor = game / "mods/managed-0.jar"
                if change == "changed":
                    vendor.write_bytes(b"another build")
                elif change == "repeated":
                    shutil.copy2(vendor, game / "mods/repeated.jar")
                elif change == "manifest":
                    (game / "artifact-inputs.json").write_text(json.dumps([
                        {"name": vendor.name, "sha256": "0" * 64}]), encoding="utf-8")
                elif change == "lock":
                    lock.write_text(lock.read_text().replace(control["vendor_hashes"]["bop"], "0" * 64),
                                    encoding="utf-8")
                else:
                    (tools.parent / "gradle.properties").write_text("neo_version=26.2.0.45-beta\n",
                                                                    encoding="utf-8")
                with patch.object(notices, "TOOLS", tools), patch.object(notices, "CONTROL", control):
                    with self.assertRaises(ValueError):
                        notices.check_inputs(game)


if __name__ == "__main__":
    unittest.main()
