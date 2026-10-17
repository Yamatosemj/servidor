"""Auren 0.7.29 unified menu and safe world targeting regression gate.
This verifies source wiring and packaged bytecode, not visual gameplay.
"""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
java = root / "src/main/java/br/com/auren/minecraft"
read = lambda p: (java / p).read_text()
client = read("client/ClientEvents.java")
menu = read("client/AurenMenuScreen.java")
overlay = read("client/AurenAttackOverlay.java")
commands = read("AurenCommands.java")
network = read("network/AurenRiderNetwork.java")
packet = read("network/AurenMenuActionPayload.java")
legacy = read("network/AurenMarkPayload.java")
dragon = read("entity/AurenDragon.java")
modbus = read("client/ClientModEvents.java")

for token in (
    "GLFW.GLFW_KEY_V", "event.register(MARK_TARGET)",
    "while (MARK_TARGET.consumeClick())", "mc.setScreen(new AurenMenuScreen())",
    "ClientEvents.isTargeting()", "InputEvent.InteractionKeyMappingTriggered",
    "event.isUseItem()", "event.setCanceled(true)",
    "event.getHand() == InteractionHand.OFF_HAND && suppressOffhand",
    "new AurenMenuActionPayload(", "endSelection();",
    "DRAGON_BREATH.isDown()", "DRAGON_LIGHTNING.isDown()",
    "RenderGuiEvent.Post", "RenderLevelStageEvent"):
    assert token in client, token
assert "TOGGLE_BOMBARDMENT.consumeClick()" not in client
assert "PERCEPTION.consumeClick()" not in client
assert "bus = EventBusSubscriber.Bus.GAME" in client
assert "bus = EventBusSubscriber.Bus.MOD" in modbus
assert "ClientEvents.registerKeyMappings(event)" in modbus

for token in (
    "extends Screen", "panelWidth = 418", "panelHeight = 290",
    "g.pose().scale(scale, scale, 1.0F)",
    "Principal", "Combate", "Autonomia", "Mudar forma", "Descansar",
    "Seguir / Ficar", "Marcar criatura", "Marcar area", "Cancelar ataque",
    "Cacar animais", "Em desenvolvimento", "isPauseScreen()",
    "ClientEvents.matchesMenuKey(key, scan)", "AurenAttackOverlay.setSelectedRadius",
    "ClientEvents.beginSelection(area)", "Minecraft.getInstance().setScreen(null)"
): assert token in menu, token
assert "setBlock(" not in menu and "markEnemy(" not in menu

for token in (
    "RenderLevelStageEvent.Stage.AFTER_PARTICLES", "RenderType.lines()",
    "LevelRenderer.renderLineBox(", "private static void ring(",
    "if (!previewActive(mc)) return;", "selectedRadius >= 30 ? 5 : selectedRadius + 5",
    "ClientEvents.isAreaTargeting()", "ClientEvents.isCreatureTargeting()",
    "Botao direito: confirmar", "V: voltar ao menu",
    "mc.level == previewLevel", "living instanceof TamableAnimal pet",
    "dragon.getOwnerUUID().equals(pet.getOwnerUUID())",
): assert token in overlay, token
assert "graphics.fill(" not in overlay

for token in (
    "record AurenMenuActionPayload(int action, int radius)",
    "TARGET_CREATURE = 5", "TARGET_AREA = 6",
    "STOP_ATTACK = 7", "TOGGLE_AREA = 8",
): assert token in packet, token
for banned in ("BlockPos", "Vec3", "entityId", "dragonId"):
    assert banned not in packet, banned
assert "record AurenMarkPayload(int radius, boolean clear)" in legacy
assert "registrar.playToServer(AurenMenuActionPayload.ID" in network
assert "registrar.playToServer(AurenMarkPayload.ID" in network
assert "context.player() instanceof ServerPlayer" in network

for token in (
    "handleMenuAction(ServerPlayer player, int action, int radius)",
    "ownedDragon(player)", "dragon.canExpandToAdultHere()",
    "player.getViewVector(1.0F).scale(AIM_REACH)",
    "radius < 5 || radius > 30 || radius % 5 != 0",
    "ProjectileUtil.getEntityHitResult(",
    "ClipContext.Block.COLLIDER",
    "dragon.getAttackOrders().markEnemy(enemy)",
    "dragon.getAttackOrders().markArea(block.getBlockPos(), radius)",
    "dragon.getAttackOrders().canMarkTarget(living)",
    "handleMarkKey(ServerPlayer player, int radius, boolean clear)",
    "Commands.literal(\"atacar\")", "Commands.literal(\"parar\")",
): assert token in commands, token
for token in (
    "new SitWhenOrderedToGoal(this)", "requestOwnerRest(boolean rest)",
    "canExpandToAdultHere()", "getSyncedMarkMode()",
    "getSyncedMarkCenter()", "getSyncedMarkedEnemyId()",
    "getSyncedMarkRadius()", "this.syncAttackMark()"
): assert token in dragon, token

assert "mod_version=0.7.29-unified-ui-alpha" in (root / "gradle.properties").read_text()
jars = list((root / "build/libs").glob("aurenminecraft-*.jar"))
assert len(jars) == 1, jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for path in (
        "br/com/auren/minecraft/client/AurenMenuScreen.class",
        "br/com/auren/minecraft/client/ClientEvents.class",
        "br/com/auren/minecraft/client/AurenAttackOverlay.class",
        "br/com/auren/minecraft/network/AurenMenuActionPayload.class",
        "br/com/auren/minecraft/network/AurenMarkPayload.class",
        "assets/aurenminecraft/lang/pt_br.json",
    ): assert path in jar.namelist(), path
print("PASS: unified V menu, secure right-click targeting, server-side actions and packaged UI")
