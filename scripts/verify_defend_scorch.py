"""v0.7.6 regression gate: owner protection + baby bites + original CE scorch.

Static verification + compiled JAR inspection, not a substitute for gameplay QA.
"""
from pathlib import Path
from zipfile import ZipFile
import json
import tomllib

ROOT=Path(__file__).resolve().parents[1]
J=ROOT/"src/main/java/br/com/auren/minecraft"
dragon=(J/"entity/AurenDragon.java").read_text()
fire=(J/"entity/AurenFireBreath.java").read_text()
terrain=(J/"entity/AurenScorchTerrain.java").read_text()
registry=(J/"entity/AurenScorchedBlocks.java").read_text()
scorched=(J/"entity/AurenScorchedBlock.java").read_text()
main=(J/"AurenMinecraft.java").read_text()
animations=(J/"client/AurenOriginalAnimations.java").read_text()
renderer=(J/"client/AurenDragonRenderer.java").read_text()
build=(ROOT/"build.gradle").read_text()
version=(ROOT/"gradle.properties").read_text()

# Both life stages can protect owner; no harm to their own rider or pets.
for expected in (
    "new OwnerHurtByTargetGoal(this)",
    "new OwnerHurtTargetGoal(this)",
    "new MeleeAttackGoal(this, 1.15D, true)",
    "this.isOnShoulder() || this.isVehicle() || this.isAlliedTo(living)",
    "this.isSmallForm() ? 3.0F : 7.0F",
    "this.entityData.set(BITE_ATTACK_TICKS, 12)",
    "this.entityData.set(BITE_ATTACK_TICKS, remainingBite - 1)",
    ".add(Attributes.ATTACK_DAMAGE, 7.0D)",
):
    assert expected in dragon, f"Owner defense invariant missing: {expected}"
assert "this.isTame()" in dragon
assert "this.isOnShoulder()" in dragon
assert "this.isVehicle()" in dragon
assert "this.setTarget(null);" in dragon  # stale target cleared on world reload
assert 'loadSeries("bite", 3)' in animations
assert '"BodyUpper", "Neck1", "Neck2", "Neck3", "Head", "Jaw"' in animations
assert "applyBitePose(model, dragon)" in renderer

# CE original terrain materials retained, but 0.7.15 uses an Auren-specific
# irregular fire footprint and gradual temperature, not a fixed 3x3x3 cube.
# Verify griefing/chunk/block-entity protection and registry stability.
for token in (
    "IafDragonDestructionManager.transformBlockFire",
    "GameRules.RULE_MOBGRIEFING",
    "for (int dx = -3; dx <= 3; ++dx)",
    "for (int dz = -3; dz <= 3; ++dz)",
    "applyHeat(level, pos, tracker, time, gain)",
    "level.getBlockEntity(pos) != null",
    "level.hasChunkAt(pos)",
    "level.setBlockAndUpdate(pos, after)",
    "original.getBlock() instanceof SpreadingSnowyDirtBlock",
    "original.is(Blocks.DIRT)",
    "original.is(Blocks.GRAVEL)",
    "original.is(Blocks.COBBLESTONE)",
    "original.is(BlockTags.BASE_STONE_OVERWORLD)",
    "original.is(Blocks.DIRT_PATH)",
):
    assert token in terrain, f"CE terrain conversion invariant missing: {token}"
assert "AurenScorchTerrain.scorch(level, scorchCenter, dragon);" in fire
assert "Blocks.SOUL_FIRE.defaultBlockState()" in fire
assert "ParticleTypes.SOUL_FIRE_FLAME" in fire
assert "randomTick(" in scorched
assert "random.nextInt(8) == 0" in scorched
assert "level.setBlockAndUpdate(pos, restored)" in scorched
assert "AurenScorchedBlocks.register(modBus)" in main
assert "ENTITIES.register(\"auren\"" in main
assert "DeferredRegister.Items" not in main and "DeferredRegister.Items" not in registry
assert "BlockItem(" not in registry and "BlockItem(" not in main
assert "0.7.17-cave-aim-fix-alpha" in version

names=["chared_grass","chared_dirt","chared_gravel",
       "chared_cobblestone","chared_stone","chared_dirt_path"]
assert registry.count('BLOCKS.register("chared_')==6
for n in names:
    assert f'"{n}"' in registry, n
    assert f"assets/aurenminecraft/blockstates/{n}.json" in build
    assert f"assets/aurenminecraft/models/block/{n}.json" in build
for m in ["bite1","bite2","bite3"]:
    assert "lightningdragon_"+m+".tbl" in build
tag=json.loads((ROOT/"src/main/resources/data/minecraft/tags/block/soul_fire_base_blocks.json").read_text())
assert tag["replace"] is False
assert sorted(tag["values"])==sorted(["aurenminecraft:"+n for n in names])
jars=list((ROOT/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as mod:
    assert mod.testzip() is None
    files=set(mod.namelist())
    for n in names:
        for p in [f"assets/aurenminecraft/blockstates/{n}.json",
                  f"assets/aurenminecraft/models/block/{n}.json",
                  f"data/aurenminecraft/loot_table/blocks/{n}.json"]:
            assert p in files,p
        a=mod.read(f"assets/aurenminecraft/blockstates/{n}.json").decode()
        b=mod.read(f"assets/aurenminecraft/models/block/{n}.json").decode()
        assert "aurenminecraft:block/" in a,a
        assert "iceandfire:" not in a and "iceandfire:" not in b
        assert json.loads(mod.read(f"data/aurenminecraft/loot_table/blocks/{n}.json"))["pools"]==[]
    for name in ["chared_stone","chared_dirt","chared_grass_top","chared_gravel"]:
        assert f"assets/aurenminecraft/textures/block/{name}.png" in files
    for name in ["bite1","bite2","bite3"]:
        assert f"assets/aurenminecraft/models/tabula/lightningdragon/lightningdragon_{name}.tbl" in files
    assert "data/minecraft/tags/block/soul_fire_base_blocks.json" in files
    for n in ["AurenScorchedBlocks","AurenScorchTerrain","AurenScorchedBlock"]:
        assert f"br/com/auren/minecraft/entity/{n}.class" in files
    assert not any(p.startswith(("com/iafenvoy/iceandfire/","assets/iceandfire/")) for p in files)
    toml=tomllib.loads(mod.read("META-INF/neoforge.mods.toml").decode())
    assert [m["modId"] for m in toml["mods"]]==["aurenminecraft"]
print("PASS: owner target defense, BABY melee+CE bite pose, six CE burned terrains, no extra items or entities")
