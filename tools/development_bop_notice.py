"""Keep the known BOP development notice separate from the strict game audit."""

import gzip
import json
from pathlib import Path
import re
import shutil

from run_packaged_checks import audit, digest


TOOLS = Path(__file__).resolve().parent
CONTROL = json.loads((TOOLS / "bop-development-control.json").read_text(encoding="utf-8"))


def checked_notice(text):
    """Remove at most one exact control-proven pair from a derived log view."""
    lines = text.splitlines(keepends=True)
    kept = []
    count = 0
    index = 0
    while index < len(lines):
        line = lines[index].rstrip()
        if line.endswith(CONTROL["message"]):
            header = (r"\[[^\r\n]+\] \[Server thread/ERROR\] \["
                      r"(?:ne\.ne\.ne\.se|net\.neoforged\.neoforge\.server)"
                      r"\.ServerLifecycleHooks/\]: " + re.escape(CONTROL["message"]))
            if count or not re.fullmatch(header, line):
                raise ValueError("Changed or repeated BOP development notice")
            if index + 1 >= len(lines) or lines[index + 1].strip() != "- minecraft:allay":
                raise ValueError("The BOP development notice names another entity")
            if index + 2 < len(lines) and lines[index + 2].strip().startswith("- "):
                raise ValueError("The BOP development notice includes additional entities")
            count += 1
            index += 2
        else:
            kept.append(lines[index])
            index += 1
    return "".join(kept), count


def check_inputs(directory):
    properties = dict(line.split("=", 1) for line in
                      (TOOLS.parent / "gradle.properties").read_text(encoding="utf-8").splitlines()
                      if line and not line.startswith("#"))
    if properties["neo_version"] != CONTROL["neoforge"]:
        raise ValueError("The BOP development control belongs to another NeoForge build")
    lock_file = next(path for path in (
        TOOLS.parent / "gradle/content-dependencies.properties",
        TOOLS.parent / "gradle/core-dependency.properties") if path.is_file())
    lock = dict(line.split("=", 1) for line in lock_file.read_text(encoding="utf-8").splitlines()
                if line and not line.startswith("#"))
    inputs = json.loads((directory / "artifact-inputs.json").read_text(encoding="utf-8"))
    actual = {path: digest(path) for path in (directory / "mods").glob("*.jar")}
    verified = []
    for name, expected in CONTROL["vendor_hashes"].items():
        if lock.get(name + ".sha256", lock.get(name + "_sha256")) != expected:
            raise ValueError("The BOP development control uses another dependency: " + name)
        matching = [path for path, checksum in actual.items() if checksum == expected]
        if len(matching) != 1:
            raise ValueError("The controlled BOP dependency is missing, changed or repeated: " + name)
        # NeoGradle adds these jars after the probe-only input manifest is written.
        for row in inputs:
            if row["name"] == matching[0].name and row["sha256"] != expected:
                raise ValueError("The BOP dependency conflicts with its input manifest: " + name)
        verified.append({"dependency": name, "name": matching[0].name, "sha256": expected})
    return verified


def audit_development(directory, phase, mode, no_early_display=False):
    vendor_inputs = check_inputs(directory)
    console = directory / (phase + "-console.log")
    text = console.read_text(encoding="utf-8")
    if " in DEV)" not in text or "BUILD SUCCESSFUL" not in text:
        raise ValueError("The development launch did not finish normally")
    view = directory / (phase + "-audit-view")
    view.mkdir()
    shutil.copytree(directory / "config", view / "config")
    if (directory / "crash-reports").exists():
        shutil.copytree(directory / "crash-reports", view / "crash-reports")
    for source in directory.glob("*.json"):
        shutil.copyfile(source, view / source.name)
    records = []
    paths = [console, *[path for path in (directory / "logs").rglob("*")
                       if path.is_file() and path.name.endswith((".log", ".log.gz"))]]
    for source in paths:
        raw = source.read_bytes()
        content = (gzip.decompress(raw) if source.name.endswith(".gz") else raw).decode("utf-8")
        filtered, count = checked_notice(content)
        target = view / source.relative_to(directory)
        target.parent.mkdir(parents=True, exist_ok=True)
        data = filtered.encode("utf-8")
        target.write_bytes(gzip.compress(data, mtime=0) if source.name.endswith(".gz") else data)
        records.append({"path": source.relative_to(directory).as_posix(),
                        "original_sha256": digest(source), "audited_sha256": digest(target),
                        "control_proven_notice_count": count})
    audit(view, phase, mode, no_early_display=no_early_display)
    (directory / (phase + "-development-audit.json")).write_text(json.dumps({
        "passed": True, "raw_logs_preserved": True,
        "control": "tools/bop-development-control.json", "vendor_inputs": vendor_inputs, "logs": records
    }, indent=2) + "\n", encoding="utf-8")
