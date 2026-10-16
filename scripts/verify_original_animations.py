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
NAMES = ["ground", "sitting_shoulder", "sitting", "sleeping", "flying", "hovering", "diving"] + [
    f"walk{i}" for i in range(1, 5)
] + [f"flight{i}" for i in range(1, 7)] + [
    "attack_stream_charge1", "attack_stream_charge2",
    "attack_stream_charge3", "attack_stream_breath",
    "bite1", "bite2", "bite3",
]


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
    assert 'loadSeries("attack_stream_charge", 3)' in source
    assert 'load("attack_stream_breath")' in source
    assert 'loadSeries("bite", 3)' in source
    assert "applyBitePose(" in source
    assert "applyBreathPose(" in source
    assert "AurenOriginalAnimations.instance().applyBreathPose(model, dragon, partialTick);" in renderer
    for name in ("flying", "hovering", "diving"):
        assert f'load("{name}")' in source
    assert "AurenOriginalAnimations.instance().apply" in renderer
    assert "dragon.getFlightMode()" in source
    assert "dragon.getFlightBlend(partialTick)" in source
    assert "AurenOriginalFlightMotion.apply(model, age, flightBlend);" in source
    motion = (ROOT / "src/main/java/br/com/auren/minecraft/client/"
              "AurenOriginalFlightMotion.java").read_text(encoding="utf-8")
    for required in ("model.chainWave(tailWithBody", "model.chainWave(neck",
                     "model.chainWave(toesL", "model.chainWave(toesR",
                     "model.walk(model.getCube(\"ThighR\")",
                     "model.bob(model.getCube(\"BodyUpper\")"):
        assert required in motion, f"Original CE flight motion missing: {required}"
    assert "AurenFlightController.FLYING" in source
    assert "blendCycle(model, this.flight, age * 0.2F, flightBlend)" in source
    assert "dragon.getDeltaMovement().y < -0.18D" in source
    assert "AurenPoseTransition.apply(model, awake, shoulderBlend);" in renderer
    assert 'lightningdragon/lightningdragon_sleeping' in renderer
    assert "AurenPoseTransition.applyBones(model, folded, shoulderBlend," in renderer
    assert "AurenNeckPose.apply(model, shoulderBlend);" in renderer
    assert "AurenOriginalShoulderIdle.apply(model, age, shoulderBlend);" in renderer
    # The original CE uses dynamic X body pitch while mounted. This is
    # allowed and must not reintroduce the old static 90-degree shoulder yaw.
    assert "if (entity.isVehicle())" in renderer
    assert "pose.mulPose(Axis.XP.rotationDegrees(entity.getRiderVisualPitch(partialTick)))" in renderer
    assert "Axis.YP.rotationDegrees(90" not in renderer
    assert "mod_version=0.7.7-marked-assault-alpha" in version
    print(f"PASS: {len(NAMES)} compatible original Lightning Dragon "
          f"Tabula models are bundled and referenced; original CE folded wings and illustrated awake neck wrap integrated")


if __name__ == "__main__":
    check()
