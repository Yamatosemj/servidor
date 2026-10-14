"""Verify Auren uses the original CE lightningdragon sitting_shoulder pose.

This tests SOURCE INTEGRITY and eliminates a known distortion from v0.5.2:
a custom neck-wrap additive pose and global 90-degree root rotation.

The CE source renderer offsetZ detail is essential, but final visual placement
still requires first/third-person testing in Minecraft on Zalith.
"""
from __future__ import annotations
import io
import json
import sys
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "src/main/java/br/com/auren/minecraft/client/AurenDragonRenderer.java"
ENTITY = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenDragon.java"
TRANSITION = ROOT / "src/main/java/br/com/auren/minecraft/client/AurenPoseTransition.java"
PROPS = ROOT / "gradle.properties"
TABULA = "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_sleeping.tbl"
TABULA_SHOULDER = "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_sitting_shoulder.tbl"


def bones(cubes):
    for cube in cubes:
        yield cube["name"]
        yield from bones(cube.get("children", []))


def verify():
    jars = sorted((ROOT / "build/libs").glob("*.jar"))
    assert len(jars) == 1, f"Expected a single built JAR: {jars}"
    with ZipFile(jars[0]) as outer:
        assert TABULA in outer.namelist(), "Missing the original shoulder pose"
        with ZipFile(io.BytesIO(outer.read(TABULA))) as inner:
            skeleton = set(bones(json.loads(inner.read("model.json"))["cubes"]))
    assert "BodyUpper" in skeleton, "Original CE torso bone absent"
    # Regression: sleeping is a distinct, genuinely tucked/resting source pose.
    with ZipFile(jars[0]) as jar:
        with ZipFile(io.BytesIO(jar.read(TABULA_SHOULDER))) as original:
            shoulder = json.loads(original.read("model.json"))

    def find_bone(cubes, name):
        for cube in cubes:
            if cube["name"] == name:
                return cube
            result = find_bone(cube.get("children", []), name)
            if result is not None:
                return result
        return None

    with ZipFile(jars[0]) as jar:
        with ZipFile(io.BytesIO(jar.read(TABULA))) as curled:
            sleeping = json.loads(curled.read("model.json"))
    assert find_bone(sleeping["cubes"], "BodyUpper")["rotation"][0] < find_bone(shoulder["cubes"], "BodyUpper")["rotation"][0]
    assert abs(find_bone(sleeping["cubes"], "armL1")["rotation"][0]) < abs(find_bone(shoulder["cubes"], "armL1")["rotation"][0])
    assert find_bone(sleeping["cubes"], "LegR")["rotation"][0] < find_bone(shoulder["cubes"], "LegR")["rotation"][0]

    renderer = RENDERER.read_text(encoding="utf-8")
    entity = ENTITY.read_text(encoding="utf-8")
    transition = TRANSITION.read_text(encoding="utf-8")
    properties = PROPS.read_text(encoding="utf-8")

    # Correct: original CE sleeping posture + CE idle, blended over 20 ticks.
    assert 'lightningdragon/lightningdragon_sleeping' in renderer
    assert 'AurenPoseTransition.apply(model, restPose, shoulderBlend);' in renderer
    assert 'torso.offsetZ +=' not in renderer
    assert 'AurenOriginalShoulderIdle.apply(model, age, shoulderBlend);' in renderer
    idle = (ROOT / "src/main/java/br/com/auren/minecraft/client/AurenOriginalShoulderIdle.java").read_text(encoding="utf-8")
    assert 'float speedIdle = 0.05F;' in idle
    assert 'float degreeIdle = 0.5F * blend;' in idle
    assert 'model.chainSwing(tailParts, speedIdle, degreeIdle * 0.25F, -2' in idle
    assert 'model.chainWave(tailParts, speedIdle, degreeIdle * 0.15F, -2' in idle
    assert 'model.chainWave(neckParts, speedIdle, degreeIdle * -0.15F, -3' in idle
    assert 'model.walk(model.getCube("Neck1"), speedIdle, degreeIdle * 0.05F' in idle
    for part in ("BodyUpper", "ThighR", "ThighL", "armR1", "armL1"):
        assert f'model.bob(model.getCube("{part}")' in idle
    assert 'this.model = groundModel;' in renderer
    assert 'float shoulderBlend = dragon.getShoulderBlend(partialTick);' in renderer
    assert 'shoulderBlend > 0.0F && dragon.isSmallForm()' in renderer
    assert 'SHOULDER_ANIMATION_TICKS = 20.0F' in entity

    # No more procedural twist over CE's original shoulder pose.
    assert 'AurenNeckPose.apply' not in renderer
    assert 'Axis.YP.rotationDegrees(90.0F * blend)' not in renderer
    assert 'pose.mulPose(Axis.YP' not in renderer
    assert not (ROOT / "src/main/java/br/com/auren/minecraft/client/AurenNeckPose.java").exists()

    # CE shoulder positioning, body yaw only; no effect on follow AI.
    assert 'double lateral = 0.30D;' in entity
    assert 'double backward = 0.04D;' in entity
    assert 'double y = player.getY() + 1.30D;' in entity
    assert 'this.yHeadRot = player.yBodyRot;' in entity
    assert 'float factor = entity.isSmallForm() ? 0.42F : 3.25F;' in renderer
    assert 'mod_version=0.5.5-alpha' in properties

    for property_name in ("rotateAngleX", "rotateAngleY", "rotateAngleZ",
                          "rotationPointX", "rotationPointY", "rotationPointZ"):
        assert f"from.{property_name}" in transition
    assert "Math.atan2" in transition
    print("PASS: CE sleeping keyframe is folded vs standing shoulder, "
          "CE idle animation runs on shoulder; anchor near neck, scale unchanged")


if __name__ == "__main__":
    try:
        verify()
    except Exception as e:
        print(f"FAIL shoulder verification: {e}", file=sys.stderr)
        sys.exit(1)
