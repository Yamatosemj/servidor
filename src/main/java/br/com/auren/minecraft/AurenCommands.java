package br.com.auren.minecraft;

import br.com.auren.minecraft.entity.AurenDragon;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;

/**
 * Touch-friendly server-side marking by crosshair. No separate GUI required.
 * Every order is scoped to an already-tamed, nearby, owner-verified Auren.
 */
public final class AurenCommands {
    private static final double DRAGON_REACH = 64.0D;
    private static final double AIM_REACH = 48.0D;

    private AurenCommands() {}

    private static AurenDragon ownedDragon(ServerPlayer player) {
        List<AurenDragon> nearby = player.level().getEntitiesOfClass(
                AurenDragon.class, player.getBoundingBox().inflate(DRAGON_REACH),
                dragon -> dragon.isAlive() && dragon.isTame() && dragon.isOwnedBy(player));
        return nearby.stream().min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private static AurenDragon getDragon(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AurenDragon dragon = ownedDragon(player);
        if (dragon == null) context.getSource().sendFailure(
                Component.literal("Nenhum Auren seu encontrado em 64 blocos."));
        return dragon;
    }

    private static int markEnemy(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AurenDragon dragon = getDragon(context);
        if (dragon == null) return 0;
        if (dragon.isVehicle()) {
            context.getSource().sendFailure(Component.literal("Desmonte antes de marcar ataques."));
            return 0;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(AIM_REACH));
        BlockHitResult obstacle = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (obstacle.getType() == HitResult.Type.BLOCK) end = obstacle.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player.level(), player, eye, end, new AABB(eye, end).inflate(1.0D),
                entity -> entity instanceof LivingEntity living
                        && dragon.getAttackOrders().canMarkTarget(living));
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            context.getSource().sendFailure(Component.literal(
                    "Mire em uma criatura válida a até 48 blocos."));
            return 0;
        }
        if (!dragon.getAttackOrders().markEnemy(target)) {
            context.getSource().sendFailure(Component.literal(
                    "Alvo inválido ou além de 64 blocos de distância do Auren."));
            return 0;
        }
        context.getSource().sendSuccess(() ->
                Component.literal("Auren: inimigo marcado. /auren parar cancela."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int markArea(CommandContext<CommandSourceStack> context, int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AurenDragon dragon = getDragon(context);
        if (dragon == null) return 0;
        if (dragon.isVehicle()) {
            context.getSource().sendFailure(Component.literal("Desmonte antes de marcar ataques."));
            return 0;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(AIM_REACH));
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendFailure(Component.literal(
                    "Mire em um bloco a até 48 blocos para marcar a área."));
            return 0;
        }
        BlockPos center = hit.getBlockPos();
        if (!dragon.getAttackOrders().markArea(center, radius)) {
            context.getSource().sendFailure(Component.literal(
                    "Área inválida, descarregada ou além de 64 blocos do Auren."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Auren: bombardeio de " + radius + " blocos marcado em "
                + center.toShortString() + ". /auren parar cancela."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        AurenDragon dragon = getDragon(context);
        if (dragon == null) return 0;
        dragon.getAttackOrders().clear();
        context.getSource().sendSuccess(() ->
                Component.literal("Auren: alvo desmarcado. Bombardeio encerrado."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        AurenDragon dragon = getDragon(context);
        if (dragon == null) return 0;
        context.getSource().sendSuccess(() ->
                Component.literal("Auren: " + dragon.getAttackOrders().description()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int shoulder(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AurenDragon dragon = getDragon(context);
        if (dragon == null) return 0;
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
    }


    /**
     * Keybind entry point. Target selection is repeated on the SERVER from the
     * player's actual gaze. Never trust client-provided coordinates or entity IDs.
     */
    public static void handleMarkKey(ServerPlayer player, int radius, boolean clear) {
        AurenDragon dragon = ownedDragon(player);
        if (dragon == null) {
            player.displayClientMessage(Component.literal("Auren não encontrado perto de você."), true);
            return;
        }
        if (clear) {
            dragon.getAttackOrders().clear();
            player.displayClientMessage(Component.literal("Auren: ataque cancelado."), true);
            return;
        }
        // A reserved action (radius zero) toggles the persistent aerial area mode.
        // No position or entity ID ever crosses the client/server boundary.
        if (radius == 0) {
            boolean enabled = dragon.getAttackOrders().toggleAreaEnabled();
            player.displayClientMessage(Component.literal("Auren: bombardeio "
                    + (enabled ? "ativado." : "desativado.")), true);
            return;
        }
        if (radius < 5 || radius > 30 || radius % 5 != 0) return;
        if (dragon.isVehicle() || dragon.isSmallForm() || dragon.isOnShoulder()) {
            player.displayClientMessage(Component.literal("Use a forma adulta e desmonte para marcar."), true);
            return;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(AIM_REACH));
        BlockHitResult block = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() == HitResult.Type.BLOCK) end = block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(
                player.level(), player, eye, end, new AABB(eye, end).inflate(1.0D),
                candidate -> candidate instanceof LivingEntity living
                        && living.isAlive() && candidate != player && candidate != dragon);
        if (entity != null) {
            if (entity.getEntity() instanceof LivingEntity mob
                    && dragon.getAttackOrders().markEnemy(mob)) {
                player.displayClientMessage(Component.literal("Auren: inimigo marcado!"), true);
            } else {
                player.displayClientMessage(Component.literal(
                        "Alvo inválido, protegido ou além de 64 blocos do Auren."), true);
            }
            return;
        }
        if (!dragon.getAttackOrders().isAreaEnabled()) {
            player.displayClientMessage(Component.literal(
                    "Bombardeio desligado. Aperte B para ativar."), true);
            return;
        }
        if (block.getType() == HitResult.Type.BLOCK
                && dragon.getAttackOrders().markArea(block.getBlockPos(), radius)) {
            player.displayClientMessage(Component.literal(
                    "Auren: bombardeio marcado, raio " + radius + " blocos."), true);
        } else {
            player.displayClientMessage(Component.literal(
                    "Mire em uma criatura/bloco a até 48 blocos, e no máximo 64 do Auren."), true);
        }
    }

    public static void register(RegisterCommandsEvent event) {
        var areas = Commands.literal("area");
        for (int radius = 5; radius <= 30; radius += 5) {
            final int selected = radius;
            areas.then(Commands.literal(Integer.toString(selected))
                    .executes(ctx -> markArea(ctx, selected)));
        }
        event.getDispatcher().register(
                Commands.literal("auren")
                        .then(Commands.literal("ombro").executes(AurenCommands::shoulder))
                        .then(Commands.literal("atacar")
                                .then(Commands.literal("inimigo")
                                        .executes(AurenCommands::markEnemy))
                                .then(areas))
                        .then(Commands.literal("parar").executes(AurenCommands::stop))
                        .then(Commands.literal("status").executes(AurenCommands::status))
        );
    }
}
