/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * CE scorched terrain implementation for one Auren dragon.
 * No BlockItem registries: burn marks exist as terrain effects only.
 */
package br.com.auren.minecraft.entity;

import br.com.auren.minecraft.AurenMinecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AurenScorchedBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AurenMinecraft.MOD_ID);

    public static final DeferredHolder<Block, Block> GRASS = BLOCKS.register("chared_grass",
            () -> new AurenScorchedBlock(
                    BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GRASS),
                    Blocks.GRASS_BLOCK.defaultBlockState()));
    public static final DeferredHolder<Block, Block> DIRT = BLOCKS.register("chared_dirt",
            () -> new AurenScorchedBlock(
                    BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GRAVEL),
                    Blocks.DIRT.defaultBlockState()));
    public static final DeferredHolder<Block, Block> STONE = BLOCKS.register("chared_stone",
            () -> new AurenScorchedBlock(
                    BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.STONE),
                    Blocks.STONE.defaultBlockState()));
    public static final DeferredHolder<Block, Block> COBBLESTONE = BLOCKS.register("chared_cobblestone",
            () -> new AurenScorchedBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.STONE),
                    Blocks.COBBLESTONE.defaultBlockState()));
    public static final DeferredHolder<Block, Block> GRAVEL = BLOCKS.register("chared_gravel",
            () -> new AurenScorchedBlock(
                    BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GRAVEL),
                    Blocks.GRAVEL.defaultBlockState()));
    public static final DeferredHolder<Block, Block> DIRT_PATH = BLOCKS.register("chared_dirt_path",
            () -> new AurenScorchedBlock(
                    BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GRAVEL),
                    Blocks.DIRT_PATH.defaultBlockState()));

    private AurenScorchedBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
