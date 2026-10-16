"""Regression: wood burns only by vanilla fire, terrain erodes only with heat.
Short fire puffs never demolish anything; prolonged grass/magma heat can
remove those whitelisted terrain blocks without generic destroyBlock calls.

Static checks complement NeoForge compilation, not a substitute for playtest.
"""
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
java=root/"src/main/java/br/com/auren/minecraft"
fire=(java/"entity/AurenFireBreath.java").read_text()
terrain=(java/"entity/AurenScorchTerrain.java").read_text()
animation=(java/"client/AurenOriginalAnimations.java").read_text()
orders=(java/"entity/AurenAttackOrders.java").read_text()
for forbidden in ("destroyBlock(", "removeBlock(", "setBlock(\"air\"", "createExplosion"):
    assert forbidden not in fire and forbidden not in terrain, forbidden
assert "AurenScorchTerrain.bombard(" not in fire
assert "public static void bombard(" not in terrain
assert "canAimForward(" not in fire
assert "MIN_FORWARD_DOT" not in fire
assert "getHeadPosition()" in fire
assert "neckLower * 0.65D" in fire
assert "AurenScorchTerrain.igniteVillage(" in fire
assert "AurenScorchTerrain.scorch(level, scorchCenter, dragon);" in fire
for token in ("lowerBreathBone(", "streamBreath", "streamCharge",
              '"Neck1", 0.06F', '"Neck2", 0.10F',
              '"Neck3", 0.12F', '"Head", 0.17F'):
    assert token in animation, token
for token in ("public static void igniteVillage(", "GameRules.RULE_MOBGRIEFING",
              "BlockTags.WOODEN_STAIRS", "BlockTags.WOODEN_SLABS",
              "BlockTags.WOODEN_DOORS", "BlockTags.PLANKS",
              "level.getBlockEntity(wood) != null", "lit >= 16",
              "Blocks.FIRE.defaultBlockState().canSurvive(level, firePos)",
              "level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState())"):
    assert token in terrain, token
for token in ("findForwardAreaImpact(", "areaFiringPass = areaPhase == 2",
              "safeAreaWaypoint(", "nextSweepTick = combatTicks + 3"):
    assert token in orders, token
assert "0.7.20-reliability-review-alpha" in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1, jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for cls in ("AurenAttackOrders", "AurenScorchTerrain", "AurenFireBreath"):
        assert "br/com/auren/minecraft/entity/"+cls+".class" in jar.namelist()
print("PASS: fire ignition, no instant wooden demolition, bounded heat damage, CE breath pose")
