/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * The whitelist of initial scorch states is adapted from the IceAndFire CE
 * IafDragonDestructionManager.transformBlockFire, original LGPL-3.0-or-later.
 * The footprint and accumulated heat below are Auren-specific extensions.
 */
package br.com.auren.minecraft.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Irregular, flame-first dragonfire footprints. All edits are server-side,
 * strictly block-whitelisted, bounded by marked-area radius and mobGriefing.
 * Wood is ONLY ignited using vanilla fire; no dragon demolition of wood.
 */
public final class AurenScorchTerrain {
    private static final int HEAT_EXPIRY_TICKS = 240;
    private static final int MAX_HEAT_POSITIONS = 4096;
    private static final int SCORCH_HEAT = 28;
    private static final int MAGMA_HEAT = 135;
    private static final int GRASS_BREAK_HEAT = 195;
    private static final int MAGMA_BREAK_HEAT = 330;
    private static final int MAX_FLAMES_PER_IMPACT = 12;

    // One in-memory, bounded tracker per ServerLevel. Does not force chunks to
    // load, create extra block entities, or persist heat into saved worlds.
    // Weak level keys ensure stopped worlds are collectible.
    private static final Map<ServerLevel, HeatTracker> HEAT = new WeakHashMap<>();

    private static final class HeatCell {
        int heat;
        long lastContact;

        HeatCell(long tick) { lastContact = tick; }
    }

    private static final class HeatTracker {
        final Map<BlockPos, HeatCell> cells = new HashMap<>();
        long lastCleanup = -1000L;
    }

    private AurenScorchTerrain() {}

    public static void scorch(ServerLevel level, BlockPos impact, AurenDragon dragon) {
        scorch(level, impact, dragon, null, 0);
    }

    /**
     * Irregular oval burn, approximately 5-7 blocks across, not a filled
     * square. Every breath tick uses different jitter and skipped edge tiles.
     * Repeated hits accumulate HEAT, leaving blue soul fire where possible,
     * orange vanilla fire elsewhere and genuine flame particles at the soil.
     */
    public static void scorch(ServerLevel level, BlockPos impact, AurenDragon dragon,
                              BlockPos markedCenter, int radius) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || (markedCenter != null && (radius < 5 || radius > 30))
                || !level.hasChunkAt(impact)) return;

        HeatTracker tracker = HEAT.computeIfAbsent(level, ignored -> new HeatTracker());
        long time = level.getGameTime();
        cleanup(tracker, time);
        double offsetX = (dragon.getRandom().nextDouble() - 0.5D) * 0.7D;
        double offsetZ = (dragon.getRandom().nextDouble() - 0.5D) * 0.7D;
        double radiusX = 2.2D + dragon.getRandom().nextDouble() * 1.0D;
        double radiusZ = 2.0D + dragon.getRandom().nextDouble() * 1.2D;
        int flames = 0;

        // No fixed 4x4 or completely-filled disc. Dense molten center with
        // ragged edges that change naturally as the fire stream sweeps.
        for (int dx = -3; dx <= 3; ++dx) {
            for (int dz = -3; dz <= 3; ++dz) {
                double nx = (dx - offsetX) / radiusX;
                double nz = (dz - offsetZ) / radiusZ;
                double distance = nx * nx + nz * nz;
                if (distance > 1.0D) continue;
                double coverage = Math.max(0.22D, 0.93D - distance * 0.60D);
                if (dx != 0 || dz != 0) {
                    if (dragon.getRandom().nextDouble() > coverage) continue;
                }

                BlockPos pos = exposedSurface(level, impact, dx, dz);
                if (pos == null || !withinArea(pos, markedCenter, radius)) continue;
                int gain = Math.max(3, 11 - (int) (distance * 6.0D))
                        + dragon.getRandom().nextInt(3);
                applyHeat(level, pos, tracker, time, gain);

                // Many actual flame sources, with random gaps. Most charred
                // surfaces support stable BLUE soul fire via the bundled tag.
                // Grass and flammable village surfaces use ordinary vanilla fire.
                if (flames < MAX_FLAMES_PER_IMPACT
                        && dragon.getRandom().nextDouble() < 0.78D - distance * 0.35D
                        && igniteSurface(level, pos, markedCenter, radius)) ++flames;

                // Flame stays the main visual; scorch is secondary.
                if (dragon.getRandom().nextFloat() < 0.70F) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.getX() + 0.5D, pos.getY() + 1.10D, pos.getZ() + 0.5D,
                            4, 0.37D, 0.20D, 0.37D, 0.025D);
                }
                if (dragon.getRandom().nextFloat() < 0.20F) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            pos.getX() + 0.5D, pos.getY() + 1.12D, pos.getZ() + 0.5D,
                            1, 0.24D, 0.10D, 0.24D, 0.015D);
                }
            }
        }
    }

    private static void cleanup(HeatTracker tracker, long now) {
        if (now - tracker.lastCleanup < 40 && tracker.cells.size() < MAX_HEAT_POSITIONS)
            return;
        tracker.lastCleanup = now;
        Iterator<Map.Entry<BlockPos, HeatCell>> iterator = tracker.cells.entrySet().iterator();
        while (iterator.hasNext()) {
            HeatCell sample = iterator.next().getValue();
            if (now - sample.lastContact > HEAT_EXPIRY_TICKS) iterator.remove();
        }
        // Safety cap for long sessions / several dragons: drop oldest samples
        // instead of keeping unlimited entries in server RAM.
        if (tracker.cells.size() >= MAX_HEAT_POSITIONS) {
            iterator = tracker.cells.entrySet().iterator();
            while (tracker.cells.size() >= MAX_HEAT_POSITIONS && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static boolean eligible(BlockState state) {
        return state.getBlock() instanceof SpreadingSnowyDirtBlock
                || state.is(Blocks.DIRT) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COBBLESTONE)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(AurenScorchedBlocks.GRASS.get())
                || state.is(AurenScorchedBlocks.DIRT.get())
                || state.is(AurenScorchedBlocks.GRAVEL.get())
                || state.is(AurenScorchedBlocks.STONE.get())
                || state.is(AurenScorchedBlocks.COBBLESTONE.get())
                || state.is(AurenScorchedBlocks.DIRT_PATH.get());
    }

    private static BlockPos exposedSurface(ServerLevel level, BlockPos impact,
                                           int dx, int dz) {
        // Use nearby vertical surfaces so a hit on a slope still leaves a
        // nonrectangular trace instead of silently missing most terrain.
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            BlockPos pos = impact.offset(dx, dy, dz);
            if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) != null) continue;
            BlockState state = level.getBlockState(pos);
            if (!eligible(state)) continue;
            BlockState above = level.getBlockState(pos.above());
            if (above.isAir() || above.is(Blocks.FIRE)
                    || above.is(Blocks.SOUL_FIRE)) return pos;
        }
        return null;
    }

    private static void applyHeat(ServerLevel level, BlockPos pos, HeatTracker tracker,
                                  long now, int gain) {
        BlockState original = level.getBlockState(pos);
        if (!eligible(original) || level.getBlockEntity(pos) != null) return;
        HeatCell cell = tracker.cells.get(pos);
        if (cell == null || now - cell.lastContact > HEAT_EXPIRY_TICKS) {
            cell = new HeatCell(now);
            tracker.cells.put(pos.immutable(), cell);
        } else {
            // Heat cools between passes; isolated short puffs are much less
            // destructive than holding the dragon's flame on one surface.
            int elapsed = (int) Math.max(0L, Math.min(now - cell.lastContact, 240L));
            cell.heat = Math.max(0, cell.heat - elapsed / 4);
        }
        cell.heat = Math.min(420, cell.heat + gain);
        cell.lastContact = now;

        BlockState after = null;
        if (original.is(Blocks.MAGMA_BLOCK)) {
            if (cell.heat >= MAGMA_BREAK_HEAT) after = Blocks.AIR.defaultBlockState();
        } else if (original.getBlock() instanceof SpreadingSnowyDirtBlock
                || original.is(AurenScorchedBlocks.GRASS.get())) {
            if (cell.heat >= GRASS_BREAK_HEAT) after = Blocks.AIR.defaultBlockState();
            else if (cell.heat >= SCORCH_HEAT) {
                after = AurenScorchedBlocks.GRASS.get().defaultBlockState();
            }
        } else if (original.is(BlockTags.BASE_STONE_OVERWORLD)
                || original.is(Blocks.COBBLESTONE)
                || original.is(AurenScorchedBlocks.STONE.get())
                || original.is(AurenScorchedBlocks.COBBLESTONE.get())) {
            if (cell.heat >= MAGMA_HEAT) {
                after = Blocks.MAGMA_BLOCK.defaultBlockState();
            } else if (cell.heat >= SCORCH_HEAT) {
                after = convertNaturalBlock(original);
            }
        } else if (cell.heat >= SCORCH_HEAT) {
            after = convertNaturalBlock(original);
        }

        if (after != null && !after.equals(original)) {
            level.setBlockAndUpdate(pos, after);
            if (after.isAir()) tracker.cells.remove(pos);
        }
    }

    private static boolean withinArea(BlockPos pos, BlockPos markedCenter, int radius) {
        if (markedCenter == null) return true;
        double dx = pos.getX() - markedCenter.getX();
        double dz = pos.getZ() - markedCenter.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    private static boolean igniteSurface(ServerLevel level, BlockPos pos,
                                         BlockPos markedCenter, int radius) {
        BlockPos firePos = pos.above();
        if (!level.hasChunkAt(firePos) || !withinArea(firePos, markedCenter, radius)
                || !level.isEmptyBlock(firePos)) return false;
        BlockState current = level.getBlockState(pos);
        boolean charred = current.is(AurenScorchedBlocks.GRASS.get())
                || current.is(AurenScorchedBlocks.DIRT.get())
                || current.is(AurenScorchedBlocks.GRAVEL.get())
                || current.is(AurenScorchedBlocks.STONE.get())
                || current.is(AurenScorchedBlocks.COBBLESTONE.get())
                || current.is(AurenScorchedBlocks.DIRT_PATH.get());
        BlockState fire = charred ? Blocks.SOUL_FIRE.defaultBlockState()
                : Blocks.FIRE.defaultBlockState();
        if (!fire.canSurvive(level, firePos)) return false;
        level.setBlockAndUpdate(firePos, fire);
        return true;
    }

    /**
     * Real vanilla fires on flammable structures. Remains deliberately
     * separate from the *whitelisted* heat destruction of soil, magma, stone.
     * Do not directly destroy wood, doors, loot containers or block entities.
     */
    public static void igniteVillage(ServerLevel level, BlockPos impact, AurenDragon dragon,
                                     BlockPos markedCenter, int radius) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || (markedCenter != null && (radius < 5 || radius > 30))
                || !level.hasChunkAt(impact)) return;
        int lit = 0;
        for (BlockPos wood : BlockPos.betweenClosed(
                impact.offset(-4, -3, -4), impact.offset(4, 3, 4))) {
            if (lit >= 16) break;
            if (!level.hasChunkAt(wood) || level.getBlockEntity(wood) != null)
                continue;
            if (markedCenter != null) {
                double dx = (wood.getX() + 0.5D) - (markedCenter.getX() + 0.5D);
                double dz = (wood.getZ() + 0.5D) - (markedCenter.getZ() + 0.5D);
                if (dx * dx + dz * dz > radius * radius) continue;
            }
            BlockState fuel = level.getBlockState(wood);
            boolean flammable = fuel.is(BlockTags.PLANKS)
                    || fuel.is(BlockTags.LOGS)
                    || fuel.is(BlockTags.WOODEN_STAIRS)
                    || fuel.is(BlockTags.WOODEN_SLABS)
                    || fuel.is(BlockTags.WOODEN_FENCES)
                    || fuel.is(BlockTags.WOODEN_DOORS)
                    || fuel.is(BlockTags.WOODEN_TRAPDOORS)
                    || fuel.is(BlockTags.WOOL)
                    || fuel.is(BlockTags.LEAVES)
                    || fuel.is(Blocks.HAY_BLOCK);
            if (!flammable || dragon.getRandom().nextFloat() > 0.80F) continue;
            for (Direction face : Direction.values()) {
                BlockPos firePos = wood.relative(face);
                if (!withinArea(firePos, markedCenter, radius)) continue;
                if (!level.hasChunkAt(firePos)
                        || !level.isEmptyBlock(firePos)
                        || !Blocks.FIRE.defaultBlockState().canSurvive(level, firePos))
                    continue;
                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                ++lit;
                break;
            }
        }
    }

    /**
     * The original CE whitelist: grass, dirt, gravel, cobblestone, stone,
     * dirt path. Carbonization is visual, before magma or eventual erosion.
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
