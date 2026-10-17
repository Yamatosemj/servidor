package br.com.auren.minecraft;

import br.com.auren.minecraft.entity.AurenDragon;
import br.com.auren.minecraft.bond.AurenBondItem;
import br.com.auren.minecraft.entity.AurenScorchedBlocks;
import br.com.auren.minecraft.network.AurenRiderNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AurenMinecraft.MOD_ID)
public final class AurenMinecraft {
    public static final String MOD_ID = "aurenminecraft";

    // One non-placeable, non-creative token for the existing Auren; never a second dragon.
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<AurenBondItem> BOND_ITEM =
            ITEMS.register("auren_bond", () -> new AurenBondItem(new Item.Properties()));

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AurenDragon>> AUREN =
            ENTITIES.register("auren", () ->
                    EntityType.Builder.of(AurenDragon::new, MobCategory.CREATURE)
                            .sized(1.65F, 2.0F)
                            // Dragonfire cannot hurt the fire dragon itself.
                            // In particular: FIRE, SOUL_FIRE, MAGMA and LAVA.
                            .fireImmune()
                            .clientTrackingRange(12)
                            .build(MOD_ID + ":auren"));

    public AurenMinecraft(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        // Scorch-marks only: NO BlockItems or creative inventory entries.
        AurenScorchedBlocks.register(modBus);
        modBus.addListener(AurenRiderNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AurenCommands::register);
        modBus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AUREN.get(), AurenDragon.createAttributes().build());
    }
}
