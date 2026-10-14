"""Static regression checks for both follow sizes and real flight."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT/"src/main/java/br/com/auren/minecraft/entity/AurenDragon.java"
FOLLOW = ROOT/"src/main/java/br/com/auren/minecraft/entity/AurenAdultFollowOwnerGoal.java"
FLIGHT = ROOT/"src/main/java/br/com/auren/minecraft/entity/AurenFlightController.java"
LOADER = ROOT/"src/main/java/br/com/auren/minecraft/entity/AurenAdultChunkLoader.java"

def verify():
    entity=ENTITY.read_text(encoding="utf-8")
    follow=FOLLOW.read_text(encoding="utf-8")
    flight=FLIGHT.read_text(encoding="utf-8")
    loader=LOADER.read_text(encoding="utf-8")
    version=(ROOT/"gradle.properties").read_text(encoding="utf-8")
    assert "new AurenAdultFollowOwnerGoal(this, true)" in entity
    assert "new AurenAdultFollowOwnerGoal(this)" in entity
    assert "new FollowOwnerGoal" not in entity
    for expected in ("START_DISTANCE = 15.0D", "STOP_DISTANCE = 8.0D",
                     "BABY_START_DISTANCE = 4.5D", "BABY_STOP_DISTANCE = 1.7D",
                     "EMERGENCY_TELEPORT_DISTANCE = 50.0D"):
        assert expected in follow
    assert "this.dragon.getFlightController().follow(this.owner)" in follow
    assert "this.dragon.getFlightController().land();" in follow
    assert "this.dragon.getFlightController().reset();" in follow
    # Keep the real working, safety-checked 50-block teleport rather than
    # restoring the vanilla 12-block pet teleport by accident.
    for safety in ("level.hasChunkAt(candidate)",
                   "Direction.UP",
                   "level.noCollision(this.dragon, destinationBox)",
                   "level.getFluidState(candidate).isEmpty()"):
        assert safety in follow, f"Missing teleport safety: {safety}"
    for mechanic in ("dragon.setDeltaMovement(next)", "dragon.setNoGravity(true)",
                     "dragon.setFlightMode(TAKING_OFF)", "dragon.setFlightMode(FLYING)",
                     "dragon.setFlightMode(LANDING)", "level.noCollision(dragon, futureBox)",
                     "level.hasChunkAt", "TARGET_TIMEOUT_TICKS", "obstructionTicks"):
        assert mechanic in flight, f"Missing flight mechanic: {mechanic}"
    for sync in ("SynchedEntityData.defineId(AurenDragon.class, EntityDataSerializers.INT)",
                 "void setFlightMode(int mode)", "public int getFlightMode()",
                 "this.flightController.tick()", "this.flightController.reset()",
                 "this.flightController.land()", "getFlightBlend(float partialTick)"):
        assert sync in entity, f"Missing flight state sync: {sync}"
    for ticket in ("TicketType.create", "addRegionTicket", "removeRegionTicket",
                   "!dragon.isOnShoulder()", "owner.level() == level",
                   "TICKET_TIMEOUT_TICKS = 100", "TICKET_REFRESH_TICKS = 20"):
        assert ticket in loader, f"Missing ticket safety: {ticket}"
    assert "&& !dragon.isSmallForm()" not in loader
    assert "mod_version=0.6.0-alpha" in version
    print("PASS: 15/8 adult and 4.5/1.7 baby; autonomous flight; synced animation; 50+ teleport; bounded tickets")

if __name__ == "__main__":
    verify()
