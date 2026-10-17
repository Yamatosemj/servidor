"""Auren v0.7.25: grounded, interruptible, day/night and weather-aware routine.

Build/source guard only; actual visual naturalness requires a world playtest.
"""
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
entity=root/"src/main/java/br/com/auren/minecraft/entity"
client=root/"src/main/java/br/com/auren/minecraft/client"
routine=(entity/"AurenNaturalIdleGoal.java").read_text()
dragon=(entity/"AurenDragon.java").read_text()
render=(client/"AurenDragonRenderer.java").read_text()
follow=(entity/"AurenAdultFollowOwnerGoal.java").read_text()
orders=(entity/"AurenAttackOrders.java").read_text()
fire=(entity/"AurenFireBreath.java").read_text()
for expected in (
  "private enum Routine { OBSERVE, EXPLORE, INVESTIGATE, REST, SEEK_SHELTER, APPROACH_OWNER }",
  "MIN_COOLDOWN_TICKS = 100",
  "MAX_COOLDOWN_TICKS = 230",
  "MAX_SEARCH_ATTEMPTS = 12",
  "private boolean night()",
  "getDayTime() % 24000L",
  "private boolean exposedRain()",
  "isRainingAt(dragon.blockPosition().above())",
  "chooseInterestPath(true)",
  "chooseInterestPath(false)",
  "dragon.level().canSeeSky(floor.above())",
  "nearby.stream().min(",
  "getEntitiesOfClass(Animal.class",
  "interestingAnimal.distanceToSqr(owner) > 100.0D",
  "dragon.getNavigation().createPath(floor, 0)",
  "dragon.getNavigation().moveTo(shortRoute, 0.68D)",
  "dragon.getNavigation().moveTo(route, 0.63D)",
  "animal.distanceToSqr(dragon) > 2.0D * 2.0D",
  "routine = startedRoutine = Routine.REST",
  "dragon.setNaturalRestPose(night() ? 2 : 1)",
  "dragon.setNaturalRestPose(0)",
  "dragon.getAttackOrders().hasOrder()",
  "dragon.getTarget() == null",
  "observed.dangerNearOwner()",
  "observed.ownerBurning()",
  "dragon.isVehicle()",
  "dragon.isOnShoulder()",
  "dragon.isOrderedToSit()",
  "ownerStationary(candidate)",
  "public void stop()",
):
    assert expected in routine, expected

for forbidden in ("markEnemy(", "markArea(", "setTarget(", "destroyBlock(",
                  "setBlock(", "level.explode(", "dragon.getFlightController().follow("):
    assert forbidden not in routine, forbidden
for expected in (
   "NATURAL_REST_POSE",
   "builder.define(NATURAL_REST_POSE, 0)",
   "void setNaturalRestPose(int pose)",
   "if (!this.level().isClientSide() && pose >= 0 && pose <= 2",
   "public float getNaturalRestBlend(float partialTick)",
   "public int getRestVisualPose()",
   "previousNaturalRestProgress",
   "getBreathAttackTicks() == 0",
   "getBiteAttackTicks() == 0",
   "new AurenNaturalIdleGoal(this)",
):
    assert expected in dragon, expected
for expected in (
    "lightningdragon/lightningdragon_sitting",
    "lightningdragon/lightningdragon_sleeping",
    "dragon.getNaturalRestBlend(partialTick)",
    "dragon.getRestVisualPose() == 2",
    "AurenPoseTransition.apply(model,",
    "!dragon.isVehicle()",
):
    assert expected in render,expected
assert "new WaterAvoidingRandomStrollGoal(this)" not in dragon
assert "this.iafFlightManager" not in routine
assert "getNavigation().createPath(this.owner, 0)" in follow
assert "findDistributedAreaImpact(" in orders
assert "AurenScorchTerrain.scorchFace(" in fire
assert "mod_version=0.7.30-bonded-head-alpha" in (
    root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for name in ("AurenNaturalIdleGoal","AurenDragon","AurenAdultFollowOwnerGoal"):
        assert f"br/com/auren/minecraft/entity/{name}.class" in jar.namelist()
    for pose in ("sitting","sleeping"):
        assert ("assets/aurenminecraft/models/tabula/lightningdragon/"
                f"lightningdragon_{pose}.tbl") in jar.namelist()
print("PASS: bounded daily routines, real CE rest poses, owner-safe interrupts and unchanged combat")
