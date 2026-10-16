"""Regression safety gate for the DIRECT IceAndFire Community Edition flight port.

Tests confirm that Auren keeps the original CE movement algorithms and
existing owner/teleport/chunk ticket rules. GitHub CI also compiles the mod.
They do NOT claim to simulate flying inside the game; Zalith still required.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenDragon.java"
FOLLOW = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenAdultFollowOwnerGoal.java"
FLIGHT = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenFlightController.java"
CE = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenIafDragonFlightManager.java"
LOADER = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenAdultChunkLoader.java"

def verify():
    entity = ENTITY.read_text(encoding="utf-8")
    follow = FOLLOW.read_text(encoding="utf-8")
    flight = FLIGHT.read_text(encoding="utf-8")
    ce = CE.read_text(encoding="utf-8")
    loader = LOADER.read_text(encoding="utf-8")
    version = (ROOT / "gradle.properties").read_text(encoding="utf-8")

    # No more re-created Auren acceleration/steering algorithm.
    for expected in (
        "class FlightMoveHelper extends MoveControl",
        "class AurenIafDragonFlightManager",
        "float approach(float number, float max, float min)",
        "float approachDegrees(float number, float max, float min)",
        "float degreesDifferenceAbs(float f1, float f2)",
        "this.dragon.horizontalCollision",
        "this.flightManager.invalidateTarget();",
        "float yawTurn = Mth.wrapDegrees(this.dragon.getYRot() + 90);",
        "approachDegrees(",
        "this.speedModifier, 1.8F",
        "this.speedModifier, 0.2F",
        "float finPitch =",
        "motionCap = 0.2D",
        "ClipContext.Block.COLLIDER",
        "getBlockInViewEscort(LivingEntity owner)",
        "Heightmap.Types.MOTION_BLOCKING_NO_LEAVES",
        "// Auren's flight uses noGravity=true",
        "WAYPOINT_REEVALUATE_TICKS = 60",
        "this.lastOwnerPos.distanceToSqr(owner.position()) > 64.0D",
        "int baseHeight = dragon.isSmallForm() ? 2 : 4;",
        "int heightRange = dragon.isSmallForm() ? 2 : 3;",
    ):
        assert expected in ce, f"Missing original CE flight mechanic: {expected}"

    for expected in (
        "new AurenIafDragonFlightManager(dragon)",
        "new AurenIafDragonFlightManager.FlightMoveHelper(",
        "this.iafFlightManager.update(this.owner)",
        "dragon.useIceAndFireFlightNavigation(",
        "dragon.useOriginalGroundNavigation();",
        "dragon.setFlightMode(TAKING_OFF)",
        "dragon.setFlightMode(FLYING)",
        "dragon.setFlightMode(LANDING)",
        "dragon.setNoGravity(true)",
        "dragon.setNoGravity(false)",
        "TARGET_TIMEOUT_TICKS = 20",
        "MAX_OBSTRUCTION_TICKS = 65",
    ):
        assert expected in flight, f"Flight lifecycle missing: {expected}"

    # Real CE/Uranus nav switch; ensure stuck handler doesn't quietly force
    # the old rapid pet teleport. Restores ground navigation after flight.
    for expected in (
        "new AdvancedPathNavigate(this, this.level()",
        "AdvancedPathNavigate.MovementType.FLYING",
        "PathingStuckHandler.createStuckHandler()",
        "this.navigation = this.savedGroundNavigation;",
        "this.moveControl = this.savedGroundMoveControl;",
        "SynchedEntityData.defineId(AurenDragon.class, EntityDataSerializers.INT)",
        "this.flightController.tick()",
        "this.flightController.reset()",
    ):
        assert expected in entity, f"CE navigation/state sync missing: {expected}"

    # Auren cannot borrow the CE +0.1 Y/tick boost because it has no gravity.
    assert "dragon.getDeltaMovement().add(0, 0.1D, 0)" not in ce
    assert "double desiredVerticalSpeed = Mth.clamp(" in flight
    assert "double stabilizedY = Mth.lerp(" in flight
    assert "Mth.clamp(stabilizedY, -0.20D, 0.22D)" in flight
    assert "dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.85D))" in flight

    # Baby/adult distances retained, including the 50+ emergency teleport.
    assert "new AurenAdultFollowOwnerGoal(this, true)" in entity
    assert "new AurenAdultFollowOwnerGoal(this)" in entity
    assert "new FollowOwnerGoal" not in entity
    assert "this.dragon.getNavigation().stop()" in follow
    assert "private final PathNavigation navigation" not in follow
    # Walk first, and only switch to the CE flight mover after repeated
    # ground path failure/stalling or after 40 ticks of a long chase.
    for expected in (
        "GROUND_WALK_BEFORE_FLIGHT_TICKS = 40",
        "GROUND_STALL_TICKS = 30",
        "ADULT_LONG_CHASE_DISTANCE = 32.0D",
        "BABY_LONG_CHASE_DISTANCE = 14.0D",
        "getNavigation().moveTo(this.owner, WALK_SPEED)",
        "this.stalledGroundTicks >= GROUND_STALL_TICKS",
        "this.groundPursuitTicks >= GROUND_WALK_BEFORE_FLIGHT_TICKS",
        "if (blocked || longPursuit)",
        "if (flightMode != AurenFlightController.GROUNDED) return;",
        "START_DISTANCE = 15.0D",
        "STOP_DISTANCE = 8.0D",
        "BABY_START_DISTANCE = 4.5D",
        "BABY_STOP_DISTANCE = 1.7D",
        "EMERGENCY_TELEPORT_DISTANCE = 50.0D",
        "this.dragon.getFlightController().follow(this.owner)",
        "this.dragon.getFlightController().land()",
        "this.dragon.getFlightController().reset()",
        "level.hasChunkAt(candidate)",
        "Direction.UP",
        "level.noCollision(this.dragon, destinationBox)",
        "level.getFluidState(candidate).isEmpty()",
    ):
        assert expected in follow, f"Follow/teleport regression: {expected}"

    for expected in (
        "TicketType.create",
        "addRegionTicket",
        "removeRegionTicket",
        "!dragon.isOnShoulder()",
        "owner.level() == level",
        "TICKET_TIMEOUT_TICKS = 100",
        "TICKET_REFRESH_TICKS = 20",
    ):
        assert expected in loader, f"Chunk ticket regression: {expected}"
    assert "&& !dragon.isSmallForm()" not in loader
    assert "mod_version=0.7.18-cave-route-navigation-alpha" in version
    print("PASS: ground-first CE follow; bounded vertical speed; 15/8 adult, 4.5/1.7 baby; safe 50+ teleport; bounded tickets")

if __name__ == "__main__":
    verify()
