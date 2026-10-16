"""v0.7.21 fire safety / reactive defence / true face-burn regression.

Source+JAR tests. The code must still be tested in actual Minecraft:
no static check can guarantee the flight/cave route is achievable.
"""
from pathlib import Path
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[1]
java=ROOT/"src/main/java/br/com/auren/minecraft"
main=(java/"AurenMinecraft.java").read_text()
dragon=(java/"entity/AurenDragon.java").read_text()
fire=(java/"entity/AurenFireBreath.java").read_text()
scorch=(java/"entity/AurenScorchTerrain.java").read_text()
hunt=(java/"entity/AurenRelentlessHunt.java").read_text()
sensor=(java/"entity/AurenWorldPerception.java").read_text()
assert ".fireImmune()" in main
for check in (
    "new HurtByTargetGoal(this)",
    "AurenDragon.this.getLastHurtByMob()",
    "getAttackOrders().canMarkTarget(attacker)",
    "public boolean hurt(DamageSource source, float damage)",
    "boolean tookDamage = super.hurt(source, damage)",
    "source.getEntity() instanceof LivingEntity attacker",
    "this.getAttackOrders().markEnemy(attacker)",
    "&& !this.getAttackOrders().hasOrder()",
):
    assert check in dragon,check
assert "new OwnerHurtByTargetGoal(this)" in dragon
assert "new OwnerHurtTargetGoal(this)" in dragon
assert "this.isSmallForm() ? 3.0F : 7.0F" in dragon
for check in (
    "GRASS_BREAK_HEAT = 115",
    "SCORCH_HEAT = 28",
    "MAGMA_HEAT = 135",
    "MAGMA_BREAK_HEAT = 330",
    "public static void scorchFace(ServerLevel level, BlockPos impact,",
    "Direction face, AurenDragon dragon,",
    "switch (face.getAxis())",
    "BlockPos exposed = surface.relative(face)",
    "if (!eligible(state)) continue;",
    "level.getBlockEntity(surface) != null",
    "withinArea(surface, markedCenter, radius)",
    "applyHeat(level, surface, tracker, now, gain)",
    "level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME",
    "front.is(Blocks.FIRE)",
    "level.isEmptyBlock(exposed)",
    "Blocks.FIRE.defaultBlockState().canSurvive(level, exposed)",
):
    assert check in scorch,check
assert "destroyBlock(" not in scorch
assert "level.explode(" not in scorch
for check in (
    "boolean onWall = blockHit != null",
    "blockHit.getDirection() != net.minecraft.core.Direction.UP",
    "AurenScorchTerrain.scorchFace(level, scorchCenter,",
    "AurenScorchTerrain.scorch(level, scorchCenter, dragon,",
    "previousGroundImpact = onWall ? null : thisGroundImpact",
    "!onWall && previousGroundImpact != null",
):
    assert check in fire,check
assert "level.explode(" not in fire
for check in (
    "findMouthVantage(ServerLevel level)",
    "pressureCaveMouth(ServerLevel level, ServerPlayer owner)",
    "caveMouthVantage = findMouthVantage(level)",
    "if (caveLanding == null) return pressureCaveMouth(level, owner)",
    "fireAim = mouth",
    "dragon.getFlightController().holdFacing(owner, mouth)",
    "inaccessibleMouthTicks < 200",
    "caveRouteExhausted = true",
    "canBreachRock(level, owner, enemy, wall.getBlockPos())",
):
    assert check in hunt,check
assert "level.explode(" in hunt
for check in (
    "boolean ownerBurning",
    "latest.ownerBurning()",
    "owner.isOnFire()",
    "Chamas detectadas na região.",
    "Dono está pegando fogo.",
):
    assert check in sensor,check
assert "latest.fireNearby() || latest.lavaNearby()" not in sensor
assert 'mod_version=0.7.21-defense-flame-hunt-alpha' in (ROOT/"gradle.properties").read_text()
jars=list((ROOT/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for cls in ("AurenDragon","AurenFireBreath","AurenScorchTerrain",
                "AurenRelentlessHunt","AurenWorldPerception$Snapshot"):
        assert f"br/com/auren/minecraft/entity/{cls}.class" in jar.namelist(),cls
print("PASS: reactive owner-safe dragon, fire immunity, faster grass, face heating, cave mouth focus")
