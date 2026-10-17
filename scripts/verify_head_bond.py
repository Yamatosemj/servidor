"""Source/package guard for v0.7.30 bonded-head alpha (NOT in-world gameplay QA)."""
from pathlib import Path
from zipfile import ZipFile
from struct import unpack
root=Path(__file__).resolve().parents[1]
src=root/'src/main/java/br/com/auren/minecraft'
def read(p): return (src/p).read_text()
store=read('bond/AurenBondSavedData.java')
svc=read('bond/AurenBondService.java')
item=read('bond/AurenBondItem.java')
model=read('client/AurenHeadCompanionRenderer.java')
mod=read('AurenMinecraft.java')
cmd=read('AurenCommands.java')
dragon=read('entity/AurenDragon.java')
for val in ('extends SavedData','server.overworld()', 'Map<UUID, Entry>', 'entityNbt().copy()',
            'setDirty()', 'boolean bound', 'isBoundDragon(UUID id)'):
    assert val in store, val
for val in ('dragon.saveWithoutId(entity)', 'store.put(entry)', 'dragon.discard()',
            'AUTHORIZED_SPAWNS.add(entry.dragon())', 'level.addFreshEntity(dragon)',
            'if (!added)', 'dragon.load(tag)', 'dragon.isOwnedBy(p)', 
            'data.put(new AurenBondSavedData.Entry', 'reconcile(p)',
            'onDragonJoin(EntityJoinLevelEvent event)', 'event.setCanceled(true)',
            'onDrops(LivingDropsEvent event)', 'onToss(ItemTossEvent event)',
            'onClone(PlayerEvent.Clone event)', 'player.containerMenu.getCarried()',
            'slot.container != inv', 'inv.getFreeSlot()', 'findSafeSpot('):
    assert val in svc,val
assert svc.index('store.put(entry)') < svc.index('dragon.discard()')
assert svc.index('level.addFreshEntity(dragon)') < svc.index('data.put(new AurenBondSavedData.Entry')
assert 'new AurenDragon(' not in item
assert 'AurenBondService.deploy(player)' in item
assert 'canFitInsideContainerItems()' in item
assert 'model.renderToBuffer(' in model
assert 'getRenderer().getModel().head.translateAndRotate(pose)' in model
assert 'new AurenDragon(' not in model
assert 'AurenBondService.capture(player, dragon)' in cmd
assert 'AurenBondService.capture(serverPlayer, this)' in dragon
assert mod.count('ITEMS.register("auren_bond"')==1
assert 'mod_version=0.7.30-bonded-head-alpha' in (root/'gradle.properties').read_text()
jars=list((root/'build/libs').glob('aurenminecraft-*.jar'))
assert len(jars)==1, jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for f in ('assets/aurenminecraft/textures/item/auren_bond.png',
              'assets/aurenminecraft/models/item/auren_bond.json',
              'br/com/auren/minecraft/bond/AurenBondSavedData.class',
              'br/com/auren/minecraft/bond/AurenBondItem.class',
              'br/com/auren/minecraft/bond/AurenBondService.class',
              'br/com/auren/minecraft/client/AurenHeadCompanionRenderer.class'):
        assert f in jar.namelist(), f
    png=jar.read('assets/aurenminecraft/textures/item/auren_bond.png')
    assert png[:8]==b'\x89PNG\r\n\x1a\n'
    assert unpack('>II',png[16:24])==(16,16)
print('PASS: saved-data authority, inert one-off inventory token and exact 16x16 sprite')
