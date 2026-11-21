"""Recognise only checksum-bound, qualified registry entries from upgrade fixtures."""

import hashlib
import json
from pathlib import Path
import re


def validate_registry_proofs(root, profile):
    """Keep static snapshot notices separate from the world's live biome data."""
    receipts = {}
    for key in ("static_biome_contract", "retired_poi_contract", "live_biome_contract",
                "forge47_unused_registry_contract"):
        binding = profile.get(key)
        if binding is None:
            continue
        path = Path(root) / binding["path"]
        data = json.loads(path.read_text(encoding="utf-8"))
        if hashlib.sha256(path.read_bytes()).hexdigest().upper() != binding["sha256"]:
            raise ValueError("The native registry proof checksum changed")
        receipts[key] = data
        if key != "live_biome_contract" and data["source_world_sha256"] != profile["sha256"]:
            raise ValueError("The native registry proof belongs to another fixture")
    static = receipts.get("static_biome_contract")
    if static is not None:
        biomes = profile["native_biome_contract"]
        if (static["native_biome_contract_sha256"] != biomes["sha256"]
                or static["checked_cells"] != biomes["checked_cells"]
                or static["native_renamed_cells"] != biomes["renamed_cells"]
                or static["snapshot_entries"] != profile["mappings"].get("minecraft:worldgen/biome", {})):
            raise ValueError("The static biome snapshot lacks its complete conversion proof")
    poi = receipts.get("retired_poi_contract")
    if poi is not None and (poi["inspected_nbt_values"] <= 0
            or poi["inspected_nbt_records"] != profile["inspected_nbt_records"]
            or poi["saved_uses"] != {"minecraft:nitwit": 0, "minecraft:unemployed": 0}
            or poi["snapshot_entries"] != {"minecraft:point_of_interest_type":
                                          profile["mappings"]["minecraft:point_of_interest_type"]}):
        raise ValueError("The retired POI entries are not proven unused")
    forge47 = receipts.get("forge47_unused_registry_contract")
    if forge47 is not None:
        entries = forge47["snapshot_entries"]
        names = {name for registry in entries.values() for name in registry}
        if (len(names) != 7 or forge47["inspected_nbt_values"] <= 0
                or "minecraft:worldgen/biome" in entries
                or forge47["saved_uses"] != dict.fromkeys(names, 0)
                or any(profile["mappings"].get(registry, {}).get(name) != numeric_id
                       for registry, values in entries.items() for name, numeric_id in values.items())):
            raise ValueError("The Forge 47 entries are not proven unused")
    dynamic = profile.get("dynamic_biome_saved_uses", 0)
    if dynamic:
        live = receipts.get("live_biome_contract")
        if static is None or live is None:
            raise ValueError("Used dynamic biomes need both registry and saved-volume proofs")
        names = {row["id"] for row in live["entries"]}
        contract_path = Path(root) / profile["native_biome_contract"]["path"]
        contract = json.loads(contract_path.read_text(encoding="utf-8"))
        if (len(names) != len(live["entries"])
                or hashlib.sha256(contract_path.read_bytes()).hexdigest().upper()
                != profile["native_biome_contract"]["sha256"]
                or not set(contract["target_names"].values()) <= names
                or static["live_biome_contract_sha256"] != profile["live_biome_contract"]["sha256"]
                or static["dynamic_biome_saved_uses"] != dynamic
                or dynamic + static["native_renamed_cells"] + static["bop_renamed_cells"]
                + static["unnoticed_biome_cells"]
                != static["checked_cells"]):
            raise ValueError("The saved biome names do not match the live registry proof")


def profile_for(directory):
    manifest = Path(directory) / "forward-fixture.json"
    if not manifest.is_file():
        return None
    source = json.loads(manifest.read_text(encoding="utf-8"))
    root = Path(__file__).resolve().parent.parent
    profiles = json.loads((root / "tools/native-log-profiles.json").read_text(encoding="utf-8"))["profiles"]
    profile = profiles.get(source.get("archive_sha256"))
    if profile is None:
        return None
    fixture = root / profile["fixture"]
    sources = {source.get("source_jar_sha256")}
    sources.update(entry.get("sha256") for entry in source.get("source_artifacts", []))
    if (hashlib.sha256(fixture.read_bytes()).hexdigest().upper() != profile["sha256"]
            or profile["source_jar_sha256"] not in sources
            or profile.get("non_biome_saved_uses", profile["saved_uses"]) != 0
            or profile["inspected_nbt_records"] <= 0):
        raise ValueError("The unused-entry proof does not match the upgrade fixture")
    proof = profile.get("native_biome_contract")
    if profile["saved_uses"] and proof is None:
        raise ValueError("Used biome entries require a complete native conversion contract")
    if proof is not None:
        contract = root / proof["path"]
        data = json.loads(contract.read_text(encoding="utf-8"))
        if (hashlib.sha256(contract.read_bytes()).hexdigest().upper() != proof["sha256"]
                or data["source_world_sha256"] != profile["sha256"]
                or data["source_minecraft"] != proof["source"]
                or data["checked_cells"] != proof["checked_cells"]
                or data["native_renamed_cells"] != proof["renamed_cells"]
                or profile["saved_uses"] != proof["renamed_cells"]
                + profile.get("dynamic_biome_saved_uses", 0)):
            raise ValueError("The native biome contract does not match this source fixture")
    validate_registry_proofs(root, profile)
    return profile


def audit_biome_proof(text, profile):
    proof = profile.get("native_biome_contract") if profile else None
    if proof is None:
        return
    marker = ("BOP_SAVED_BIOME_VOLUME_PASS source=" + proof["source"]
              + " cells=" + str(proof["checked_cells"]) + " renamed=" + str(proof["renamed_cells"]))
    if not any(line.endswith(marker) for line in text.splitlines()):
        raise ValueError("The complete native biome conversion was not verified")


def audit_text(text, profile=None):
    text = text.replace("\r\n", "\n")
    observed = {}
    pattern = r"Unidentified mapping from registry (minecraft:[a-z_/]+)\n(.*?)(?:\n\n)"
    for registry, body in re.findall(pattern, text, re.S):
        entries = {}
        for row in body.splitlines():
            if not row.strip():
                continue
            # Namespace syntax is parsed here; the complete name/number map
            # must still equal this exact fixture's independently checked proof.
            entry = re.fullmatch(r"\s*([a-z0-9_]+:[a-z0-9_./]+): (\d+)", row)
            if not entry or entry[1] in entries:
                raise ValueError("Malformed or repeated registry entry: " + row)
            entries[entry[1]] = int(entry[2])
        if registry in observed:
            raise ValueError("Repeated native registry notice: " + registry)
        observed[registry] = entries
    if observed and (profile is None or observed != profile["mappings"]):
        raise ValueError("Unexpected missing registry entries: " + repr(observed))
    headers = {"Unidentified mapping from registry " + registry for registry in observed}
    if observed:
        headers.add("There are unidentified mappings in this world - we are going to attempt to process anyway")
    seen = set()
    for line in text.splitlines():
        if not re.search(r"/(?:ERROR|FATAL)\]|\[STDERR[/\]]", line):
            continue
        message = re.fullmatch(r"\[[^\r\n]+\] \[main/ERROR\]"
                               r"(?: \[net\.minecraftforge\.registries\.GameData/REGISTRIES\])?: (.+)", line)
        if message and message[1] in headers and message[1] not in seen:
            seen.add(message[1])
        else:
            raise ValueError("Unexpected native failure: " + line)
    if seen != headers:
        raise ValueError("The native registry notice is incomplete")
    for problem in ("Encountered an unexpected exception", "Exception stopping the server",
                    "MissingModsException", "NoSuchMethodError", "NoClassDefFoundError",
                    "ExceptionInInitializerError", "cascading worldgen lag", "Unable to load model",
                    "Unable to load blockstate", "Unable to read property", "outside of the generating region",
                    "java.lang.AssertionError", "java.lang.IllegalStateException:",
                    "java.util.ConcurrentModificationException", "java.nio.channels.ClosedChannelException"):
        if problem in text:
            raise ValueError("Unexpected native failure: " + problem)
    return sorted(headers)
