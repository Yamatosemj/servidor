"""Auren v0.7.24: calm grounded companion and owner-relative walking.

Structural + packaged JAR gate. Real game testing required for animation
quality, obstacle handling and CE landing timings.
"""
from pathlib import Path
from zipfile import ZipFile
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"src/main/java/br/com/auren/minecraft/entity"
follow=(BASE/"AurenAdultFollowOwnerGoal.java").read_text()
idle=(BASE/"AurenNaturalIdleGoal.java").read_text()
dragon=(BASE/"AurenDragon.java").read_text()
flight=(BASE/"AurenFlightController.java").read_text()
ground=(BASE/"AurenCeGroundMoveControl.java").read_text()
for token in (
    "REPATH_INTERVAL_TICKS = 25",
    "GROUND_WALK_BEFORE_FLIGHT_TICKS = 120",
    "GROUND_STALL_TICKS = 80",
    "MOTION_SAMPLE_TICKS = 20",
    "OWNER_REPATH_SQ = 3.5D * 3.5D",
    "getNavigation().createPath(this.owner, 0)",
    "candidate != null && candidate.canReach()",
    "getNavigation().moveTo(candidate, WALK_SPEED)",
    "boolean movedOwner = this.lastPathOwnerPosition == null",
    "boolean routeDone = this.dragon.getNavigation().isDone()",
    "distanceToSqr(this.previousGroundPosition) >= MOTION_SAMPLE_SQ",
    "boolean openSky =",
    "canSeeSky(from)",
    "canSeeSky(to)",
    "if (openSky && (blocked || longPursuit))",
    "EMERGENCY_TELEPORT_DISTANCE = 50.0D",
    "level.noCollision(this.dragon, destinationBox)",
    "START_DISTANCE = 15.0D",
    "BABY_START_DISTANCE = 4.5D",
):
    assert token in follow,token
assert "previousOwnerDistance" not in follow
assert "getNavigation().moveTo(this.owner, WALK_SPEED)" not in follow
for token in (
    "class AurenNaturalIdleGoal extends Goal",
    "MIN_REST_TICKS = 65",
    "MAX_REST_TICKS = 135",
    "MIN_COOLDOWN_TICKS = 100",
    "MAX_COOLDOWN_TICKS = 230",
    "dragon.getAttackOrders().hasOrder()",
    "dragon.getTarget() == null",
    "dragon.getFlightMode() == AurenFlightController.GROUNDED",
    "dragon.onGround()",
    "dragon.isOrderedToSit()",
    "dragon.isOnShoulder()",
    "dragon.isVehicle()",
    "ownerStationary(candidate)",
    "dragon.level().hasChunkAt(floor)",
    "dragon.level().noCollision(dragon, space)",
    "dragon.getNavigation().createPath(floor, 0)",
    "dragon.getNavigation().moveTo(shortRoute, 0.68D)",
    "dragon.getLookControl().setLookAt(owner, 7.0F, 7.0F)",
    "dragon.getNavigation().stop()",
):
    assert token in idle,token
assert "new AurenNaturalIdleGoal(this)" in dragon
assert "new WaterAvoidingRandomStrollGoal" not in dragon
assert "new AurenAdultFollowOwnerGoal(this, true)" in dragon
assert "new AurenAdultFollowOwnerGoal(this)" in dragon
assert "this.iafFlightManager.update(this.owner)" in flight
assert "class AurenCeGroundMoveControl extends MoveControl" in ground
assert "mod_version=0.7.30-bonded-head-alpha" in (ROOT/"gradle.properties").read_text()
jars=list((ROOT/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for name in ("AurenNaturalIdleGoal","AurenAdultFollowOwnerGoal","AurenDragon"):
        assert f"br/com/auren/minecraft/entity/{name}.class" in jar.namelist()
print("PASS: genuine ground detours, flight fallback with sky clearance and quiet bounded curiosity")
