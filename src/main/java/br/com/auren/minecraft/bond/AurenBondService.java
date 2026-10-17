package br.com.auren.minecraft.bond;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-only owner-token reconciliation. A token is never used to restore
 * unsaved NBT; only the world SavedData can authorize a transformation.
 */
@EventBusSubscriber(modid = AurenMinecraft.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class AurenBondService {
    private AurenBondService() {}
    private static final Set<UUID> AUTHORIZED_SPAWNS = new HashSet<>();

    public static boolean isBound(ServerPlayer player) {
        AurenBondSavedData.Entry e = AurenBondSavedData.get(player.server).forOwner(player.getUUID());
        return e != null && e.bound();
    }

    private static void say(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s), true);
    }

    public static boolean capture(ServerPlayer p, AurenDragon dragon) {
        if (!p.isAlive() || p.isSpectator() || !(dragon.level() instanceof ServerLevel)
                || !dragon.isAlive() || !dragon.isTame() || !dragon.isOwnedBy(p)
                || dragon.isVehicle() || dragon.isPassenger()
                || dragon.distanceToSqr(p) > 64 * 64) return false;
        AurenBondSavedData store = AurenBondSavedData.get(p.server);
        AurenBondSavedData.Entry previous = store.forOwner(p.getUUID());
        if (previous != null && (previous.bound()
                || !previous.dragon().equals(dragon.getUUID()))) {
            say(p, "Ja existe outro Auren vinculado a este jogador.");
            return false;
        }
        if (store.isKnownOtherDragon(p.getUUID(), dragon.getUUID())) return false;
        // Refuse to store Auren if the only slot for its physical token is full.
        boolean hasRoom = p.getInventory().getFreeSlot() >= 0;
        if (!hasRoom) {
            say(p, "Libere um espaco no inventario para guardar o Auren.");
            return false;
        }
        dragon.getAttackOrders().clear();
        dragon.setOrderedToSit(false);
        dragon.setSmallForm(true);
        dragon.setOnShoulder(false);
        CompoundTag entity = new CompoundTag();
        dragon.saveWithoutId(entity);
        entity.putString("id", "aurenminecraft:auren");
        entity.putBoolean("SmallForm", true);
        entity.putBoolean("OnShoulder", false);
        entity.putUUID("UUID", dragon.getUUID());

        AurenBondSavedData.Entry entry = new AurenBondSavedData.Entry(
                p.getUUID(), dragon.getUUID(), true, entity,
                dragon.level().dimension().location().toString());
        store.put(entry);  // Authoritative BOUND record exists before discarding.
        dragon.discard();
        reconcile(p);
        say(p, "Auren agora esta na sua cabeca. Segure o icone e use para soltá-lo.");
        return true;
    }

    private static Vec3 findSafeSpot(ServerLevel level, ServerPlayer player) {
        double[][] offsets = {{1.2,0},{-1.2,0},{0,1.2},{0,-1.2},
                              {1.3,1.3},{-1.3,-1.3}};
        for (double[] xy : offsets) {
            double x = player.getX() + xy[0], y = player.getY(), z = player.getZ() + xy[1];
            net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(x,y,z);
            if (!level.hasChunkAt(base) || !level.hasChunkAt(base.above())) continue;
            AABB box = new AABB(x-0.25,y,z-0.25,x+0.25,y+0.7,z+0.25);
            if (level.noCollision(box) && level.getFluidState(base).isEmpty())
                return new Vec3(x,y,z);
        }
        return null;
    }

    public static boolean deploy(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator()) return false;
        AurenBondSavedData data = AurenBondSavedData.get(p.server);
        AurenBondSavedData.Entry entry = data.forOwner(p.getUUID());
        if (entry == null || !entry.bound()) {
            say(p, "Este item nao representa um Auren ativo.");
            return false;
        }
        // Neither creative-given items nor forged UUIDs can spawn another Auren.
        ServerLevel level = p.serverLevel();
        Vec3 spawn = findSafeSpot(level, p);
        if (spawn == null) {
            say(p, "Nao ha espaco livre para o Auren aparecer.");
            return false;
        }
        AurenDragon dragon = AurenMinecraft.AUREN.get().create(level);
        if (dragon == null) return false;
        try {
            CompoundTag tag = entry.entityNbt().copy();
            dragon.load(tag);
            if (!dragon.getUUID().equals(entry.dragon())
                    || !dragon.isTame() || !dragon.isOwnedBy(p)) {
                say(p, "Vinculo invalido: Auren nao foi invocado.");
                return false;
            }
            dragon.setSmallForm(true);
            dragon.setOnShoulder(false);
            dragon.setPos(spawn.x, spawn.y, spawn.z);
            dragon.setOrderedToSit(false);
            AUTHORIZED_SPAWNS.add(entry.dragon());
            boolean added;
            try {
                added = level.addFreshEntity(dragon);
            } finally {
                AUTHORIZED_SPAWNS.remove(entry.dragon());
            }
            if (!added) {
                say(p, "Falha ao materializar o Auren. Item preservado.");
                return false;
            }
            data.put(new AurenBondSavedData.Entry(p.getUUID(), entry.dragon(), false,
                    entry.entityNbt().copy(), level.dimension().location().toString()));
            reconcile(p); // Remove the token only AFTER the entity joins successfully.
            say(p, "Auren voltou para a forma pequena.");
            return true;
        } catch (RuntimeException exception) {
            say(p, "Nao foi possivel restaurar o Auren; item preservado.");
            return false;
        }
    }

    private static boolean token(ItemStack stack) {
        return stack.is(AurenMinecraft.BOND_ITEM.get());
    }

    /** Drop/creative/duplication repair is derived from SavedData, not the token. */
    public static void reconcile(ServerPlayer player) {
        AurenBondSavedData.Entry state =
                AurenBondSavedData.get(player.server).forOwner(player.getUUID());
        boolean shouldHave = state != null && state.bound();
        boolean found = false;
        Inventory inv = player.getInventory();
        for (int i=0; i<inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!token(stack)) continue;
            if (!shouldHave || found) {
                inv.items.set(i, ItemStack.EMPTY);
            } else {
                found = true;
                if (stack.getCount()!=1) stack.setCount(1);
            }
        }
        for (int i=0; i<inv.offhand.size(); i++) {
            ItemStack stack = inv.offhand.get(i);
            if (!token(stack)) continue;
            if (!shouldHave || found) inv.offhand.set(i,ItemStack.EMPTY);
            else found = true;
        }
        // Cursor temporarily holds the owner's token while reordering slots.
        if (token(player.containerMenu.getCarried())) {
            if (!shouldHave || found) player.containerMenu.setCarried(ItemStack.EMPTY);
            else found = true;
        }
        // Don't permit transfer into currently-open chests/furnaces/etc.
        // Their items are not the authoritative Auren and have no redeemable NBT.
        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            if (slot.container != inv && token(slot.getItem())) slot.set(ItemStack.EMPTY);
        }
        if (shouldHave && !found) {
            int free = inv.getFreeSlot();
            if (free >= 0) inv.items.set(free, new ItemStack(AurenMinecraft.BOND_ITEM.get()));
        }
        inv.setChanged();
        player.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 10 == 0)
            reconcile(p);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // No copy of the dragon NBT or token is performed here. Next player tick
        // regenerates precisely one icon from persistent SavedData after respawn.
        if (event.getEntity() instanceof ServerPlayer p) reconcile(p);
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (token(event.getEntity().getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        // Dropping or duplicating the token is forbidden even with keepInventory false.
        if (event.getEntity() instanceof Player)
            event.getDrops().removeIf(drop -> token(drop.getItem()));
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Catch even a one-tick transfer to chest before the periodic scan.
        for (net.minecraft.world.inventory.Slot slot : event.getContainer().slots) {
            if (slot.container != player.getInventory() && token(slot.getItem()))
                slot.set(ItemStack.EMPTY);
        }
        reconcile(player);
    }

    @SubscribeEvent
    public static void onDragonJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof AurenDragon dragon)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        AurenBondSavedData state = AurenBondSavedData.get(level.getServer());
        AurenBondSavedData.Entry ownerEntry = dragon.getOwnerUUID() == null
                ? null : state.forOwner(dragon.getOwnerUUID());
        if ((state.isBoundDragon(dragon.getUUID())
                || (ownerEntry != null && ownerEntry.bound()))
                && !AUTHORIZED_SPAWNS.contains(dragon.getUUID())) {
            // Crash rollback / old chunk loading while the world record is BOUND.
            event.setCanceled(true);
        }
    }
}
