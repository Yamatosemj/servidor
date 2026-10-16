"""Auren 0.7.16 gates: preserve normal attacks, add staged hidden-prey
pursuit with reversible compact pathfinding and heavily gated rock siege.
Static source/class checks only: game behavior needs scenario-based playtests.
"""
from pathlib import Path
from zipfile import ZipFile

root=Path(__file__).resolve().parents[1]
entity=root/"src/main/java/br/com/auren/minecraft/entity"
hunt=(entity/"AurenRelentlessHunt.java").read_text()
orders=(entity/"AurenAttackOrders.java").read_text()
dragon=(entity/"AurenDragon.java").read_text()
fire=(entity/"AurenFireBreath.java").read_text()
terrain=(entity/"AurenScorchTerrain.java").read_text()

for token in (
    "COVER_BREATH_DELAY = 65",
    "CAVE_SEARCH_DELAY = 110",
    "SIEGE_DELAY = 260",
    "SIEGE_COOLDOWN = 95",
    "MAX_SIEGE_CHARGES = 5",
    "CHARGE_REACH = 27.0D",
    "shelteredUnderground(ServerLevel level, LivingEntity target)",
    "startCavePursuit(ServerLevel level, ServerPlayer owner,",
    "dragon.getFlightController().land()",
    "dragon.setSmallForm(true)",
    "dragon.getNavigation().createPath(enemy, 0)",
    "route == null || !route.canReach()",
    "path != null && path.canReach()",
    "dragon.getNavigation().moveTo(path, 1.17D)",
    "dragon.doHurtTarget(enemy)",
    "pendingRestore = true",
    "level.noCollision(dragon, adult)",
    "dragon.setSmallForm(false)",
    "AurenHuntTemporarySmall",
    "blockedTicks >= SIEGE_DELAY && routeChecked && noAerialRoute",
    "siegeCharges < MAX_SIEGE_CHARGES",
    "canBreachRock(level, owner, enemy, wall.getBlockPos())",
    "level.explode(dragon, strike.x, strike.y, strike.z,",
    "Level.ExplosionInteraction.MOB",
    "GameRules.RULE_MOBGRIEFING",
    "state.is(BlockTags.BASE_STONE_OVERWORLD)",
    "state.getDestroySpeed(level, pos) < 0.0F",
    "owner.distanceToSqr(Vec3.atCenterOf(impact)) < 8.0D * 8.0D",
    "level.getBlockEntity(pos) != null",
    "dragon.isAlliedTo(entity)",
):
    assert token in hunt,token

for token in (
    "new AurenRelentlessHunt(dragon)",
    "hunter.reset()",
    "hunter.tickPassive()",
    "hunter.tick(level, owner, enemy, directSight)",
    "hunter.inspectAerialRoute(hoverPoint(level, enemy) != null)",
    "fireAim = hunter.getFireAim()",
    "(dragon.isSmallForm() && !hunter.isCompact())",
    "hunter.save(tag)",
    "hunter.load(tag)",
):
    assert token in orders,token

# Existing flame-first blast only affects marked enemies, never area B.
assert "AurenRelentlessHunt" not in fire and "AurenRelentlessHunt" not in terrain
assert "level.explode(" not in fire and "level.explode(" not in terrain
assert "this.burnProgress == CE_BURN_PROGRESS_MAX" in fire
assert "MAX_HEAT_POSITIONS = 4096" in terrain
assert 'mod_version=0.7.22-organic-area-bombing-alpha' in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for cls in ("AurenRelentlessHunt","AurenAttackOrders","AurenFireBreath"):
        assert "br/com/auren/minecraft/entity/"+cls+".class" in jar.namelist()
print("PASS: escalating marked hunt; actual cave path; guarded stone-only siege; fire/area unchanged")
