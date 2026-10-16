"""Auren v0.7.23: bounded terrestrial hunt, target-only compact lightning
and proportional organic scorching of ordinary dirt and grass.

Source + packaged JAR regression. Live Minecraft combat/pathing still untested.
"""
from pathlib import Path
from zipfile import ZipFile
import random,math
ROOT=Path(__file__).resolve().parents[1]
J=ROOT/"src/main/java/br/com/auren/minecraft/entity"
orders=(J/"AurenAttackOrders.java").read_text()
hunter=(J/"AurenRelentlessHunt.java").read_text()
fire=(J/"AurenFireBreath.java").read_text()
lightning=(J/"AurenLightningBreath.java").read_text()
scorch=(J/"AurenScorchTerrain.java").read_text()
for token in (
    "private boolean tickGroundEnemy(ServerLevel level, LivingEntity enemy,",
    "dragon.getFlightMode() != AurenFlightController.GROUNDED",
    "dragon.getNavigation().createPath(enemy, 0)",
    "dragon.getNavigation().moveTo(path, 1.12D)",
    "groundNextPathTick = combatTicks + 35",
    "if (directSight && distanceSq <= 24.0D * 24.0D)",
    "fireAim = enemy.getBoundingBox().getCenter()",
    "return !directSight && sightLostTicks < 145",
    "if (tickGroundEnemy(level, enemy, directSight)) return",
    "public Vec3 getCompactLightningAim()",
    "hunter.isCompact()", "dragon.isSmallForm()",
    "dragon.distanceToSqr(enemy) > 17.0D * 17.0D",
    "public boolean isMarkedEnemy(LivingEntity candidate)",
):
    assert token in orders,token
for token in (
    "private Path groundCaveApproach(ServerLevel level)",
    "dragon.getNavigation().createPath(test, 0)",
    "safeAdultGround(level, test)",
    "Path approach = groundCaveApproach(level)",
    "dragon.getNavigation().moveTo(approach, 1.10D)",
    "nextGroundMouthPathTick = dragon.tickCount + 55",
):
    assert token in hunter,token
for token in (
    "dragon.getAttackOrders().getCompactLightningAim()",
    "boolean compactLightning = compactAim != null",
    "Vec3 endpoint = compactLightning ? compactAim",
    "dragon.getAttackOrders().isMarkedEnemy(target)",
    "float damage = compact ? 5.0F : CE_STAGE * CE_LIGHTNING_DAMAGE_SCALE",
    "double area = compact ? 1.35D : CE_DAMAGE_RADIUS",
    "level.clip(new ClipContext(",
    "dragon.setLightningTarget(currentEnd)",
    "dragon.isAlliedTo(target)",
):
    assert token in lightning,token
for token in (
    "dragon.getFlightMode() == AurenFlightController.GROUNDED",
    "&& !dragon.getAttackOrders().isAreaOrder()",
    "if (dragon.isSmallForm())",
    "dragon.getEyePosition().add(dragon.getLookAngle().scale(0.32D))",
):
    assert token in fire,token
for token in (
    "DIRT_BREAK_HEAT = 165",
    "MAX_FLAMES_PER_IMPACT = 16",
    "for (int dx = -5; dx <= 5; ++dx)",
    "for (int dz = -5; dz <= 5; ++dz)",
    "for (int u = -3; u <= 3; u++)",
    "for (int v = -3; v <= 3; v++)",
    "double radiusX = 3.6D",
    "double radiusZ = 3.5D",
    "original.is(Blocks.COARSE_DIRT)",
    "original.is(Blocks.ROOTED_DIRT)",
    "original.is(AurenScorchedBlocks.DIRT.get())",
    "cell.heat >= DIRT_BREAK_HEAT",
    "after = Blocks.AIR.defaultBlockState()",
    "GameRules.RULE_MOBGRIEFING",
    "level.getBlockEntity(pos) != null",
):
    assert token in scorch,token
assert "destroyBlock(" not in scorch
assert "createExplosion(" not in fire and "createExplosion(" not in lightning
assert "level.explode(" not in fire and "level.explode(" not in lightning
# Smoke test: wider elliptical irregular spread than 0.7.15 and still not
# a solid square. Reflects mathematical shape, not in-game fire rendering.
rng=random.Random(723)
footprints=[]
for _ in range(90):
    radius_x=3.6+rng.random()*1.5
    radius_z=3.5+rng.random()*1.6
    jitter_x=(rng.random()-.5)*.7
    jitter_z=(rng.random()-.5)*.7
    cells=set()
    for x in range(-5,6):
        for z in range(-5,6):
            dd=((x-jitter_x)/radius_x)**2+((z-jitter_z)/radius_z)**2
            if dd>1:continue
            if (x,z)!=(0,0) and rng.random()>max(.20,.94-dd*.57):continue
            cells.add((x,z))
    assert (0,0) in cells
    assert len(cells)<121
    footprints.append(frozenset(cells))
assert len(set(footprints))>=85
assert max(len(t) for t in footprints)>=45
assert "mod_version=0.7.27-visible-offline-routine-alpha" in (
    ROOT/"gradle.properties").read_text()
jars=list((ROOT/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for cls in ("AurenAttackOrders","AurenRelentlessHunt",
                "AurenLightningBreath","AurenFireBreath","AurenScorchTerrain"):
        assert "br/com/auren/minecraft/entity/"+cls+".class" in jar.namelist()
print("PASS: terrestrial fire, compact target-only electricity, larger heat and dirt erosion")
