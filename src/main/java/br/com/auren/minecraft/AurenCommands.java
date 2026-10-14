package br.com.auren.minecraft;

import br.com.auren.minecraft.entity.AurenDragon;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;

/** Touch-friendly fallback to interact with the small dragon on mobile. */
public final class AurenCommands {
    private AurenCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("auren")
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
