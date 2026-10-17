"""Auren 0.7.27: visible behavior selection and owner-only diagnostics.
Pure Java test checks utility. This checks wiring + bounded paths, not actual
visual gameplay or whether a specific player stood at the right location.
"""
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
entity=root/"src/main/java/br/com/auren/minecraft/entity"
policy=(entity/"AurenLocalDecisionPolicy.java").read_text()
idle=(entity/"AurenNaturalIdleGoal.java").read_text()
mind=(entity/"AurenLocalMind.java").read_text()
for x in (
    "APPROACH_OWNER",
    "case APPROACH_OWNER ->",
    "case REST -> 25.0D",
): assert x in policy,x
for x in (
    "case APPROACH_OWNER ->",
    "routine = startedRoutine = Routine.APPROACH_OWNER",
    "chooseInterestPath(false,",
    "dragon.getNavigation().moveTo(approach, 0.82D)",
    "dragon.getNavigation().moveTo(shortRoute, 0.68D)",
    "dragon.setNaturalRestPose(night() ? 2 : 1)",
    "for (int dy : new int[] {0, 1, -1, 2, -2})",
    "MAX_SEARCH_ATTEMPTS = 12",
    "dragon.tickCount < nextRoutineTick",
    "nextRoutineTick = dragon.tickCount + MIN_COOLDOWN_TICKS",
    "remainingPathBudget = 6;",
    "if (remainingPathBudget <= 0) return null;",
    "--remainingPathBudget;",
    "dragon.level().noCollision(dragon, space)",
    "dragon.level().getBlockState(below).isFaceSturdy(",
    "if (path != null && path.canReach())",
    "dragon.getMind().idleUnready(",
    "dragon.getMind().idlePathFailed()",
    "dragon.getMind().idleFinished(",
    "reportRouteCompletion(false);",
    "dragon.getMind().idleRouteEnded(",
    "initialOwnerDistance - dragon.distanceTo(owner)",
    "traveledDuringRoute, interrupted",
    "reportRouteCompletion(true);",
    "ownerStationary(candidate)",
    "dragon.getAttackOrders().hasOrder()",
):
    assert x in idle,x
assert "new AurenLocalDecisionPolicy" not in idle
for x in (
    "private int idleStarts",
    "private int failedIdlePaths",
    "private int stalledIdleRoutes",
    "public void idleRouteEnded(String route, double progressBlocks,",
    "private int routesWithMovement",
    '" | ultimo trajeto: " + lastRouteResult',
    "idleStatus = \"executando: \" + switch (activity)",
    "case APPROACH_OWNER -> \"aproximando do dono\"",
    "public void idleUnready(String reason)",
    "public void idlePathFailed()",
    "public void idleFinished(String summary)",
    '" | rotina: " + idleStatus',
    '" | iniciadas: " + idleStarts',
    '" | ultima interrupcao: " + lastCompleted',
    '" | limite: " + lastBlocked',
):
    assert x in mind,x
assert "mod_version=0.7.28-natural-priorities-alpha" in (
    root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for cls in ("AurenNaturalIdleGoal","AurenLocalMind",
                "AurenLocalDecisionPolicy","AurenDragon"):
        assert f"br/com/auren/minecraft/entity/{cls}.class" in jar.namelist(),cls
print("PASS: visible safe owner approach, daylight rest, finite path searches and owner diagnostics")
