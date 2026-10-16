/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Focused CE IafDragonDestructionManager.transformBlockFire / attackBlock
 * stage-3 burn marks. Original source (LGPL):
 * aa2f11a6b79acb48a1094765d610cdaca79e3d18.
 */
package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
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
