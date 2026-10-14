"""Verify that all imported Lightning Dragon animation assets are actually bundled.

These are original upstream Tabula keyframes, not made-up Euler animations.
The verifier checks their skeletons against the ground pose and that the code
loads those specific models. Build success does not prove visual quality.
"""
from __future__ import annotations

import io
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
BASE = "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_"
NAMES = ["ground", "sitting_shoulder", "flying", "hovering", "diving"] + [
    f"walk{i}" for i in range(1, 5)
] + [f"flight{i}" for i in range(1, 7)]


def names(cubes):
    found = set()
    for cube in cubes:
        found.add(cube["name"])
        found.update(names(cube.get("children", [])))
    return found


def check():
    jars = list((ROOT / "build/libs").glob("*.jar"))
    assert len(jars) == 1, f"Expected one Auren mod JAR: {jars}"
    skeleton = None
    with ZipFile(jars[0]) as mod:
        for name in NAMES:
            path = BASE + name + ".tbl"
            assert path in mod.namelist(), f"Missing source Tabula keyframe {path}"
            with ZipFile(io.BytesIO(mod.read(path))) as tbl:
                model = json.loads(tbl.read("model.json"))
            this = names(model["cubes"])
            if skeleton is None:
                skeleton = this
            else:
                assert this == skeleton, (
                    f"Incompatible CE skeleton {name}: "
                    f"missing {sorted(skeleton - this)}; extra {sorted(this - skeleton)}"
                )

    source = (ROOT / "src/main/java/br/com/auren/minecraft/client/"
              "AurenOriginalAnimations.java").read_text(encoding="utf-8")
    renderer = (ROOT / "src/main/java/br/com/auren/minecraft/client/"
                "AurenDragonRenderer.java").read_text(encoding="utf-8")
    version = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    assert 'loadSeries("walk", 4)' in source
    assert 'loadSeries("flight", 6)' in source
    for name in ("flying", "hovering", "diving"):
        assert f'load("{name}")' in source
    assert "AurenOriginalAnimations.instance().apply" in renderer
    assert "AurenNeckPose.apply" in renderer
    assert "mod_version=0.5.3-alpha" in version
    print(f"PASS: {len(NAMES)} compatible original Lightning Dragon "
          f"Tabula models are bundled and referenced; neck pose retained")


if __name__ == "__main__":
    check()
