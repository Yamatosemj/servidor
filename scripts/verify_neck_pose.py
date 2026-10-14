"""Check that v0.3's neck-wrap pose targets real bones in the bundled Tabula model.

Structural build test only: pose appearance must also be checked in Minecraft.
"""
from __future__ import annotations

import io
import json
import re
import sys
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
POSE = ROOT / "src/main/java/br/com/auren/minecraft/client/AurenNeckPose.java"
RENDERER = ROOT / "src/main/java/br/com/auren/minecraft/client/AurenDragonRenderer.java"
ENTITY = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenDragon.java"
GRADLE = ROOT / "gradle.properties"
TABULA = "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_sitting_shoulder.tbl"


def iter_names(cubes):
    for cube in cubes:
        yield cube["name"]
        yield from iter_names(cube.get("children", []))


def verify():
    jars = sorted((ROOT / "build/libs").glob("*.jar"))
    assert len(jars) == 1, f"Expected one built jar: {jars}"
    with ZipFile(jars[0]) as outer:
        assert TABULA in outer.namelist(), "Missing shoulder Tabula model"
        with ZipFile(io.BytesIO(outer.read(TABULA))) as inner:
            model = json.loads(inner.read("model.json"))
    skeleton = set(iter_names(model["cubes"]))
    pose_source = POSE.read_text(encoding="utf-8")
    names = set(re.findall(r'rotate[XYZ]\\(model, "([^"]+)"', pose_source))
    names.update(re.findall(r'model\\.getCube\\("([^"]+)"', pose_source))
    assert names, "Pose refers to no bones"
    missing = names - skeleton
    assert not missing, f"Pose references missing Tabula bones: {sorted(missing)}"

    renderer = RENDERER.read_text(encoding="utf-8")
    entity = ENTITY.read_text(encoding="utf-8")
    version = GRADLE.read_text(encoding="utf-8")
    assert "AurenNeckPose.apply(model, age);" in renderer
    assert "dragon.isSmallForm() && dragon.isOnShoulder()" in renderer
    assert "entity.isSmallForm() && entity.isOnShoulder()" in renderer
    assert "Axis.YP.rotationDegrees(90.0F)" in renderer
    assert "float factor = entity.isSmallForm() ? 0.42F : 5.8F;" in renderer
    assert "double backward = 0.18D;" in entity
    assert "mod_version=0.3.0-alpha" in version
    print(f"PASS: v0.3 shoulder model includes {len(names)} valid animated bones; "
          "neck pose isolated and baby scale preserved")


if __name__ == "__main__":
    try:
        verify()
    except Exception as e:
        print(f"FAIL neck pose verification: {e}", file=sys.stderr)
        sys.exit(1)
