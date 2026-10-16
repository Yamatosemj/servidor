/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Focused CE IafDragonDestructionManager.transformBlockFire / attackBlock
 * stage-3 burn marks. Original source (LGPL):
 * aa2f11a6b79acb48a1094765d610cdaca79e3d18.
 */
package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;

/** World edits are server-owned, mobGriefing-gated and block-type-whitelisted. */
public final class AurenScorchTerrain {
    private AurenScorchTerrain() {}

    /**
     * CE stage<=3 processes the cube centered on impact (one block around
     * each axis). Only whitelisted block types can become CE-looking scorch blocks.
     * Player-placed dirt, stone and cobblestone are indistinguishable from natural blocks.
     */
    public static void scorch(ServerLevel level, BlockPos impact, AurenDragon dragon) {
        scorch(level, impact, dragon, null, 0);
    }

    /**
     * Bounded stage-3 CE charge-style destruction for AREA orders only.
     * No explosions, drops, block entities, unloaded chunks, or unbreakables.
     * Unlike terrain scorching, village facades actually lose blocks.
     */
    public static void bombard(ServerLevel level, BlockPos impact, AurenDragon dragon,
                               BlockPos markedCenter, int radius) {
        if (markedCenter == null || radius < 5 || radius > 30
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !level.hasChunkAt(impact)) return;
        int destroyed = 0;
        // A sustained sweep damages both the ground and the buildings over it.
        for (BlockPos pos : BlockPos.betweenClosed(
                impact.offset(-3, -2, -3), impact.offset(3, 2, 3))) {
            if (destroyed >= 20) break;
            double dx = pos.getX() - markedCenter.getX();
            double dz = pos.getZ() - markedCenter.getZ();
            if (dx * dx + dz * dz > radius * radius
                    || impact.distSqr(pos) > 12.0D
                    || pos.getY() < level.getMinBuildHeight()
                    || pos.getY() >= level.getMaxBuildHeight()
                    || !level.hasChunkAt(pos)
                    || level.getBlockEntity(pos) != null
                    || dragon.getRandom().nextFloat() > 0.85F) continue;
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (state.isAir() || !state.getFluidState().isEmpty()
                    || hardness < 0 || hardness > 8.0F) continue;
            // The old whitelist omitted most village foundations/ground.
            // Respect hardness and block entities, but allow both structural
            // and diggable terrain blocks in the marked destructible circle.
            boolean villageBlock = state.is(BlockTags.MINEABLE_WITH_AXE)
                    || state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                    || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                    || state.is(BlockTags.MINEABLE_WITH_HOE)
                    || state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)
                    || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE)
                    || state.is(Blocks.HAY_BLOCK) || state.is(Blocks.DIRT)
                    || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SAND)
                    || state.is(Blocks.GRAVEL);

            if (villageBlock && level.destroyBlock(pos, false)) ++destroyed;
        }
    }

    /**
     * Only area bombing ignites ordinary flammable building materials.
     * Soul fire cannot survive beside normal wood; a real vanilla FIRE block
     * is needed for wooden walls/roofs to catch and spread flames.
     * The blue breath PARTICLES are unaffected.
     */
    public static void igniteVillage(ServerLevel level, BlockPos impact, AurenDragon dragon,
                                     BlockPos markedCenter, int radius) {
        if (markedCenter == null || radius < 5 || radius > 30
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !level.hasChunkAt(impact)) return;
        int lit = 0;
        for (BlockPos wood : BlockPos.betweenClosed(
                impact.offset(-3, -2, -3), impact.offset(3, 2, 3))) {
            if (lit >= 8) break;
            double dx = wood.getX() - markedCenter.getX();
            double dz = wood.getZ() - markedCenter.getZ();
            if (dx * dx + dz * dz > radius * radius
                    || !level.hasChunkAt(wood) || level.getBlockEntity(wood) != null)
                continue;
            BlockState fuel = level.getBlockState(wood);
            if (!(fuel.is(BlockTags.PLANKS) || fuel.is(BlockTags.LOGS)
                    || fuel.is(BlockTags.WOOL) || fuel.is(BlockTags.LEAVES)
                    || fuel.is(Blocks.HAY_BLOCK)) || dragon.getRandom().nextFloat() > 0.65F)
                continue;
            for (Direction face : Direction.values()) {
                BlockPos firePos = wood.relative(face);
                double fx = firePos.getX() - markedCenter.getX();
                double fz = firePos.getZ() - markedCenter.getZ();
                if (fx * fx + fz * fz > radius * radius
                        || !level.hasChunkAt(firePos)
                        || !level.isEmptyBlock(firePos)
                        || !Blocks.FIRE.defaultBlockState().canSurvive(level, firePos))
                    continue;
                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                ++lit;
                break;
            }
        }
    }

    /** Restrict autonomous area-order terrain changes to its selected radius. */
    public static void scorch(ServerLevel level, BlockPos impact, AurenDragon dragon,
                              BlockPos markedCenter, int radius) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        if (!level.hasChunkAt(impact)) return;
        BlockPos.betweenClosedStream(impact.offset(-1, -1, -1), impact.offset(1, 1, 1))
                .forEach(pos -> {
                    if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) != null
                            || !dragon.getRandom().nextBoolean()) return;
                    if (markedCenter != null) {
                        double dx = pos.getX() - markedCenter.getX();
                        double dz = pos.getZ() - markedCenter.getZ();
                        if (dx * dx + dz * dz > radius * radius) return;
                    }
                    BlockState before = level.getBlockState(pos);
                    BlockState after = convertNaturalBlock(before);
                    if (after != null && !after.is(before.getBlock())) {
                        level.setBlockAndUpdate(pos, after);
                    }
                });
    }

    /**
     * Original CE fire transformation table: grass, dirt, gravel,
     * cobblestone, overworld stone, dirt path. Ignores non-whitelisted types, existing scorch blocks,
     * block entities and unloaded terrain. Whitelisted player-placed blocks can be affected.
     *
     * The original CE has additional leaf/flower/log/ash transformations;
     * these intentionally remain outside the terrain-only v0.7.6 scope.
     */
    private static BlockState convertNaturalBlock(BlockState original) {
        if (original.getBlock() instanceof SpreadingSnowyDirtBlock)
            return AurenScorchedBlocks.GRASS.get().defaultBlockState();
        if (original.is(Blocks.DIRT))
            return AurenScorchedBlocks.DIRT.get().defaultBlockState();
        if (original.is(Blocks.GRAVEL))
            return AurenScorchedBlocks.GRAVEL.get().defaultBlockState();
        if (original.is(Blocks.COBBLESTONE))
            return AurenScorchedBlocks.COBBLESTONE.get().defaultBlockState();
        if (original.is(BlockTags.BASE_STONE_OVERWORLD))
            return AurenScorchedBlocks.STONE.get().defaultBlockState();
        if (original.is(Blocks.DIRT_PATH))
            return AurenScorchedBlocks.DIRT_PATH.get().defaultBlockState();
        return null;
    }
}
