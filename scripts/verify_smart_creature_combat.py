"""Guard actual creature-combat priorities and unchanged area/fire safety.

Checks source/packaged classes; gameplay quality still requires playtesting.
"""
from pathlib import Path
from zipfile import ZipFile
root = Path(__file__).resolve().parents[1]
j = root / "src/main/java/br/com/auren/minecraft/entity"
orders=(j/"AurenAttackOrders.java").read_text()
fire=(j/"AurenFireBreath.java").read_text()
terrain=(j/"AurenScorchTerrain.java").read_text()
for token in (
  "sightLostTicks == 20 && tactic != AirAttack.HOVER_BLAST",
  "private boolean clearViewFrom(ServerLevel level, Vec3 vantage, LivingEntity enemy)",
  "level.clip(new ClipContext(vantage, enemy.getEyePosition()",
  "canFlyTo(level, point) && clearViewFrom(level, point, enemy)",
  "enemy.getDeltaMovement()", "lead.lengthSqr() > 25.0D",
  "lead = lead.normalize().scale(5.0D)",
  "nextTacticTick = combatTicks + 125",
  "previous == AirAttack.HOVER_BLAST",
  "tactic = AirAttack.SCORCH_STREAM",
  "tactic = AirAttack.TACKLE",
  "tactic = AirAttack.HOVER_BLAST",
  "streamLeg % 2 == 0 ? 1.0D : -1.0D",
  "dragon.getFlightController().patrolAttack(owner, recovery, false)",
):
    assert token in orders, token
for token in ("areaFiringPass = areaPhase == 2",
              "nextSweepTick = combatTicks + 3",
              "findForwardAreaImpact(", "safeAreaWaypoint(",
              "nextWaypointTick = combatTicks + (areaFiringPass ? 180 : 120)"):
    assert token in orders, token
assert "canAimForward(" not in fire
assert "AurenScorchTerrain.bombard(" not in fire
assert "destroyBlock(" not in terrain
assert "Blocks.FIRE.defaultBlockState()" in terrain
assert "mod_version=0.7.13-smart-creature-combat-alpha" in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1, jars
with ZipFile(jars[0]) as f:
    assert f.testzip() is None
    for clazz in ("AurenAttackOrders","AurenFireBreath","AurenFlightController"):
        assert "br/com/auren/minecraft/entity/"+clazz+".class" in f.namelist()
print("PASS: LOS-aware CE attacks, predictive tackle, continuous area + safe fire unchanged")
