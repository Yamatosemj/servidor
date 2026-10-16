"""v0.7.5: original CE attack-stream poses + volumetric BLUE soul-fire.

CI proves source integration, model archive integrity and matching Tabula
skeletons. It cannot prove visual quality inside Minecraft.
"""
from __future__ import annotations
import io
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/br/com/auren/minecraft"
fire = (JAVA / "entity/AurenFireBreath.java").read_text(encoding="utf-8")
lightning = (JAVA / "entity/AurenLightningBreath.java").read_text(encoding="utf-8")
dragon = (JAVA / "entity/AurenDragon.java").read_text(encoding="utf-8")
poses = (JAVA / "client/AurenOriginalAnimations.java").read_text(encoding="utf-8")
renderer = (JAVA / "client/AurenDragonRenderer.java").read_text(encoding="utf-8")
build = (ROOT / "build.gradle").read_text(encoding="utf-8")
version = (ROOT / "gradle.properties").read_text(encoding="utf-8")

# Blue FIRE without changing the source CE impact/owner/safety/length logic.
for token in (
    "ParticleTypes.SOUL_FIRE_FLAME",
    "ParticleTypes.SMOKE",
    "Blocks.SOUL_FIRE.defaultBlockState().canSurvive(level, firePos)",
    "level.setBlockAndUpdate(firePos, Blocks.SOUL_FIRE.defaultBlockState())",
    "double plumeLength = head.distanceTo(streamEnd)",
    "double width = Math.min(2.0D,",
    "level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME",
    "CE_REACH = 10.0D * CE_STAGE",
    "CE_IMPACT_RADIUS = 3.5D",
    "CE_FIRE_DAMAGE_SCALE = 2.0F",
    "this.burnProgress == CE_BURN_PROGRESS_MAX && dragon.tickCount % 5 == 0",
    "target.igniteForSeconds(CE_IGNITE_SECONDS)",
    "dragon.isRiderLightning()",
    "dragon.isOwnedBy(rider)",
):
    assert token in fire, f"Blue-fire safety/effect invariant missing: {token}"
assert "ParticleTypes.FLAME," not in fire, "Orange stream particle reintroduced"
assert "Blocks.FIRE.defaultBlockState()" not in fire, "Red ground fire reintroduced"
assert "level.hasChunkAt(BlockPos.containing(burnTarget))" in fire
assert "level.hasChunkAt(BlockPos.containing(center))" in fire
assert "this.burnProgress / (double) CE_BURN_PROGRESS_MAX" in fire

# Server-synced animation clock: both R and G use source upstream poses.
for token in (
    "BREATH_ATTACK_TICKS",
    "builder.define(BREATH_ATTACK_TICKS, 0)",
    "public int getBreathAttackTicks()",
    "public float getBreathPoseBlend(float partialTick)",
    "this.fireBreath.getAttackTicks()",
    "this.lightningBreath.getAttackTicks()",
    "this.entityData.set(BREATH_ATTACK_TICKS, attackTicks)",
):
    assert token in dragon, f"Attack pose sync invariant missing: {token}"
assert "int getAttackTicks()" in fire and "int getAttackTicks()" in lightning

for token in (
    'loadSeries("attack_stream_charge", 3)',
    'load("attack_stream_breath")',
    "public void applyBreathPose(",
    "this.streamCharge.get(0)",
    "this.streamCharge.get(1)",
    "this.streamCharge.get(2)",
    "this.streamBreath",
    '"BodyUpper", "Neck1", "Neck2", "Neck3", "Head", "Jaw"',
):
    assert token in poses, f"Original CE Tabula attack pose not wired: {token}"
assert "AurenOriginalAnimations.instance().applyBreathPose(model, dragon, partialTick);" in renderer
assert "AurenOriginalAnimations.instance().apply(model, dragon, limbSwing, limbSwingAmount, age);" in renderer
assert "new AurenRiderFeatureRenderer(this)" in renderer
assert "this.lightningRenderer.render(partialTick, pose, buffer)" in renderer
assert "mod_version=0.7.10-directional-bombardment-alpha" in version

original = {
    "attack_stream_charge1": "7865c66437438b74307df427d2febcac474426f9",
    "attack_stream_charge2": "3fc377bdc32bc6f7c66ebdad2e258be855ca8f6d",
    "attack_stream_charge3": "40988fd55cb0954ba72d8ae2ba99e24ab316d591",
    "attack_stream_breath": "f5183e5bcc089caac704d3c6a7937725dcbc5d62",
}
for name, git_sha in original.items():
    assert name in build and git_sha in build, f"Unpinned source pose: {name}"

jars = list((ROOT / "build/libs").glob("aurenminecraft-*.jar"))
assert len(jars) == 1, jars
root = "assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_"
with ZipFile(jars[0]) as mod:
    assert mod.testzip() is None
    for name in original:
        path = root + name + ".tbl"
        assert path in mod.namelist(), f"Missing source pose: {path}"
        with ZipFile(io.BytesIO(mod.read(path))) as original_tabula:
            assert original_tabula.testzip() is None
            data = json.loads(original_tabula.read("model.json"))
            assert data["cubes"], f"Empty upstream Tabula skeleton: {name}"
    names = set(mod.namelist())
    assert "br/com/auren/minecraft/entity/AurenFireBreath.class" in names
    assert "br/com/auren/minecraft/entity/AurenLightningBreath.class" in names
    assert not any(p.startswith(("com/iafenvoy/iceandfire/",
                                "assets/iceandfire/", "data/iceandfire/")) for p in names)
print("PASS: SHA-pinned original CE charge/breath poses and blue flame plume, safe impact, one Auren")
