"""Structural regression checks for rider-control port of IceAndFire CE 1.21.1.

The GitHub Actions Gradle build verifies mappings and binary compatibility.
These checks do NOT simulate actual mounted movement; test in Zalith.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/br/com/auren/minecraft"
dragon = (JAVA / "entity/AurenDragon.java").read_text(encoding="utf-8")
follow = (JAVA / "entity/AurenAdultFollowOwnerGoal.java").read_text(encoding="utf-8")
flight = (JAVA / "entity/AurenFlightController.java").read_text(encoding="utf-8")
client = (JAVA / "client/ClientEvents.java").read_text(encoding="utf-8")
mod = (JAVA / "AurenMinecraft.java").read_text(encoding="utf-8")
packet = (JAVA / "network/AurenDragonControlPayload.java").read_text(encoding="utf-8")
network = (JAVA / "network/AurenRiderNetwork.java").read_text(encoding="utf-8")
version = (ROOT / "gradle.properties").read_text(encoding="utf-8")

for value in (
    "getControllingPassenger()", "this.isOwnedBy(player)",
    "canAddPassenger(Entity passenger)", "!this.isSmallForm()",
    "positionRider(Entity passenger, MoveFunction callback)",
    "public void travel(Vec3 input)", "this.moveRelative(flyingSpeed",
    "this.move(MoverType.SELF, this.getDeltaMovement())",
    "this.getDeltaMovement().scale(0.9D)",
    "super.travel(new Vec3(strafe, input.y, forward))",
    "private void tickMountedRider(LivingEntity rider)",
    "this.riderTakeoffTicks > 20", "this.onGround()",
    "this.setFlightMode(AurenFlightController.TAKING_OFF)",
    "this.setFlightMode(AurenFlightController.FLYING)",
    "this.flightController.land()", "this.flightController.reset()",
    "private boolean wasPilotMounted", "this.wasPilotMounted = false",
):
    assert value in dragon, f"Missing CE mounted control behavior: {value}"

assert "&& !this.dragon.isVehicle()" in follow
assert "dragon.getControllingPassenger() != null" in flight
assert "modBus.addListener(AurenRiderNetwork::registerPayloads)" in mod
assert "registrar.playToServer(AurenDragonControlPayload.ID" in network
assert "player.getVehicle() == dragon && dragon.isOwnedBy(player)" in network
assert "payload.controlState() & 3" in network
assert "dragon.setRiderControlState(controls)" in client
assert "PacketDistributor.sendToServer(new AurenDragonControlPayload(" in client
assert "mc.options.keyJump.isDown()" in client
assert "DRAGON_DOWN.isDown()" in client
assert "GLFW.GLFW_KEY_X" in client
assert "event.register(DRAGON_DOWN)" in client
assert "record AurenDragonControlPayload(int dragonId, byte controlState)" in packet
assert "mod_version=0.6.3-alpha" in version
assert "EMERGENCY_TELEPORT_DISTANCE = 50.0D" in follow
assert "GROUND_WALK_BEFORE_FLIGHT_TICKS = 40" in follow
assert "WAYPOINT_REEVALUATE_TICKS = 60" in (
    JAVA / "entity/AurenIafDragonFlightManager.java").read_text(encoding="utf-8")
print("PASS: CE mounted control; owner-only, up/down packet, grounded follow preserved")
