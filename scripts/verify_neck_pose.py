"""Verify the visual reference's intended geometry is represented in code.

The expected shape can only be APPROVED after a front/back in-game Zalith
screenshot. This catches keyframe regressions and accidental 90-degree flips.
"""
import io
import json
import re
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/br/com/auren/minecraft"
BASE = "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_"


def bone_names(items):
    for item in items:
        yield item["name"]
        yield from bone_names(item.get("children", []))


def verify():
    jars = list((ROOT / "build/libs").glob("*.jar"))
    assert len(jars) == 1, f"Expected 1 JAR: {jars}"

    skeletons = {}
    with ZipFile(jars[0]) as jar:
        for pose in ("ground", "sitting_shoulder", "sleeping"):
            with ZipFile(io.BytesIO(jar.read(BASE + pose + ".tbl"))) as tbl:
                skeletons[pose] = set(bone_names(json.loads(tbl.read("model.json"))["cubes"]))
    assert skeletons["ground"] == skeletons["sitting_shoulder"] == skeletons["sleeping"]

    render = (SOURCE / "client/AurenDragonRenderer.java").read_text()
    neck = (SOURCE / "client/AurenNeckPose.java").read_text()
    transition = (SOURCE / "client/AurenPoseTransition.java").read_text()
    entity = (SOURCE / "entity/AurenDragon.java").read_text()
    version = (ROOT / "gradle.properties").read_text()

    assert '"lightningdragon/lightningdragon_sitting_shoulder"' in render
    assert '"lightningdragon/lightningdragon_sleeping"' in render
    assert "AurenPoseTransition.apply(model, awake, shoulderBlend);" in render
    assert "AurenNeckPose.apply(model, shoulderBlend);" in render
    assert "AurenOriginalShoulderIdle.apply(model, age, shoulderBlend);" in render
    assert "AurenPoseTransition.applyBones(model, folded, shoulderBlend," in render
    assert '"armL1", "armL2", "armR1", "armR2"' in render
    # The original CE uses dynamic X body pitch while mounted. This is
    # allowed and must not reintroduce the old static 90-degree shoulder yaw.
    assert "if (entity.isVehicle())" in render
    assert "pose.mulPose(Axis.XP.rotationDegrees(entity.getRiderVisualPitch(partialTick)))" in render
    assert "Axis.YP.rotationDegrees(90" not in render
    assert "torso.offsetZ" not in render

    for name in ("Neck1", "Neck2", "Neck3", "Tail1", "Tail2", "Tail3", "Tail4", "Tail5", "Head"):
        assert f'"{name}"' in neck
    named = set(re.findall(r'rotate[XY]\(model, "([^"]+)"', neck))
    assert named <= skeletons["ground"], f"Unknown bones: {named-skeletons['ground']}"
    assert 'rotateY(model, "Neck2", -22.0F * blend)' in neck
    assert 'rotateY(model, "Neck3", -18.0F * blend)' in neck
    assert 'rotateY(model, "Tail3", -25.0F * blend)' in neck
    for wing in ("armL1", "armL2", "armR1", "armR2"):
        assert f'"{wing}"' not in neck, "Original CE supplies wing pose"

    assert "public static void applyBones(" in transition
    for attr in ("rotateAngleX", "rotateAngleY", "rotateAngleZ",
                 "rotationPointX", "rotationPointY", "rotationPointZ"):
        assert f"from.{attr}" in transition
    assert "Math.atan2" in transition

    assert "double lateral = 0.075D;" in entity
    assert "double backward = 0.18D;" in entity
    assert "double y = player.getY() + player.getBbHeight() * 0.76D;" in entity
    assert "this.yHeadRot = player.yBodyRot;" in entity
    assert "SHOULDER_ANIMATION_TICKS = 20.0F;" in entity
    assert "float factor = entity.isSmallForm() ? 0.42F : 3.25F;" in render
    assert "mod_version=0.7.10-directional-bombardment-alpha" in version
    print("PASS: reference neck wrap + CE folded wings, awake head, no root yaw, original anchor")


if __name__ == "__main__":
    verify()
