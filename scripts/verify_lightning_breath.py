"""Regression tests: single Auren, real CE Lightning Dragon continuous branch.

Checks the ported CE damage, line of sight, head position, client bolt
renderer, ownership/packet safety and preserved R fire / G lightning.
Actual visual QA and multiplayer gameplay remain separate.
"""
from pathlib import Path
from zipfile import ZipFile
import tomllib

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"src/main/java/br/com/auren/minecraft"
lightning=(JAVA/"entity/AurenLightningBreath.java").read_text()
entity=(JAVA/"entity/AurenDragon.java").read_text()
fire=(JAVA/"entity/AurenFireBreath.java").read_text()
client=(JAVA/"client/ClientEvents.java").read_text()
network=(JAVA/"network/AurenRiderNetwork.java").read_text()
renderer=(JAVA/"client/AurenDragonRenderer.java").read_text()
bolt=(JAVA/"client/lightning/LightningBoltData.java").read_text()
bolt_renderer=(JAVA/"client/lightning/LightningRenderer.java").read_text()
version=(ROOT/"gradle.properties").read_text()

for fragment in (
    "LightningDragonEntity.riderShootFire",
    "IafDragonDestructionManager.destroyAreaBreath",
    "CE_STAGE = 3",
    "CE_WARMUP_TICKS = 20",
    "CE_BURN_PROGRESS_MAX = 40",
    "CE_REACH = 10.0D * CE_STAGE",
    "CE_DAMAGE_RADIUS = 3.5D",
    "CE_LIGHTNING_DAMAGE_SCALE = 3.5F",
    "CE_LIGHTNING_KNOCKBACK = 0.3D",
    "rider.getEyePosition(1.0F)",
    "rider.getViewVector(1.0F)",
    "ClipContext.Block.COLLIDER",
    "ProjectileUtil.getEntityHitResult(",
    "this.fireBreathTicks <= CE_WARMUP_TICKS",
    "this.burnProgress / (double) CE_BURN_PROGRESS_MAX",
    "dragon.setLightningTarget(currentEnd)",
    "dragon.clearLightningTarget()",
    "dragon.tickCount % 5 == 0",
    "level.getEntitiesOfClass(LivingEntity.class, bounds)",
    "dragon.isAlliedTo(target)",
    "dragon.hasLineOfSight(target)",
    "target.knockback(CE_LIGHTNING_KNOCKBACK",
):
    assert fragment in lightning, f"Missing native CE Lightning continuous attack element: {fragment}"

# Attack is server-only, user cannot select victim coordinates, friendly
# fire is protected and no invisible CE projectiles/items are registered.
assert "dragon.level() instanceof ServerLevel level" in lightning
assert "dragon.getControllingPassenger() instanceof Player pilot" in lightning
assert "dragon.getAttackOrders().getCompactLightningAim()" in lightning
assert "if (compact && !dragon.getAttackOrders().isMarkedEnemy(target))" in lightning
assert "double area = compact ? 1.35D : CE_DAMAGE_RADIUS" in lightning
assert "dragon.isOwnedBy(pilot)" in lightning
assert "tame.getOwnerUUID().equals(dragon.getOwnerUUID())" in lightning
assert "addFreshEntity(" not in lightning
assert "spawnLightningBolt(" not in lightning
assert "createExplosion(" not in lightning
assert "level.setBlock" not in lightning
assert "level.hasChunkAt(BlockPos.containing(target))" in lightning
assert "level.hasChunkAt(BlockPos.containing(center))" in lightning

assert "private final AurenLightningBreath lightningBreath = new AurenLightningBreath(this);" in entity
assert "this.lightningBreath.tick();" in entity
assert entity.count("this.lightningBreath.reset();") >= 3
assert "RIDER_BODY_PITCH" in entity
assert "HAS_LIGHTNING_TARGET" in entity
assert all(x in entity for x in ("LIGHTNING_TARGET_X","LIGHTNING_TARGET_Y","LIGHTNING_TARGET_Z"))
assert "(this.riderControlState & 4) != 0" in entity
assert "(state & 15)" in entity
assert "this.fireBreath.tick();" in entity
assert "dragon.isRiderLightning()" in fire  # R must stop while G has priority
assert "Vec3 getHeadPosition()" in fire

assert "GLFW.GLFW_KEY_G" in client and "GLFW.GLFW_KEY_R" in client
assert "DRAGON_LIGHTNING.isDown() ? 4 : 0" in client
assert "DRAGON_BREATH.isDown() ? 8 : 0" in client
assert "event.register(DRAGON_LIGHTNING)" in client
assert "payload.controlState() & 15" in network
assert "player.getVehicle() == dragon && dragon.isOwnedBy(player)" in network

# Bolt visualization is original CE code, not a fake particle beam.
assert "class LightningRenderer" in bolt_renderer
assert "RenderType.lightning()" in bolt_renderer
assert "class LightningBoltData" in bolt
assert "LightningBoltData.BoltRenderInfo.ELECTRICITY" in renderer
assert "new LightningBoltData(" in renderer
assert ".lifespan(4)" in renderer and ".spawn(LightningBoltData.SpawnFunction.NO_DELAY)" in renderer
assert "this.lightningRenderer.render(" in renderer

assert "mod_version=0.7.24-natural-companion-alpha" in version
jar=list((ROOT/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jar)==1,jar
with ZipFile(jar[0]) as z:
    assert z.testzip() is None
    names=set(z.namelist())
    for name in (
        "br/com/auren/minecraft/entity/AurenDragon.class",
        "br/com/auren/minecraft/entity/AurenLightningBreath.class",
        "br/com/auren/minecraft/client/lightning/LightningRenderer.class",
        "br/com/auren/minecraft/client/lightning/LightningBoltData.class",
    ):
        assert name in names, name
    assert not any(s.startswith(("com/iafenvoy/iceandfire/",
                                 "assets/iceandfire/","data/iceandfire/"))
                   for s in names)
    mods=tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode())
    assert [m["modId"] for m in mods["mods"]]==["aurenminecraft"]
    assert "iceandfire" not in {d["modId"] for d in mods["dependencies"]["aurenminecraft"]}
print("PASS: original CE lightning damage/range/knockback/render, authenticated G key; R preserved, one dragon.")
