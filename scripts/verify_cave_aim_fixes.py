"""0.7.17 regression: point-at-self yaw glitch and cave-navigation freeze.

This checks deterministic source boundaries and the built JAR. Gameplay
validation still requires Minecraft tests against a real cave entrance.
"""
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
j=root/"src/main/java/br/com/auren/minecraft/entity"
hunt=(j/"AurenRelentlessHunt.java").read_text()
flight=(j/"AurenFlightController.java").read_text()
mover=(j/"AurenIafDragonFlightManager.java").read_text()
orders=(j/"AurenAttackOrders.java").read_text()
fire=(j/"AurenFireBreath.java").read_text()
terrain=(j/"AurenScorchTerrain.java").read_text()
for token in (
  "public boolean holdFacing(LivingEntity targetOwner, Vec3 aim)",
  "this.iafFlightManager.setFlightTarget(null)",
  "this.holding = true;",
  "this.holdFacing = aim;",
  "if (this.holding) {",
  "dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.72D))",
  "dragon.setYRot(AurenIafDragonFlightManager.approachDegrees(",
  "dragon.yBodyRot = dragon.getYRot()",
):
    assert token in flight, token
assert "dragon.position().add(0.0D, 0.12D, 0.0D)" not in hunt
assert "dragon.getFlightController().holdFacing(owner, strike)" in hunt
assert mover.index("Vec3 flightTarget = this.flightManager.getFlightTarget();") < mover.index("if (this.dragon.horizontalCollision)")
for token in (
  "findCaveLanding(ServerLevel level, LivingEntity prey)",
  "findCaveScoutingPath(ServerLevel level, LivingEntity enemy)",
  "safeAdultGround(ServerLevel level, BlockPos floor)",
  "walkToSafeExit()",
  "lastSafeAdultPosition = dragon.position()",
  "level.canSeeSky(ground.above(4))",
  "caveLanding = findCaveLanding(level, enemy)",
  "dragon.getFlightController().patrolAttack(",
  "dragon.getFlightController().land()",
  "dragon.setSmallForm(true)",
  "route = findCaveScoutingPath(level, enemy)",
  "path = findCaveScoutingPath(level, enemy)",
  "pendingRestore = true",
  "if (pendingRestore) {",
  "walkToSafeExit();",
  "(caveLanding == null || caveRouteExhausted)",
  "compactLastProgressTick",
  "caveRouteExhausted = true",
  "compact = false;",
):
    assert token in hunt, token
assert "hunter.tick(level, owner, enemy, directSight)" in orders
assert "level.explode(" not in fire
assert "MAX_HEAT_POSITIONS = 4096" in terrain
assert "mod_version=0.7.23-ground-hunt-electric-alpha" in (root/"gradle.properties").read_text()
jar=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jar)==1, jar
with ZipFile(jar[0]) as z:
    assert z.testzip() is None
    for clazz in ("AurenRelentlessHunt","AurenFlightController","AurenAttackOrders",
                  "AurenIafDragonFlightManager"):
        assert f"br/com/auren/minecraft/entity/{clazz}.class" in z.namelist(), clazz
print("PASS: stable stationary firing, cave-mouth landing, small path and safe exit; original fire preserved")
