"""Targeted regression checks for the 0.7.14 combat audit.

Source guards deliberately isolate marked combat from previously accepted fire,
rider controls and area bombing. Runtime pathfinding must still be playtested.
"""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
src = root / "src/main/java/br/com/auren/minecraft"
dragon = (src / "entity/AurenDragon.java").read_text()
orders = (src / "entity/AurenAttackOrders.java").read_text()
commands = (src / "AurenCommands.java").read_text()
fire = (src / "entity/AurenFireBreath.java").read_text()
terrain = (src / "entity/AurenScorchTerrain.java").read_text()
flight = (src / "entity/AurenFlightController.java").read_text()

# All four vanilla MOVE goal guards: melee twice + stroll twice.
assert dragon.count('!AurenDragon.this.getAttackOrders().hasOrder()') == 4
assert "new WaterAvoidingRandomStrollGoal" in dragon

# Never send an unsafe tackle destination directly to the CE mover.
assert "safeTacklePoint(level, enemy, desired)" in orders
assert "if (next == null || !canFlyTo(level, next)" in orders
assert "|| !clearFlightCorridor(level, next)" in orders
assert "if (tactic == AirAttack.TACKLE && next != null" not in orders
assert "dragon.getBoundingBox().inflate(0.65D)" in orders
assert "landedHit = dragon.doHurtTarget(enemy)" in orders

# Retrying recovery has a bounded cadence and a final safe landing.
assert "recoveryPoint(level, enemy)" in orders
assert "nextRecoveryTick = combatTicks + 25" in orders
assert "recoveryFailureTicks >= 80" in orders
assert "clearFlightCorridor(level, waypoint)" in orders

# A successful hit changes the next tactic and reach is checked at mark time.
assert "lastTackleConnected = true" in orders
assert "lastTackleConnected && distanceSq <= 28.0D * 28.0D" in orders
assert "dragon.distanceToSqr(enemy) > MAX_MARK_DISTANCE * MAX_MARK_DISTANCE" in orders
assert "dragon.distanceToSqr(Vec3.atCenterOf(center))" in orders
assert "64 blocos do Auren" in commands

# No regression to the 0.7.12-approved fire, bombing, or original flight.
assert "canAimForward(" not in fire
assert "AurenScorchTerrain.bombard(" not in fire
assert "destroyBlock(" not in terrain
assert "Blocks.FIRE.defaultBlockState()" in terrain
assert "this.iafFlightManager.setFlightTarget(this.patrolPoint)" in flight
assert "areaFiringPass = areaPhase == 2" in orders

assert "mod_version=0.7.14-combat-reliability-alpha" in (root/"gradle.properties").read_text()
jars = list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars) == 1, jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for name in ("AurenDragon", "AurenAttackOrders", "AurenFireBreath"):
        assert f"br/com/auren/minecraft/entity/{name}.class" in jar.namelist()
print("PASS: 0.7.14 combat audit fixes, safe aim and preserved 0.7.12 fire")
