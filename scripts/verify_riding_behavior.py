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
renderer = (JAVA / "client/AurenDragonRenderer.java").read_text(encoding="utf-8")

for value in (
    "getControllingPassenger()", "this.isOwnedBy(player)",
    "canAddPassenger(Entity passenger)", "!this.isSmallForm()",
    "positionRider(Entity passenger, MoveFunction callback)",
    "public void travel(Vec3 pTravelVector)", "this.moveRelative(flyingSpeed",
    "this.move(MoverType.SELF, this.getDeltaMovement())",
    "new Vec3(0.9D, 0.9D, 0.9D)",
    "super.travel(new Vec3(strafing, vertical, forward))",
    "private void tickMountedRider(LivingEntity rider)",
    "this.riderTakeoffTicks > 20", "this.onGround()",
    "this.setFlightMode(AurenFlightController.TAKING_OFF)",
    "this.setFlightMode(AurenFlightController.FLYING)",
    "this.flightController.land()", "this.flightController.reset()",
    "private boolean wasPilotMounted", "this.wasPilotMounted = false",
):
    assert value in dragon, f"Missing CE mounted control behavior: {value}"


# Mounted landing must not call autonomous reset() and erase momentum.
mounted_logic = dragon.split("private void tickMountedRider(LivingEntity rider)")[1].split(
    "public void travel(Vec3 pTravelVector)")[0]
assert "this.setFlightMode(AurenFlightController.GROUNDED)" in mounted_logic
assert "this.setNoGravity(false)" in mounted_logic
assert "this.flightController.reset()" not in mounted_logic

# CE LightningDragon 1.21.1 exact movement coefficients and state logic.
for required in (
    "CE_LIGHTNING_MIN_SPEED = 0.15F",
    "CE_LIGHTNING_MAX_SPEED = 0.4F",
    "Mth.map(speed,",
    "this.glidingSpeedBonus + this.getDeltaMovement().y * -0.05D",
    "this.glidingSpeedBonus * 0.01D",
    "speed *= 1.5F",
    "double strafing = 0.0D;",
    "forward = rider.zza > 0.0F",
    "vertical = Mth.abs(Mth.sin(this.getXRot()",
    "new Vec3(0.9D, 0.9D, 0.9D)",
    "this.riderTakeoffTicks += 2",
    "if (this.riderTakeoffTicks > 0) this.riderTakeoffTicks--",
    "if (rider.zza > 0.0F) this.setFlightMode(AurenFlightController.FLYING)",
    "else this.setFlightMode(AurenFlightController.TAKING_OFF)",
    "this.getRideHorizontalBase()",
    "this.getRideHeightBase()",
    "public void move(MoverType type, Vec3 motion)",
    "this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D)",
    "this.setNoGravity(this.getFlightMode() == AurenFlightController.TAKING_OFF",
    "this.updateRiderPitch(this.yOld - this.getY())",
):
    assert required in dragon, f"Original CE rider flight mechanic missing: {required}"

assert "speed *= 1.5F;" in dragon
assert "speed *= 1.8F" in dragon
# Avoid returning to the broken 0.6.3 always-FLYING 12-tick transition.
assert "this.riderAirTicks >= 12" not in dragon
assert "0.94D" not in dragon
assert "getDeltaMovement().scale(0.9D)" not in dragon

assert "this.previousDragonPitch = this.dragonPitch" in dragon
assert "getRiderVisualPitch(float partialTick)" in dragon
assert "RIDER_BODY_PITCH" in dragon
assert "0.78F * 3.25F, 1.20F * 3.25F" in dragon
assert "double forward = Math.max(0.0F, rider.zza)" in dragon
assert "forward = rider.zza > 0.0F" in dragon
assert "strafing *= 0.4F" not in dragon
assert "strafing *= 0.1F" not in dragon
layer = (JAVA / "client/AurenRiderFeatureRenderer.java").read_text(encoding="utf-8")
assert "DragonRiderFeatureRenderer" in layer
assert 'getCube("BodyUpper")' in layer
assert 'getCube("Neck1")' in layer
assert "RENDERING_RIDERS.add(player)" in layer
assert "RENDERING_RIDERS.remove(player)" in layer
assert "new AurenRiderFeatureRenderer(this)" in renderer
assert "RenderPlayerEvent.Pre" in client
assert "AurenRiderFeatureRenderer.RENDERING_RIDERS.contains" in client

assert "pose.mulPose(Axis.XP.rotationDegrees(entity.getRiderVisualPitch(partialTick)))" in renderer
assert "&& !this.dragon.isVehicle()" in follow
assert "dragon.getControllingPassenger() != null" in flight
assert "modBus.addListener(AurenRiderNetwork::registerPayloads)" in mod
assert "registrar.playToServer(AurenDragonControlPayload.ID" in network
assert "player.getVehicle() == dragon && dragon.isOwnedBy(player)" in network
assert "payload.controlState() & 15" in network
assert "dragon.setRiderControlState(controls)" in client
assert "PacketDistributor.sendToServer(new AurenDragonControlPayload(" in client
assert "mc.options.keyJump.isDown()" in client
assert "DRAGON_DOWN.isDown()" in client
assert "GLFW.GLFW_KEY_X" in client
assert "event.register(DRAGON_DOWN)" in client
assert "record AurenDragonControlPayload(int dragonId, byte controlState)" in packet
assert "mod_version=0.7.7-marked-assault-alpha" in version
assert "EMERGENCY_TELEPORT_DISTANCE = 50.0D" in follow
assert "GROUND_WALK_BEFORE_FLIGHT_TICKS = 40" in follow
assert "WAYPOINT_REEVALUATE_TICKS = 60" in (
    JAVA / "entity/AurenIafDragonFlightManager.java").read_text(encoding="utf-8")
print("PASS: CE mounted control; owner-only, up/down packet, grounded follow preserved")
