"""Recognise only checksum-bound, qualified registry entries from upgrade fixtures."""

import hashlib
import json
from pathlib import Path
import re
import zipfile


def expected_mappings(profile):
    if "forge61_mappings" in profile:
        return profile["forge61_mappings"]
    if "forge52_mappings" in profile:
        return profile["forge52_mappings"]
    expected = {kind: entries.copy() for kind, entries in profile["mappings"].items()}
    for kind, entries in profile.get("forge50_additional_mappings", {}).items():
        expected.setdefault(kind, {}).update(entries)
    return expected


def validate_registry_proofs(root, profile):
    """Keep static snapshot notices separate from the world's live biome data."""
    block_proof = profile.get("native_block_rename_contract")
    if block_proof is not None:
        path = Path(root) / block_proof["path"]
        data = json.loads(path.read_text(encoding="utf-8"))
        positions = data["positions"]
        if (hashlib.sha256(path.read_bytes()).hexdigest().upper() != block_proof["sha256"]
                or data["source_world_sha256"] != profile["sha256"]
                or data["source"] != block_proof["source"]
                or data["target"] != block_proof["target"]
                or data["source"] not in expected_mappings(profile)["minecraft:block"]
                or data["checked_positions"] != block_proof["checked_positions"]
                or len({tuple(position) for position in positions}) != data["checked_positions"]):
            raise ValueError("The saved block rename does not match the fixture")
    receipts = {}
    for key in ("static_biome_contract", "retired_poi_contract", "live_biome_contract",
                "forge47_unused_registry_contract", "forge50_unused_registry_contract"):
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
    if "forge50_unused_registry_contract" in receipts:
        proof = receipts["forge50_unused_registry_contract"]
        entries = proof["snapshot_entries"]
        names = {name for registry in entries.values() for name in registry}
        if (not names or proof["inspected_nbt_values"] <= 0
                or "minecraft:worldgen/biome" in entries
                or proof["saved_uses"] != dict.fromkeys(names, 0)
                or any(expected_mappings(profile).get(registry, {}).get(name) != numeric_id
                       for registry, values in entries.items() for name, numeric_id in values.items())):
            raise ValueError("The additional Forge registry entries are not proven unused")
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


EMPTY_CAPABILITIES = "Failed to parse saved data for 'SavedDataType[capabilities]': No key data in MapLike[{}]"


def bound_empty_capabilities(root, directory, fixture_id):
    path = root / "tools/native-log-forge61-empty-capabilities.json"
    profiles = json.loads(path.read_text(encoding="utf-8"))
    proof = profiles.get(fixture_id)
    if not proof:
        return 0
    archive = root / proof["fixture"]
    if hashlib.sha256(archive.read_bytes()).hexdigest().upper() != fixture_id:
        raise ValueError("The empty capability control fixture changed")
    with zipfile.ZipFile(archive) as source:
        for name, expected in proof["original"].items():
            if hashlib.sha256(source.read(name)).hexdigest().upper() != expected:
                raise ValueError("The empty capability source file changed: " + name)
    for name, expected in proof["rewritten"].items():
        target = Path(directory) / "world" / name
        if hashlib.sha256(target.read_bytes()).hexdigest().upper() != expected:
            raise ValueError("Forge did not rewrite the empty capability record: " + name)
    return len(proof["original"])


def forge61_registry_mappings(root, fixture_id):
    profiles = json.loads((root / "tools/native-log-forge61-profiles.json").read_text(encoding="utf-8"))
    proof = profiles.get(fixture_id)
    if proof is None:
        return {}
    fixture = root / proof["fixture"]
    if (hashlib.sha256(fixture.read_bytes()).hexdigest().upper() != fixture_id
            or proof["inspected_nbt_records"] <= 0 or not proof["mappings"]):
        raise ValueError("The Forge 61 registry proof does not match this fixture")
    return {"forge61_mappings": proof["mappings"]}


def profile_for(directory):
    manifest = Path(directory) / "forward-fixture.json"
    if not manifest.is_file():
        return None
    source = json.loads(manifest.read_text(encoding="utf-8"))
    root = Path(__file__).resolve().parent.parent
    profiles = json.loads((root / "tools/native-log-profiles.json").read_text(encoding="utf-8"))["profiles"]
    current_path = root / "tools/native-log-forge52-profiles.json"
    current_profiles = json.loads(current_path.read_text(encoding="utf-8"))["profiles"] if current_path.is_file() else {}
    fixture_id = source.get("archive_sha256")
    empty_capabilities = bound_empty_capabilities(root, directory, fixture_id)
    forge61 = forge61_registry_mappings(root, fixture_id)
    current = current_profiles.get(fixture_id)
    profile = profiles.get(fixture_id, current)
    if profile is None:
        if not empty_capabilities and not forge61:
            return None
        return {"mappings": {}, "empty_capabilities_count": empty_capabilities, **forge61}
    fixture = root / profile["fixture"]
    sources = {source.get("source_jar_sha256")}
    artifacts = source.get("source_artifacts")
    if isinstance(artifacts, list):
        sources.update(entry.get("sha256") for entry in artifacts)
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
    if current is not None:
        if (current["sha256"] != fixture_id or current["fixture"] != profile["fixture"]
                or current["source_jar_sha256"] not in sources or not current["mappings"]
                or current["inspected_nbt_records"] <= 0):
            raise ValueError("The Forge 52 registry proof does not match this fixture")
        # Keep the historical proofs for data conversion. Only the exact set
        # of registry notices changes with this Forge version.
        profile = {**profile, "forge52_mappings": current["mappings"]}
    return {**profile, "empty_capabilities_count": empty_capabilities, **forge61}


def audit_biome_proof(text, profile):
    block_proof = profile.get("native_block_rename_contract") if profile else None
    if block_proof is not None:
        marker = ("BUILDING_PIECES_NATIVE_BLOCK_RENAME_PASS source=" + block_proof["source"]
                  + " target=" + block_proof["target"] + " positions="
                  + str(block_proof["checked_positions"]))
        if not any(line.endswith(marker) for line in text.splitlines()):
            raise ValueError("The complete native block rename was not verified")
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
    if observed and (profile is None or observed != expected_mappings(profile)):
        raise ValueError("Unexpected missing registry entries: " + repr(observed))
    headers = {"Unidentified mapping from registry " + registry for registry in observed}
    if observed:
        headers.add("There are unidentified mappings in this world - we are going to attempt to process anyway")
    seen = set()
    capabilities = 0
    for line in text.splitlines():
        if not re.search(r"/(?:ERROR|FATAL)\]|\[STDERR[/\]]", line):
            continue
        message = re.fullmatch(r"\[[^\r\n]+\] \[main/ERROR\]"
                               r"(?: \[net\.minecraftforge\.registries\.GameData/REGISTRIES\])?: (.+)", line)
        if message and message[1] in headers and message[1] not in seen:
            seen.add(message[1])
        elif (profile and profile.get("empty_capabilities_count", 0)
                and re.fullmatch(r"\[[^\r\n]+\] \[Server thread/ERROR\]"
                                 r"(?: \[net\.minecraft\.world\.level\.storage\.DimensionDataStorage/\])?: "
                                 + re.escape(EMPTY_CAPABILITIES), line)):
            capabilities += 1
        else:
            raise ValueError("Unexpected native failure: " + line)
    if capabilities and capabilities != profile["empty_capabilities_count"]:
        raise ValueError("Unexpected number of empty Forge capability records")
    if seen != headers:
        raise ValueError("The native registry notice is incomplete")
    # A class-loading trace can name AssertionError without throwing one.
    if re.search(r"(?m)^\s*(?:Caused by: )?java\.lang\.AssertionError(?::|$)", text):
        raise ValueError("Unexpected native failure: java.lang.AssertionError")
    for problem in ("Encountered an unexpected exception", "Exception stopping the server",
                    "MissingModsException", "NoSuchMethodError", "NoClassDefFoundError",
                    "ExceptionInInitializerError", "cascading worldgen lag", "Unable to load model",
                    "Unable to load blockstate", "Unable to read property", "outside of the generating region",
                    "java.lang.IllegalStateException:"):
        if problem in text:
            raise ValueError("Unexpected native failure: " + problem)
    return sorted(headers) + [EMPTY_CAPABILITIES] * capabilities
