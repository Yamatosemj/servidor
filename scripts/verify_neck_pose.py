"""Verify the v0.5.7 static shoulder target and scale resets (not a visual test)."""
from __future__ import annotations
import io
import json
import re
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
BASE = "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_"
SRC = ROOT / "src/main/java/br/com/auren/minecraft"
CONFIG = ROOT / "docs/AUREN_SHOULDER_POSE_V057.json"

def flatten(cubes):
    out={}
    def walk(nodes,parent=None):
        for n in nodes:
            assert n["name"] not in out
            out[n["name"]]=parent
            walk(n.get("children",[]),n["name"])
    walk(cubes)
    return out

def verify():
    cfg=json.loads(CONFIG.read_text(encoding="utf-8"))
    assert cfg["version"]=="0.5.7-alpha"
    jars=list((ROOT/"build/libs").glob("*.jar"))
    assert len(jars)==1, f"Expected one JAR: {jars}"
    with ZipFile(jars[0]) as z:
        skeletons={}
        for pose in ("ground","sitting_shoulder","sleeping"):
            with ZipFile(io.BytesIO(z.read(BASE+pose+".tbl"))) as source:
                skeletons[pose]=flatten(json.loads(source.read("model.json"))["cubes"])
    assert skeletons["ground"]==skeletons["sitting_shoulder"]==skeletons["sleeping"]
    assert len(skeletons["ground"])==113
    pose=(SRC/"client/AurenNeckPose.java").read_text(encoding="utf-8")
    renderer=(SRC/"client/AurenDragonRenderer.java").read_text(encoding="utf-8")
    entity=(SRC/"entity/AurenDragon.java").read_text(encoding="utf-8")
    old=(SRC/"client/AurenOriginalShoulderIdle.java").read_text(encoding="utf-8")
    targets={}
    for name,x,y,z in re.findall(
            r'new PoseTarget\("([^"]+)", (-?\d+\.\d+)F, (-?\d+\.\d+)F, (-?\d+\.\d+)F\)',pose):
        targets[name]=[float(x),float(y),float(z)]
    assert targets==cfg["anglesDegrees"],f"Java/JSON mismatch {targets.keys() ^ cfg['anglesDegrees'].keys()}"
    assert set(targets)<=set(skeletons["ground"])
    assert "AurenNeckPose.resetTransientScale(model);" in renderer
    assert "AurenNeckPose.apply(model, awake, shoulderBlend);" in renderer
    assert "AurenOriginalShoulderIdle.apply(model, age, shoulderBlend);" in renderer
    assert "model.resetToDefaultPose();" in renderer
    assert "setShouldScaleChildren(false)" in pose
    assert "setShouldScaleChildren(true)" in pose
    assert "bone.setScale(1.0F, 1.0F, 1.0F);" in pose or "box.setScale(1.0F, 1.0F, 1.0F);" in pose
    assert "Mth.lerp(t, 1.0F, BABY_WING_COMPACT_SCALE)" in pose
    assert "Mth.lerp(t, 1.0F, BABY_TAIL_COMPACT_SCALE)" in pose
    assert "return" in old and "model.chainWave" in old
    assert 'FOLDED_WINGS_POSE' not in renderer
    assert "pose.mulPose(" not in renderer
    assert "torso.offsetZ" not in renderer
    assert "double lateral = -0.13D;" in entity
    assert "double backward = 0.31D;" in entity
    assert "player.getBbHeight() * 0.572D" in entity
    assert abs(cfg["anchor"]["lateral"]+0.13)<1e-8
    assert abs(cfg["anchor"]["backward"]-0.31)<1e-8
    assert abs(cfg["anchor"]["heightFactor"]-0.572)<1e-8
    assert "this.adultChunkLoader.tick(this, serverLevel)" in entity
    assert "float factor = entity.isSmallForm() ? 0.42F : 3.25F;" in renderer
    assert "mod_version=0.5.7-alpha" in (ROOT/"gradle.properties").read_text(encoding="utf-8")
    print("PASS: 113 CE bones, exactly matching authored static target, scale reset, anchor and follow preserved")

if __name__=="__main__":
    verify()
