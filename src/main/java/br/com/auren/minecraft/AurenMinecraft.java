package br.com.auren.minecraft;

import br.com.auren.minecraft.entity.AurenDragon;
import br.com.auren.minecraft.network.AurenRiderNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AurenMinecraft.MOD_ID)
public final class AurenMinecraft {
    public static final String MOD_ID = "aurenminecraft";

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AurenDragon>> AUREN =
            ENTITIES.register("auren", () ->
                    EntityType.Builder.of(AurenDragon::new, MobCategory.CREATURE)
                            .sized(1.65F, 2.0F)
                            .clientTrackingRange(12)
                            .build(MOD_ID + ":auren"));

    public AurenMinecraft(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(AurenRiderNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AurenCommands::register);
        modBus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AUREN.get(), AurenDragon.createAttributes().build());
    }
}
