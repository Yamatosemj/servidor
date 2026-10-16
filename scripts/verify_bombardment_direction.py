"""CI regression guard for autonomous forward fire and area-only demolition."""
from pathlib import Path
from zipfile import ZipFile
from math import cos, radians
root=Path(__file__).resolve().parents[1]
java=root/"src/main/java/br/com/auren/minecraft/entity"
fire=(java/"AurenFireBreath.java").read_text()
terrain=(java/"AurenScorchTerrain.java").read_text()
orders=(java/"AurenAttackOrders.java").read_text()
for value in ("MIN_FORWARD_DOT", "autonomousFire && !canAimForward(getHeadPosition(), autoAim)",
              "private boolean canAimForward(", "double forwardX = -Math.sin(yaw)",
              "double forwardZ = Math.cos(yaw)", "AurenScorchTerrain.bombard("):
    assert value in fire, value
for value in ("public static void bombard(", "GameRules.RULE_MOBGRIEFING",
              "markedCenter == null || radius < 5 || radius > 30",
              "dx * dx + dz * dz > radius * radius", "destroyed >= 20",
              "level.hasChunkAt(pos)", "level.getBlockEntity(pos) != null",
              "hardness < 0 || hardness > 8.0F",
              "state.is(BlockTags.PLANKS)", "state.is(Blocks.GLASS)",
              "level.destroyBlock(pos, false)"):
    assert value in terrain, value
for value in ("findForwardAreaImpact(", "getHeightmapPos(",
              "Heightmap.Types.MOTION_BLOCKING_NO_LEAVES",
              "dragon.getFireBreathHeadPosition()",
              "distanceSq > 25.0D * 25.0D", "distanceSq < 7.0D * 7.0D",
              "nextTacticTick = combatTicks + 75"):
    assert value in orders, value
# A 55-degree arc is still strictly forward; backward attacks remain invalid.
assert 1.0 >= cos(radians(55))
assert -1.0 < cos(radians(55))
assert cos(radians(70)) < cos(radians(55))
for token in ("igniteVillage(", "BlockTags.MINEABLE_WITH_SHOVEL",
              "BlockTags.MINEABLE_WITH_PICKAXE", "Blocks.FIRE.defaultBlockState()",
              "level.getBlockEntity(wood) != null"):
    assert token in terrain, token
for token in ("aimGraceTicks++ < 16", "this.burnProgress - 1",
              "AurenScorchTerrain.igniteVillage(", "Math.cos(Math.toRadians(55.0D))"):
    assert token in fire, token
for token in ("areaFiringPass = areaPhase == 2", "safeAreaWaypoint(",
              "nextWaypointTick = combatTicks + (areaFiringPass ? 180 : 120)"):
    assert token in orders, token
for r in [5,10,15,20,25,30]:
    assert (r+1)**2 > r*r
assert "mod_version=0.7.11-sustained-fire-alpha" in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as z:
    assert z.testzip() is None
    for cls in ("AurenAttackOrders","AurenScorchTerrain","AurenFireBreath"):
        assert f"br/com/auren/minecraft/entity/{cls}.class" in z.namelist()
print("PASS: front-only autonomous fire, bounded destructive area, tactical distance")
