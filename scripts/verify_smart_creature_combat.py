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
# Audit regression gates: orders never bypass collision tests or fight
# against the ground-wandering goal; marked orders are achievable on receipt.
dragon=(j/"AurenDragon.java").read_text()
commands=(root/"src/main/java/br/com/auren/minecraft/AurenCommands.java").read_text()
assert "AurenDragon.this.getFlightMode() == AurenFlightController.GROUNDED" in dragon
assert dragon.count("&& !AurenDragon.this.getAttackOrders().hasOrder()") >= 2
assert "&& waypoint != null && combatTicks < nextWaypointTick" in orders
assert "tackleApproachPoint(level, enemy)" in orders
assert "recoveryPoint(level, enemy)" in orders
assert "dragon.getBoundingBox().inflate(0.7D).intersects(enemy.getBoundingBox())" in orders
assert "dragon.level().hasChunkAt(enemy.blockPosition())" in orders
assert "MAX_MARK_DISTANCE * MAX_MARK_DISTANCE" in orders
assert "Vec3.atCenterOf(center)" in orders
assert "Alvo inválido ou além de 64 blocos" in commands
assert "patrolAttack(owner, next, true)" not in orders
assert "level.hasChunkAt(BlockPos.containing(next))) {" not in orders
assert "mod_version=0.7.20-reliability-review-alpha" in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1, jars
with ZipFile(jars[0]) as f:
    assert f.testzip() is None
    for clazz in ("AurenAttackOrders","AurenFireBreath","AurenFlightController"):
        assert "br/com/auren/minecraft/entity/"+clazz+".class" in f.namelist()
print("PASS: collision-safe CE combat, no wandering conflicts, bounded marks, existing fire preserved")
