"""Regression gates for topology-connected cave mouths and native A* pursuit.
Static and packaged-code verification, not a Minecraft gameplay simulation.
"""
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
entity=root/"src/main/java/br/com/auren/minecraft/entity"
planner=(entity/"AurenCaveRoutePlanner.java").read_text()
hunt=(entity/"AurenRelentlessHunt.java").read_text()
dragon=(entity/"AurenDragon.java").read_text()
fire=(entity/"AurenFireBreath.java").read_text()
for token in (
    "MAX_EXPLORED = 1600",
    "MAX_HORIZONTAL = 28",
    "MAX_VERTICAL = 10",
    "ArrayDeque<BlockPos> frontier = new ArrayDeque<>()",
    "Set<BlockPos> visited = new HashSet<>()",
    "frontier.removeFirst()",
    "frontier.addLast(next.immutable())",
    "walkable(level, next)",
    "level.canSeeSky(pos)",
    "level.hasChunkAt(position)",
    "level.getBlockState(position.below())",
    "feet.isAir()",
    "floor.getFluidState().isEmpty()",
    "pos.distSqr(origin) > 6.0D",
): assert token in planner,token
for token in (
    "caveEntrance = AurenCaveRoutePlanner.findEntrance(level, enemy)",
    "nextEntranceSearchTick = dragon.tickCount + 100",
    "if (caveEntrance == null) return false",
    "if (caveEntrance == null) return null",
    "caveEntrance.getX()",
    "compactRouteGoal = caveEntrance",
    "dragon.getNavigation().createPath(caveEntrance, 0)",
    "route = findCaveScoutingPath(level, enemy)",
    "path = findCaveScoutingPath(level, enemy)",
    "dragon.getNavigation().moveTo(route, 1.17D)",
    "dragon.getNavigation().moveTo(path, 1.17D)",
    "if (++nextNavigationCheck >= 40)",
    "if (!dragon.getNavigation().isDone() && !goalMoved && !stalled)",
    "compactLastMotionTick",
    "dragon.position().distanceToSqr(lastCompactMovement) > 1.0D",
    "dragon.tickCount - compactLastMotionTick > 100",
    "dragon.tickCount - compactStartTick > 1200",
): assert token in hunt,token
assert "for (int distance : new int[] {2, 4, 7, 10})" not in hunt
assert "if (++nextNavigationCheck >= 15)" not in hunt
assert "new AurenCaveRoutePlanner" not in fire
assert "new AdvancedPathNavigate(this, this.level()," in dragon
assert "this.navigation = this.savedGroundNavigation;" in dragon
assert "mod_version=0.7.22-organic-area-bombing-alpha" in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for clazz in ("AurenCaveRoutePlanner","AurenRelentlessHunt",
                  "AurenAttackOrders","AurenFireBreath"):
        assert f"br/com/auren/minecraft/entity/{clazz}.class" in jar.namelist(),clazz
print("PASS: cave BFS connectivity, native A* detours, no path spam and unchanged fire")
