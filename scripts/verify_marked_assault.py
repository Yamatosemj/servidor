"""v0.7.7 safety/integration gate: server-authored marking and CE orbital flight.

Tests actual packaged bytecode presence and key source invariants. Flight
quality, targeting and attack cadence still require gameplay QA in Minecraft.
"""
from pathlib import Path
from zipfile import ZipFile
import math

ROOT = Path(__file__).resolve().parents[1]
J = ROOT / "src/main/java/br/com/auren/minecraft"
commands = (J / "AurenCommands.java").read_text()
orders = (J / "entity/AurenAttackOrders.java").read_text()
flight = (J / "entity/AurenFlightController.java").read_text()
manager = (J / "entity/AurenIafDragonFlightManager.java").read_text()
fire = (J / "entity/AurenFireBreath.java").read_text()
terrain = (J / "entity/AurenScorchTerrain.java").read_text()
dragon = (J / "entity/AurenDragon.java").read_text()
follow = (J / "entity/AurenAdultFollowOwnerGoal.java").read_text()
version = (ROOT / "gradle.properties").read_text()

for command in ['Commands.literal("atacar")', 'Commands.literal("inimigo")',
                'Commands.literal("area")', 'Commands.literal("parar")',
                'Commands.literal("status")']:
    assert command in commands, command
assert "for (int radius = 5; radius <= 30; radius += 5)" in commands
assert "selectedRadius % 5 != 0" in orders
assert "Commands.literal(Integer.toString(selected))" in commands
assert "ClipContext.Block.COLLIDER" in commands
assert "ProjectileUtil.getEntityHitResult(" in commands
assert "entity instanceof Enemy" in commands
assert "dragon.isTame() && dragon.isOwnedBy(player)" in commands
assert "player.getViewVector(1.0F)" in commands
assert "MARK_DISTANCE" not in orders or "MAX_MARK_DISTANCE" in orders

for phrase in ["public boolean markArea(", "public boolean markEnemy(",
               "public void clear()", "public void save(", "public void load(",
               'tag.putInt("AurenAttackMode", mode)',
               'tag.putUUID("AurenMarkedEnemy", markedEnemy)',
               'tag.putInt("AurenAttackRadius", radius)',
               'tag.getInt("AurenAttackRadius")', "this.fireAim = null;",
               "level.getEntity(markedEnemy)", "level.hasChunkAt(",
               "dragon.getFlightController().patrol(owner, waypoint)",
               "combatTicks / 120", "combatTicks / 60",
               "double altitude = mode == AREA ? 9.0D : 11.0D",
               "MAX_MARK_DISTANCE * MAX_MARK_DISTANCE"]:
    assert phrase in orders, phrase
assert "new AurenAttackOrders(this)" in dragon
assert "this.attackOrders.tick();" in dragon
assert "this.attackOrders.save(tag);" in dragon
assert "this.attackOrders.load(tag);" in dragon
assert dragon.count("!AurenDragon.this.getAttackOrders().hasOrder()") == 2
assert "&& !this.dragon.getAttackOrders().hasOrder()" in follow
assert "if (!this.dragon.getAttackOrders().hasOrder())" in follow

# Existing CE mover remains authoritative; no new velocity-steering replica.
assert "new AurenIafDragonFlightManager.FlightMoveHelper(" in flight
assert "public boolean patrol(LivingEntity targetOwner, Vec3 waypoint)" in flight
assert "this.iafFlightManager.setFlightTarget(this.patrolPoint)" in flight
assert "this.iafFlightManager.update(this.owner)" in flight
assert "4.0F" in manager
assert "Mth.clamp(stabilizedY, -0.20D, 0.22D)" in flight

# Fire stays in the original server-only CE attack pipeline.
for phrase in ("boolean autonomousFire =", "boolean riddenFire =",
               "dragon.getAttackOrders().getFireAim()",
               "eye.distanceToSqr(endpoint) > CE_REACH * CE_REACH",
               "dragon.getFlightMode() == AurenFlightController.FLYING",
               "withinArea(target.position())",
               "AurenScorchTerrain.scorch(level, scorchCenter, dragon,",
               "dragon.isAlliedTo(target)", "GameRules.RULE_MOBGRIEFING"):
    assert phrase in fire, phrase
# Both the ray-hit face and the adjacent soul-fire placement must stay inside
# the ordered circle; a boundary hit can otherwise create fire one block out.
assert "withinArea(blockHit.getLocation())" in fire
assert "withinArea(Vec3.atCenterOf(firePos))" in fire
assert 4.5**2 <= 5**2 and not (5.5**2 <= 5**2)
assert "BlockPos markedCenter, int radius" in terrain
assert "dx * dx + dz * dz > radius * radius" in terrain
assert "mod_version=0.7.8-key-preview-alpha" in version

# Pure numerical radius and sweep sanity (no engine/AI claim).
for r in range(5, 31, 5):
    for theta in (0.0, math.pi/4, math.pi/2, math.pi, 3*math.pi/2):
        x, z = math.cos(theta) * r, math.sin(theta) * r
        assert abs(math.hypot(x, z) - r) < 1e-10
        for coverage in (0.0, 0.45, 0.80):
            assert math.hypot(coverage*x, coverage*z) <= r

jars = list((ROOT / "build/libs").glob("aurenminecraft-*.jar"))
assert len(jars) == 1, jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for cls in ("AurenAttackOrders", "AurenFlightController", "AurenFireBreath"):
        assert "br/com/auren/minecraft/entity/" + cls + ".class" in jar.namelist()
    assert "br/com/auren/minecraft/AurenCommands.class" in jar.namelist()

print("PASS: commands, 6 strict radii, owner marking, explicit stop, persisted order, CE mover, bounded area fire")
