/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package br.com.auren.minecraft.entity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Small, bounded topological search THROUGH the cave's open floor cells.
 *
 * A point "near" the enemy may be separated from it by an entire mountain.
 * Discover the cave mouth from the prey's accessible air corridors FIRST,
 * and only then ask vanilla ground navigation to walk to that entrance.
 * No direct-line steering, chunk tickets, world mutations or teleporting.
 */
final class AurenCaveRoutePlanner {
    private static final int MAX_EXPLORED = 1600;
    private static final int MAX_HORIZONTAL = 28;
    private static final int MAX_VERTICAL = 10;
    // Exact small-form dimensions from AurenDragon.getDimensions():
    // 0.50 blocks wide, 0.70 blocks high. Keep the entrance search honest.
    private static final double SMALL_HALF_WIDTH = 0.25D;
    private static final double SMALL_HEIGHT = 0.70D;

    private AurenCaveRoutePlanner() {}

    private static boolean walkable(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)
                || !level.hasChunkAt(position.above())
                || !level.hasChunkAt(position.below())) return false;
        BlockState feet = level.getBlockState(position);
        BlockState floor = level.getBlockState(position.below());
        // One-block-high tunnels ARE traversable to Auren's 0.7m child.
        // Do not require two air blocks above the floor: that would reject
        // the very caves the tiny shape is intended to access.
        if (!feet.isAir() || !floor.getFluidState().isEmpty()
                || !feet.getFluidState().isEmpty()
                || floor.getCollisionShape(level, position.below()).isEmpty())
            return false;
        // Air alone is insufficient: a wall, fence, slab or adjacent
        // collision shape may still block the real small dragon hitbox.
        double cx = position.getX() + 0.5D;
        double cz = position.getZ() + 0.5D;
        AABB childBox = new AABB(cx - SMALL_HALF_WIDTH, position.getY(),
                cz - SMALL_HALF_WIDTH, cx + SMALL_HALF_WIDTH,
                position.getY() + SMALL_HEIGHT, cz + SMALL_HALF_WIDTH);
        return level.noCollision(null, childBox);
    }

    /**
     * Find sky access by walking FLOORS from the prey position outward,
     * including one-block step-ups/downs but never through solid rock.
     * The search is intentionally bounded for Android and multiplayer.
     */
    static BlockPos findEntrance(ServerLevel level, LivingEntity prey) {
        BlockPos origin = prey.blockPosition();
        if (!level.hasChunkAt(origin)) return null;

        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos start = origin;
        if (!walkable(level, start)) {
            if (walkable(level, origin.above())) start = origin.above();
            else return null;
        }
        frontier.add(start);
        visited.add(start.immutable());
        int expanded = 0;
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        while (!frontier.isEmpty() && expanded++ < MAX_EXPLORED) {
            BlockPos pos = frontier.removeFirst();
            if (level.canSeeSky(pos) && pos.distSqr(origin) > 6.0D) {
                // Prefer nearby, low-altitude ground openings, not a distant
                // summit at the top of the mountain.
                double score = pos.distSqr(origin)
                        + Math.abs(pos.getY() - origin.getY()) * 12.0D;
                if (score < bestDistance) {
                    bestDistance = score;
                    best = pos.immutable();
                }
                // Continue exploring briefly for a better, closer exit.
                if (expanded > 280 && best != null) break;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos step = pos.relative(direction);
                for (int dy : new int[] {0, 1, -1}) {
                    BlockPos next = step.offset(0, dy, 0);
                    if (Math.abs(next.getY() - origin.getY()) > MAX_VERTICAL
                            || Math.abs(next.getX() - origin.getX()) > MAX_HORIZONTAL
                            || Math.abs(next.getZ() - origin.getZ()) > MAX_HORIZONTAL
                            || visited.contains(next) || !walkable(level, next))
                        continue;
                    visited.add(next.immutable());
                    frontier.addLast(next.immutable());
                }
            }
        }
        return best;
    }
}
