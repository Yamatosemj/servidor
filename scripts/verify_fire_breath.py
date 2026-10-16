"""Compile-time companion gate for the CE FireDragon continuous-breath port.

This checks source-level provenance/invariants, not actual particle placement or
in-game balance. Minecraft 1.21.1/NeoForge compilation happens in the same CI.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/br/com/auren/minecraft"
fire = (JAVA / "entity/AurenFireBreath.java").read_text(encoding="utf-8")
dragon = (JAVA / "entity/AurenDragon.java").read_text(encoding="utf-8")
client = (JAVA / "client/ClientEvents.java").read_text(encoding="utf-8")
network = (JAVA / "network/AurenRiderNetwork.java").read_text(encoding="utf-8")
packet = (JAVA / "network/AurenDragonControlPayload.java").read_text(encoding="utf-8")
version = (ROOT / "gradle.properties").read_text(encoding="utf-8")

for expr in (
    "FireDragonEntity.riderShootFire",
    "DragonBaseEntity.rayTraceRider / performNormalBreathAttack",
    "IafDragonDestructionManager.destroyAreaBreath",
    "CE_STAGE = 3",
    "CE_WARMUP_TICKS = 20",
    "CE_BURN_PROGRESS_MAX = 40",
    "CE_REACH = 10.0D * CE_STAGE",
    "CE_IMPACT_RADIUS = 3.5D",
    "CE_FIRE_DAMAGE_SCALE = 2.0F",
    "CE_IGNITE_SECONDS = 5 + CE_STAGE * 5",
    "this.fireBreathTicks <= CE_WARMUP_TICKS",
    "this.burnProgress / (double) CE_BURN_PROGRESS_MAX",
    "rider.getEyePosition(1.0F)",
    "rider.getViewVector(1.0F)",
    "ClipContext.Block.COLLIDER",
    "ProjectileUtil.getEntityHitResult(",
    "entityHit.getLocation()",
    "BlockHitResult mouthBlock = level.clip(new ClipContext(",
    "if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK",
    "getHeadPosition()",
    "ParticleTypes.SOUL_FIRE_FLAME",
    "ParticleTypes.SMOKE",
    "level.sendParticles(",
    "level.getEntitiesOfClass(LivingEntity.class, bounds)",
    "level.damageSources().indirectMagic(dragon, rider)",
    "target.igniteForSeconds(CE_IGNITE_SECONDS)",
    "GameRules.RULE_MOBGRIEFING",
    "Blocks.SOUL_FIRE.defaultBlockState().canSurvive(level, firePos)",
):
    assert expr in fire, f"Missing FireDragon CE breath element: {expr}"

# No client-controlled position; the mounted owner or server-authored order can breathe.
assert "public boolean isRiderFiring()" in dragon
assert "(this.riderControlState & 8) != 0" in dragon
assert "this.riderControlState = (byte) (state & 15);" in dragon
assert "this.fireBreath.tick();" in dragon
assert "this.fireBreath.reset();" in dragon
assert "DRAGON_BREATH.isDown() ? 8 : 0" in client
assert "GLFW.GLFW_KEY_R" in client
assert "event.register(DRAGON_BREATH)" in client
assert "player.getVehicle() == dragon && dragon.isOwnedBy(player)" in network
assert "payload.controlState() & 15" in network
assert "byte controlState" in packet
assert "dragon.isOwnedBy(rider)" in fire
assert "dragon.isSmallForm()" in fire
assert "dragon.isRiderLightning()" in fire
assert "dragon.getControllingPassenger() instanceof Player pilot" in fire
assert "boolean riddenFire = rider != null && dragon.isOwnedBy(rider)" in fire
assert "boolean autonomousFire = !dragon.isVehicle()" in fire
assert "dragon.getAttackOrders().getFireAim()" in fire
assert "dragon.getFlightMode() == AurenFlightController.FLYING" in fire
assert "target == dragon || target == rider" in fire
assert "tame.getOwnerUUID().equals(dragon.getOwnerUUID())" in fire
assert "dragon.isAlliedTo(target)" in fire
assert "dragon.hasLineOfSight(target)" in fire
assert "level.hasChunkAt(BlockPos.containing(burnTarget))" in fire
assert "if (this.burnProgress == CE_BURN_PROGRESS_MAX && dragon.tickCount % 5 == 0)" in fire
assert "mod_version=0.7.13-smart-creature-combat-alpha" in version

# Never expose a client-selectable target vector nor copy mod-specific block
# destruction, random explosive charge or sound registries into the port.
assert "record AurenDragonControlPayload(int dragonId, byte controlState)" in packet
assert "Blocks.TNT" not in fire
assert "createExplosion" not in fire
assert "addFreshEntity" not in fire
print("PASS: CE stage-3 continuous fire breath with server-owned target and friendly-fire protection")
