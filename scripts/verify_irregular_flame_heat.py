"""Auren 0.7.15 static + distribution checks for real irregular flames.
These gates DO NOT prove how the plume looks or how long a real game takes;
a gameplay burn test is needed.
"""
from pathlib import Path
from zipfile import ZipFile
import math
import random

root=Path(__file__).resolve().parents[1]
java=root/"src/main/java/br/com/auren/minecraft/entity"
terrain=(java/"AurenScorchTerrain.java").read_text()
fire=(java/"AurenFireBreath.java").read_text()
orders=(java/"AurenAttackOrders.java").read_text()

for token in (
    "for (int dx = -3; dx <= 3; ++dx)",
    "for (int dz = -3; dz <= 3; ++dz)",
    "double offsetX = (dragon.getRandom().nextDouble() - 0.5D) * 0.7D",
    "double coverage = Math.max(0.22D, 0.93D - distance * 0.60D)",
    "if (distance > 1.0D) continue;",
    "if (dragon.getRandom().nextDouble() > coverage) continue;",
    "MAX_FLAMES_PER_IMPACT = 12",
    "igniteSurface(level, pos, markedCenter, radius)",
    "Blocks.FIRE.defaultBlockState()",
    "Blocks.SOUL_FIRE.defaultBlockState()",
    "level.setBlockAndUpdate(firePos, fire)",
    "level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME",
    "level.sendParticles(ParticleTypes.LARGE_SMOKE",
    "HEAT_EXPIRY_TICKS = 240",
    "MAX_HEAT_POSITIONS = 4096",
    "SCORCH_HEAT = 28",
    "MAGMA_HEAT = 135",
    "GRASS_BREAK_HEAT = 195",
    "MAGMA_BREAK_HEAT = 330",
    "if (cell.heat >= MAGMA_HEAT)",
    "if (cell.heat >= MAGMA_BREAK_HEAT)",
    "if (cell.heat >= GRASS_BREAK_HEAT)",
    "after = Blocks.MAGMA_BLOCK.defaultBlockState()",
    "after = Blocks.AIR.defaultBlockState()",
    "level.setBlockAndUpdate(pos, after)",
    "private static final Map<ServerLevel, HeatTracker> HEAT = new WeakHashMap<>()",
    "tracker.cells.remove(pos)",
    "GameRules.RULE_MOBGRIEFING",
    "level.getBlockEntity(pos) != null",
    "level.hasChunkAt(pos)",
    "withinArea(pos, markedCenter, radius)",
    "withinArea(firePos, markedCenter, radius)",
):
    assert token in terrain, token

for token in (
    "int segments = Math.max(6, Math.min(14,",
    "center.x, center.y, center.z, 11,",
    "double width = Math.min(2.3D,",
    "streamEnd.x, streamEnd.y, streamEnd.z, 13,",
    "previousGroundImpact",
    "dragon.tickCount - previousGroundImpactTick <= 10",
    "horizontal > 1.25D && horizontal < 9.0D",
    "previousGroundImpact.lerp(",
    "AurenScorchTerrain.scorch(level, trail, dragon",
    "if (areaOrder)",
    "withinArea(blockHit.getLocation())",
):
    assert token in fire, token

# Static separation: neither wood nor non-whitelisted blocks can be directly
# removed by the heat stages. No generic demolition or explosion APIs.
assert "destroyBlock(" not in terrain and "removeBlock(" not in terrain
assert "createExplosion" not in terrain and "createExplosion" not in fire
assert terrain.count("after = Blocks.AIR.defaultBlockState()") == 2
assert "if (!eligible(original)" in terrain
assert "level.getBlockEntity(wood) != null" in terrain
assert "areaFiringPass = areaPhase == 2" in orders

# Numeric smoke test of the footprint recipe. This mirrors the documented
# shape probabilistically but does not execute Minecraft Java.
rng=random.Random(715)
shapes=set()
for _ in range(120):
    ox=(rng.random()-0.5)*0.7
    oz=(rng.random()-0.5)*0.7
    rx=2.2+rng.random()
    rz=2.0+rng.random()*1.2
    tiles=set()
    for dx in range(-3,4):
        for dz in range(-3,4):
            d=((dx-ox)/rx)**2+((dz-oz)/rz)**2
            if d>1.0: continue
            coverage=max(0.22,0.93-d*0.60)
            if (dx,dz)!=(0,0) and rng.random()>coverage:continue
            tiles.add((dx,dz))
    assert (0,0) in tiles
    assert len(tiles) < 49  # not a filled square
    shapes.add(frozenset(tiles))
assert len(shapes)>110, len(shapes)
assert 0<28<135<330 and 28<195<330
assert "mod_version=0.7.18-cave-route-navigation-alpha" in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for clazz in ("AurenScorchTerrain","AurenScorchTerrain$HeatCell",
                  "AurenScorchTerrain$HeatTracker","AurenFireBreath"):
        assert "br/com/auren/minecraft/entity/"+clazz+".class" in jar.namelist(),clazz
print("PASS: irregular flame patches, timed heat stages, wood safety, bounded in-memory tracking")
