"""v0.7.8: safety and packaged preview regression tripwires (not gameplay QA)."""
from pathlib import Path
from zipfile import ZipFile
root = Path(__file__).resolve().parents[1]
src = root / "src/main/java/br/com/auren/minecraft"
read = lambda p: (src / p).read_text()
client, overlay = read("client/ClientEvents.java"), read("client/AurenAttackOverlay.java")
client_mod = read("client/ClientModEvents.java")
server, payload = read("AurenCommands.java"), read("network/AurenMarkPayload.java")
network, dragon = read("network/AurenRiderNetwork.java"), read("entity/AurenDragon.java")
orders = read("entity/AurenAttackOrders.java")
for string in ('GLFW.GLFW_KEY_V', 'event.register(MARK_TARGET)', 'MARK_TARGET.consumeClick()',
               'InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)',
               'InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)', 'new AurenMarkPayload(0, true)',
               'new AurenMarkPayload(AurenAttackOverlay.selectedRadius(), false)',
               'mc.screen == null', 'RenderGuiEvent.Post', 'RenderLevelStageEvent'):
    assert string in client, string
assert 'bus = EventBusSubscriber.Bus.GAME' in client
assert 'bus = EventBusSubscriber.Bus.MOD' in client_mod
assert 'ClientEvents.registerKeyMappings(event)' in client_mod
assert 'ClientEvents.registerRenderers(event)' in client_mod
assert '@SubscribeEvent\n    public static void registerKeyMappings' not in client
assert '@SubscribeEvent\n    public static void registerRenderers' not in client
assert 'record AurenMarkPayload(int radius, boolean clear)' in payload
assert not any(x in payload for x in ('BlockPos', 'Vec3', 'entityId', 'dragonId'))
assert 'registrar.playToServer(AurenMarkPayload.ID' in network
assert 'context.player() instanceof ServerPlayer' in network
for string in ('handleMarkKey(ServerPlayer player, int radius, boolean clear)',
               'ownedDragon(player)', 'radius < 5 || radius > 30 || radius % 5 != 0',
               'player.getViewVector(1.0F).scale(AIM_REACH)',
               'ProjectileUtil.getEntityHitResult(', 'ClipContext.Block.COLLIDER',
               'dragon.getAttackOrders().markEnemy(mob)',
               'dragon.getAttackOrders().markArea(block.getBlockPos(), radius)'):
    assert string in server, string
for string in ('MARK_MODE', 'MARK_X', 'MARK_Y', 'MARK_Z', 'MARK_RADIUS',
               'MARK_ENEMY_ID', 'this.syncAttackMark()', 'getSyncedMarkMode()',
               'getSyncedMarkCenter()', 'getSyncedMarkedEnemyId()', 'getSyncedMarkRadius()'):
    assert string in dragon, string
for string in ('RenderLevelStageEvent.Stage.AFTER_PARTICLES', 'RenderType.lines()',
               'LevelRenderer.renderLineBox(', 'private static void ring(',
               'if (!previewActive(mc)) return;', 'selectedRadius >= 30 ? 5 : selectedRadius + 5',
               'dragon.getSyncedMarkMode()', 'Preview aiming = preview(mc, dragon)'):
    assert string in overlay, string
assert '.setNormal(nx, ny, nz)' in overlay  # True segment direction for line shader
assert 'TOGGLE_BOMBARDMENT.consumeClick()' in client
assert 'new AurenMarkPayload(0, false)' in client
assert 'isSyncedAreaAttackEnabled()' in overlay
assert 'graphics.fill(' not in overlay
assert 'PREVIEW_TICKS = 80' in overlay
assert 'mc.level == previewLevel' in overlay
assert 'living instanceof TamableAnimal pet' in overlay
assert 'dragon.getOwnerUUID().equals(pet.getOwnerUUID())' in overlay
assert 'isAreaEnabled()' in orders
assert 'getMarkedEnemyEntityId()' in orders
assert 'DRAGON_BREATH.isDown()' in client and 'DRAGON_LIGHTNING.isDown()' in client
assert 'Commands.literal("atacar")' in server and 'Commands.literal("parar")' in server
assert 'mod_version=0.7.11-sustained-fire-alpha' in (root / 'gradle.properties').read_text()
jars = list((root / 'build/libs').glob('aurenminecraft-*.jar'))
assert len(jars) == 1, jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for name in ('br/com/auren/minecraft/client/AurenAttackOverlay.class',
                 'br/com/auren/minecraft/client/ClientModEvents.class',
                 'br/com/auren/minecraft/network/AurenMarkPayload.class',
                 'assets/aurenminecraft/lang/pt_br.json'):
        assert name in jar.namelist(), name
print('PASS: secure V/Shift+V marked attack, synced preview, HUD and assets')
