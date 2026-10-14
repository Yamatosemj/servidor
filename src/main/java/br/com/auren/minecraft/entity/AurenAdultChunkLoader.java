package br.com.auren.minecraft.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.UUID;

/**
 * Keeps only the adult Auren's CURRENT chunk entity-ticking while its owner
 * is online in the same dimension, allowing its emergency 50-block teleport
 * goal to keep running even after the player leaves simulation distance.
 *
 * The ticket is temporary (100 ticks), never persisted to world data. It is
 * removed when the adult changes chunk/form, sits, dies, or loses its owner.
 * Every 20 ticks it is explicitly renewed by remove/re-add; this is reliable
 * even if ticket de-duplication does not update its expiry timestamp.
 *
 * Important: an Auren that was ALREADY unloaded before this version needs to
 * have its chunk loaded once so it can acquire the first ticket. No global
 * "summon from cold unloaded chunk" registry is introduced here.
 */
final class AurenAdultChunkLoader {
    static final int TICKET_TIMEOUT_TICKS = 100;
    static final int TICKET_REFRESH_TICKS = 20;
    // Region ticket radius 2: center chunk is entity-ticking, without
    // permanently force-loading the world with /forceload.
    static final int TICKET_DISTANCE = 2;

    private static final TicketType<UUID> ADULT_FOLLOW_TICKET =
            TicketType.create("aurenminecraft:adult_follow",
                    Comparator.comparing(UUID::toString), TICKET_TIMEOUT_TICKS);

    private ChunkPos ticketedChunk;
    private int refreshCountdown;

    void tick(AurenDragon dragon, ServerLevel level) {
        UUID ownerId = dragon.getOwnerUUID();
        ServerPlayer owner = ownerId == null ? null :
                level.getServer().getPlayerList().getPlayer(ownerId);

        boolean shouldStayActive = dragon.isAlive()
                && dragon.isTame()
                && !dragon.isSmallForm()
                && !dragon.isOnShoulder()
                && !dragon.isOrderedToSit()
                && owner != null
                && owner.isAlive()
                && !owner.isSpectator()
                && owner.level() == level;

        if (!shouldStayActive) {
            this.release(dragon, level);
            return;
        }

        ChunkPos current = dragon.chunkPosition();
        if (this.ticketedChunk != null && !this.ticketedChunk.equals(current)) {
            this.release(dragon, level);
        }

        if (this.ticketedChunk == null || --this.refreshCountdown <= 0) {
            if (this.ticketedChunk != null) {
                level.getChunkSource().removeRegionTicket(ADULT_FOLLOW_TICKET,
                        current, TICKET_DISTANCE, dragon.getUUID(), true);
            }
            level.getChunkSource().addRegionTicket(ADULT_FOLLOW_TICKET,
                    current, TICKET_DISTANCE, dragon.getUUID(), true);
            this.ticketedChunk = current;
            this.refreshCountdown = TICKET_REFRESH_TICKS;
        }
    }

    void release(AurenDragon dragon, ServerLevel level) {
        if (this.ticketedChunk == null) return;
        level.getChunkSource().removeRegionTicket(ADULT_FOLLOW_TICKET,
                this.ticketedChunk, TICKET_DISTANCE, dragon.getUUID(), true);
        this.ticketedChunk = null;
        this.refreshCountdown = 0;
    }
}
