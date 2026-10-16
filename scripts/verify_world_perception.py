"""0.7.19 server perception: source safety, owner authorization, HUD and jar."""
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
src=root/"src/main/java/br/com/auren/minecraft"
sensor=(src/"entity/AurenWorldPerception.java").read_text()
dragon=(src/"entity/AurenDragon.java").read_text()
commands=(src/"AurenCommands.java").read_text()
client=(src/"client/ClientEvents.java").read_text()
hud=(src/"client/AurenAttackOverlay.java").read_text()
for token in (
 "record Snapshot(long tick, String biome, String environment,",
 "SCAN_INTERVAL = 40", "ENTITY_RADIUS = 24", "MAX_ENTITIES = 96",
 "MAX_HISTORY = 8", "HAZARD_RADIUS = 5",
 "getEntitiesOfClass(LivingEntity.class", "entity instanceof Monster",
 "entity instanceof Animal", "entity instanceof Player",
 "owner.getHealth() <= owner.getMaxHealth() * 0.35F",
 "level.getBiome(ownerPos).unwrapKey()", "level.canSeeSky(ownerPos.above())",
 "level.isRainingAt(ownerPos)", "level.isThundering()",
 "level.getDayTime() % 24000L", "state.is(Blocks.LAVA)",
 "state.is(Blocks.FIRE)", "state.is(Blocks.BELL)",
 "level.hasChunkAt(pos)", "recentChanges.removeFirst()",
 "recentChanges.addLast(event)", "latest = observed",
 "dragon.isOwnedBy(requester)",
 "public void refresh(ServerLevel level, ServerPlayer requester)",
): assert token in sensor,token
for token in (
 "private final AurenWorldPerception awareness = new AurenWorldPerception(this);",
 "builder.define(WORLD_PERCEPTION,", "builder.define(WORLD_ALERT, 0)",
 "public AurenWorldPerception getAwareness()",
 "public String getSyncedPerceptionBrief()", "public int getSyncedPerceptionAlert()",
 "this.awareness.tick(perceptionLevel)", "this.syncPerception()",
): assert token in dragon,token
for token in (
 'Commands.literal("ambiente")', 'Commands.literal("perceber")',
 'Commands.literal("lembrar")', "dragon.getAwareness().refresh(level, player)",
 "dragon.getAwareness().describe()", "dragon.getAwareness().recentEvents()",
 "AurenDragon dragon = getDragon(context)",
): assert token in commands,token
for token in ("GLFW.GLFW_KEY_H","event.register(PERCEPTION)",
 "while (PERCEPTION.consumeClick())","revealPerceptionForTicks(mc)"):
    assert token in client,token
for token in ("PERCEPTION_TICKS = 140", "perceptionActive(Minecraft mc)",
 "perceptionUntil = Long.MIN_VALUE", "dragon.getSyncedPerceptionBrief()",
 "dragon.getSyncedPerceptionAlert()",
 "mode == 0 && !transientHint && !perceptionHint"):
    assert token in hud,token
for forbidden in ("setTarget(", "markEnemy(", "markArea(", "destroyBlock(",
                  "level.explode(", "setBlock(", "getFlightController()."):
    assert forbidden not in sensor,forbidden
assert "mod_version=0.7.19-environment-perception-alpha" in (root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
 assert jar.testzip() is None
 for cls in ("AurenWorldPerception","AurenWorldPerception$Snapshot","AurenDragon"):
    assert f"br/com/auren/minecraft/entity/{cls}.class" in jar.namelist(),cls
print("PASS: bounded server perception, opt-in HUD, owner-only command and no combat intervention")
