/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Focused burn-mark block adapted from IceAndFire CE ReturningStateBlock.
 */
package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Temporary scorched surface that eventually restores its vanilla terrain. */
public final class AurenScorchedBlock extends Block {
    private final BlockState restored;

    public AurenScorchedBlock(Properties props, BlockState restored) {
        super(props.randomTicks());
        this.restored = restored;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level,
                           BlockPos pos, RandomSource random) {
        if (!level.hasChunkAt(pos) || !state.is(this)) return;
        // CE ReturningStateBlock restores a burned block. This random-tick
        // implementation actually executes (CE's tick doesn't auto-run).
        if (random.nextInt(8) == 0) {
            level.setBlockAndUpdate(pos, restored);
        }
    }
}
