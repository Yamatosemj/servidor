package br.com.auren.minecraft;

import br.com.auren.minecraft.entity.AurenDragon;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.registry.IafEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;

/** Touch-friendly fallback to interact with the small dragon on mobile. */
public final class AurenCommands {
    private AurenCommands() {}


    /**
     * This is the actual CE entity, not a copy of its travel/AI/combat code.
     * A side-by-side parity baseline; the legacy Auren remains untouched.
     * Must be run in a safe open area. OP only.
     */
    private static int spawnOriginal(CommandContext<CommandSourceStack> context, boolean lightning)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        DragonBaseEntity dragon = lightning
                ? IafEntities.LIGHTNING_DRAGON.get().create(level)
                : IafEntities.FIRE_DRAGON.get().create(level);
        if (dragon == null) {
            context.getSource().sendFailure(Component.literal("Ice and Fire nao criou o dragao."));
            return 0;
        }
        dragon.setAgeInDays(50);
        dragon.setVariant(lightning ? "black" : "red");
        dragon.tame(player);
        dragon.setCommand(2);
        dragon.setCustomName(Component.literal("Auren"));
        dragon.setCustomNameVisible(true);
        dragon.addTag("auren_native_ce");
        dragon.moveTo(player.getX() + 5D, player.getY(), player.getZ() + 5D,
                player.getYRot(), 0.0F);
        dragon.refreshDimensions();
        if (!level.noCollision(dragon, dragon.getBoundingBox())) {
            context.getSource().sendFailure(Component.literal(
                    "Sem espaco para invocar um dragao adulto. Tente em terreno aberto."));
            return 0;
        }
        if (!level.addFreshEntity(dragon)) {
            context.getSource().sendFailure(Component.literal("Falha ao adicionar o dragao CE ao mundo."));
            return 0;
        }
        dragon.setHealth(dragon.getMaxHealth());
        context.getSource().sendSuccess(() -> Component.literal(
                lightning ? "Auren CE original (raio/negro) invocado."
                        : "Auren CE original (fogo/vermelho) invocado."), false);
        return Command.SINGLE_SUCCESS;
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("auren")
                    .then(Commands.literal("original")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> spawnOriginal(context, true)))
                    .then(Commands.literal("original_fogo")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> spawnOriginal(context, false)))
                    .then(Commands.literal("ombro").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        List<AurenDragon> nearby = player.level().getEntitiesOfClass(
                                AurenDragon.class,
                                player.getBoundingBox().inflate(16D),
                                dragon -> dragon.isTame() && dragon.isOwnedBy(player)
                        );
                        AurenDragon dragon = nearby.stream()
                                .min(Comparator.comparingDouble(player::distanceToSqr))
                                .orElse(null);
                        if (dragon == null) {
                            context.getSource().sendFailure(Component.literal(
                                    "Nenhum Auren domesticado encontrado em 16 blocos."));
                            return 0;
                        }
                        if (dragon.isVehicle()) {
                            context.getSource().sendFailure(Component.literal(
                                    "Desmonte do Auren antes de usar o ombro."));
                            return 0;
                        }
                        if (dragon.isOnShoulder()) {
                            dragon.setOnShoulder(false);
                            context.getSource().sendSuccess(() ->
                                    Component.literal("Auren saiu do seu ombro."), false);
                        } else {
                            dragon.setSmallForm(true);
                            dragon.setOnShoulder(true);
                            context.getSource().sendSuccess(() ->
                                    Component.literal("Auren subiu no seu ombro."), false);
                        }
                        return Command.SINGLE_SUCCESS;
                    }))
        );
    }
}
