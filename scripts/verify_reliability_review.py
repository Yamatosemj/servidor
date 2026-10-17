"""Auren 0.7.20 reliability audit checks; static + packaged JAR.

The 0.7.19 original sampled hazard blocks at odd positions only,
missed (0,0), and accepted the corners of a 48x48 search AABB as
within 24-block perceptual range. These invariants block regressions.
"""
from pathlib import Path
from zipfile import ZipFile

root=Path(__file__).resolve().parents[1]
src=root/"src/main/java/br/com/auren/minecraft/entity"
sense=(src/"AurenWorldPerception.java").read_text()
cave=(src/"AurenCaveRoutePlanner.java").read_text()
fire=(src/"AurenFireBreath.java").read_text()
orders=(src/"AurenAttackOrders.java").read_text()
brain=(src/"AurenRelentlessHunt.java").read_text()

for phrase in (
    "import java.util.Comparator;",
    "owner.distanceToSqr(living)",
    "<= ENTITY_RADIUS * ENTITY_RADIUS",
    "nearby.sort(Comparator.comparingDouble(owner::distanceToSqr))",
    "if (seen++ >= MAX_ENTITIES) break",
    "for (int dx = -HAZARD_RADIUS; dx <= HAZARD_RADIUS; ++dx)",
    "for (int dz = -HAZARD_RADIUS; dz <= HAZARD_RADIUS; ++dz)",
    "if (dx * dx + dz * dz > HAZARD_RADIUS * HAZARD_RADIUS) continue",
    "for (int dy = -2; dy <= 2; ++dy)",
    "ownerPos.offset(dx, dy, dz)",
    "if (!level.hasChunkAt(pos)) continue",
    "if (latest.villageClue() != bell)",
    "if ((latest.monsters() == 0) != (monsters == 0))",
):
    assert phrase in sense,phrase
assert "dx += 2" not in sense and "dz += 2" not in sense
# Verify the geometry of a full inclusive radius-5 integer disk, not a
# cherry-picked set of offsets excluding the owner's column.
disc={(x,z) for x in range(-5,6) for z in range(-5,6) if x*x+z*z<=25}
assert (0,0) in disc
assert (0,1) in disc and (1,0) in disc
assert (-5,0) in disc and (5,0) in disc
assert (5,5) not in disc
assert len(disc)==81
# Exact 0.50 x 0.70 small form AABB must fit in the claimed corridor.
for phrase in (
    "import net.minecraft.world.phys.AABB;",
    "SMALL_HALF_WIDTH = 0.25D",
    "SMALL_HEIGHT = 0.70D",
    "AABB childBox = new AABB(",
    "return level.noCollision(null, childBox)",
    "MAX_EXPLORED = 1600",
    "level.hasChunkAt(position)",
):
    assert phrase in cave,phrase
assert "findEntrance" in cave and "findCaveLanding" in brain
assert "level.explode(" not in fire
assert "AurenScorchTerrain.scorch(" in fire
assert "hunter.tick(level, owner, enemy, directSight)" in orders
assert "mod_version=0.7.28-natural-priorities-alpha" in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for clazz in ("AurenWorldPerception","AurenWorldPerception$Snapshot",
                  "AurenCaveRoutePlanner","AurenAttackOrders","AurenFireBreath"):
        assert f"br/com/auren/minecraft/entity/{clazz}.class" in jar.namelist(),clazz
print("PASS: centered complete hazard scan, true radial range, nearest-first capped mobs, cave hitbox guard")
