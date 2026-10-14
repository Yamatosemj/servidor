"""Structural checks for the single-file NeoForge distribution.

This does not replace an actual client startup test (especially on Android).
"""
from __future__ import annotations

import io
import json
import sys
import tomllib
from pathlib import Path
from zipfile import ZipFile

EXPECTED_VERSION = "3.0-beta.1"
EXPECTED_RESOURCES = (
    "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_ground.tbl",
    "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_sitting_shoulder.tbl",
    "assets/aurenminecraft/textures/entity/auren_black_1.png",
    "assets/aurenminecraft/textures/entity/auren_black_5.png",
    "META-INF/licenses/LGPL-3.0.txt",
    "META-INF/licenses/NOTICE.txt",
)

def verify() -> None:
    jars = sorted(Path("build/libs").glob("*.jar"))
    if len(jars) != 1:
        raise AssertionError(f"Expected exactly one distributable JAR; found: {jars}")

    with ZipFile(jars[0]) as auren:
        names = set(auren.namelist())
        assert all(path in names for path in EXPECTED_RESOURCES), (
            f"Missing resources: {set(EXPECTED_RESOURCES) - names}"
        )
        metadata_path = "META-INF/jarjar/metadata.json"
        assert metadata_path in names, "Missing NeoForge Jar-in-Jar metadata"
        metadata = json.loads(auren.read(metadata_path))
        assert len(metadata.get("jars", [])) == 1, "Unexpected nested JAR count"
        embedded = metadata["jars"][0]
        assert embedded["identifier"] == {
            "group": "curse.maven", "artifact": "uranus-1010827"
        }, f"Wrong dependency: {embedded['identifier']}"
        assert embedded["version"]["artifactVersion"] == "8682870"
        nested_path = embedded["path"]
        assert nested_path in names and nested_path.endswith(".jar")
        top_mods = tomllib.loads(auren.read("META-INF/neoforge.mods.toml").decode())
        deps = top_mods["dependencies"]["aurenminecraft"]
        assert any(dep["modId"] == "uranus" and dep["type"] == "required"
                   for dep in deps), "Auren no longer declares Uranus"
        with ZipFile(io.BytesIO(auren.read(nested_path))) as uranus:
            modfile = "META-INF/neoforge.mods.toml"
            assert modfile in uranus.namelist(), "Nested Uranus is not a NeoForge mod"
            inner = tomllib.loads(uranus.read(modfile).decode())
            assert inner["mods"][0]["modId"] == "uranus", "Wrong nested mod ID"
            assert inner["mods"][0]["version"] == EXPECTED_VERSION, (
                f"Wrong Uranus version: {inner['mods'][0]['version']}"
            )
            assert "uranus.mixins.json" in uranus.namelist(), (
                "Uranus Mixin configuration is missing"
            )
    print(f"PASS: {jars[0]} ({jars[0].stat().st_size} bytes): Uranus "
          f"{EXPECTED_VERSION} embedded, original NeoForge mod metadata preserved")

if __name__ == "__main__":
    try:
        verify()
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
