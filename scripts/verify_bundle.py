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
EXPECTED_JUPITER_VERSION = "2.3.7"
EXPECTED_RESOURCES = (
    "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_ground.tbl",
    "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_sitting_shoulder.tbl",
    "assets/aurenminecraft/textures/entity/auren_black_1.png",
    "assets/aurenminecraft/textures/entity/auren_black_5.png",
    "META-INF/licenses/LGPL-3.0.txt",
    "META-INF/licenses/NOTICE.txt",
    "META-INF/licenses/JUPITER_NOTICE.txt",
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
        embedded = metadata.get("jars", [])
        assert len(embedded) == 2, f"Expected Uranus and Jupiter, got {len(embedded)}"
        expected = {
            ("curse.maven", "uranus-1010827"): ("8682870", "uranus", EXPECTED_VERSION),
            ("curse.maven", "jupiter-1072905"): ("7738312", "jupiter", EXPECTED_JUPITER_VERSION),
        }
        top_mods = tomllib.loads(auren.read("META-INF/neoforge.mods.toml").decode())
        deps = top_mods["dependencies"]["aurenminecraft"]
        assert any(dep["modId"] == "uranus" and dep["type"] == "required"
                   for dep in deps), "Auren no longer declares Uranus"
        assert any(dep["modId"] == "iceandfire" and dep["type"] == "required"
                   for dep in deps), "Auren no longer declares IceAndFire CE"
        found = set()
        paths = set()
        for item in embedded:
            key = (item["identifier"]["group"], item["identifier"]["artifact"])
            assert key in expected, f"Unexpected nested dependency: {key}"
            assert key not in found, f"Duplicate nested dependency: {key}"
            found.add(key)
            artifact, mod_id, version = expected[key]
            assert item["version"]["artifactVersion"] == artifact, (
                f"Wrong embedded artifact for {mod_id}"
            )
            path = item["path"]
            assert path not in paths and path in names and path.endswith(".jar"), (
                f"Missing/duplicate nested .jar: {path}"
            )
            paths.add(path)
            with ZipFile(io.BytesIO(auren.read(path))) as inner_jar:
                assert inner_jar.testzip() is None, f"Corrupted embedded {mod_id} JAR"
                modfile = "META-INF/neoforge.mods.toml"
                assert modfile in inner_jar.namelist(), (
                    f"Nested {mod_id} is not an independent NeoForge mod"
                )
                inner = tomllib.loads(inner_jar.read(modfile).decode())
                assert inner["mods"][0]["modId"] == mod_id, (
                    f"Wrong embedded mod ID: {inner['mods'][0]['modId']}"
                )
                assert inner["mods"][0]["version"] == version, (
                    f"Wrong embedded {mod_id} version: {inner['mods'][0]['version']}"
                )
                if mod_id == "uranus":
                    assert "uranus.mixins.json" in inner_jar.namelist(), (
                        "Uranus Mixin configuration is missing"
                    )
        assert found == set(expected), f"Missing bundled libraries: {set(expected)-found}"
    print(f"PASS: {jars[0]} ({jars[0].stat().st_size} bytes): Uranus "
          f"{EXPECTED_VERSION} and Jupiter {EXPECTED_JUPITER_VERSION} nested, "
          f"with original NeoForge mod metadata preserved")

if __name__ == "__main__":
    try:
        verify()
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
