package br.com.auren.minecraft.entity;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-only, bounded perception. Sensors report world facts; they DO NOT
 * choose targets, move the dragon, destroy blocks or generate AI dialogue.
 * A future desktop Auren bridge can consume the immutable Snapshot contract.
 */
public final class AurenWorldPerception {
    private static final int SCAN_INTERVAL = 40;  // 2 seconds at 20 TPS
    private static final int ENTITY_RADIUS = 24;
    private static final int MAX_ENTITIES = 96;
    private static final int MAX_HISTORY = 8;
    private static final int HAZARD_RADIUS = 5;

    public record Snapshot(long tick, String biome, String environment,
                           String weather, String timeOfDay, int monsters,
                           int animals, int otherPlayers, String nearestMonster,
                           int nearestMonsterBlocks, boolean dangerNearOwner,
                           boolean lowOwnerHealth, boolean fireNearby,
                           boolean ownerBurning, boolean lavaNearby, boolean villageClue) {
        public String summary() {
            if (dangerNearOwner || lowOwnerHealth || ownerBurning || lavaNearby) {
                String problem = lowOwnerHealth ? "dono ferido"
                        : dangerNearOwner ? "criaturas perigosas perto do dono"
                        : ownerBurning ? "dono pegando fogo" : "lava próxima";
                return "AUREN • ALERTA: " + problem;
            }
            return "AUREN • " + environment + " • " + monsters
                    + (monsters == 1 ? " monstro" : " monstros") + " por perto";
        }

        public String detail() {
            StringBuilder out = new StringBuilder("Ambiente: ").append(environment)
                    .append(" | bioma: ").append(biome)
                    .append(" | ").append(timeOfDay)
                    .append(" | ").append(weather)
                    .append(". Criaturas: ").append(monsters).append(" monstros, ")
                    .append(animals).append(" animais, ")
                    .append(otherPlayers).append(" outros jogadores.");
            if (nearestMonster != null) out.append(" Mais próximo: ")
                    .append(nearestMonster).append(" a aprox. ")
                    .append(nearestMonsterBlocks).append(" blocos do dono.");
            if (lowOwnerHealth) out.append(" Dono com pouca vida.");
            if (dangerNearOwner) out.append(" Monstro perto do dono.");
            if (lavaNearby) out.append(" Lava próxima.");
            if (fireNearby) out.append(" Chamas detectadas na região.");
            if (ownerBurning) out.append(" Dono está pegando fogo.");
            if (villageClue) out.append(" Sino detectado nas proximidades.");
            return out.toString();
        }
    }

    private final AurenDragon dragon;
    private final ArrayDeque<String> recentChanges = new ArrayDeque<>();
    private Snapshot latest;
    private String lastAreaKey;
    private String lastDangerKey;
    private int lastScanTick = Integer.MIN_VALUE;

    AurenWorldPerception(AurenDragon dragon) { this.dragon = dragon; }

    public Snapshot latest() { return latest; }

    public String brief() {
        return latest == null ? "AUREN • observando o ambiente" : latest.summary();
    }

    public int alertLevel() {
        if (latest == null) return 0;
        if (latest.lowOwnerHealth() || latest.dangerNearOwner()
                || latest.ownerBurning()) return 2;
        // The dragon's own flames are observations, NOT an alert. Warn only
        // if the OWNER actually catches fire or is at risk near lava.
        return latest.lavaNearby() ? 1 : 0;
    }

    public String describe() {
        return latest == null ? "Ainda não há percepção disponível."
                : latest.detail();
    }

    public String recentEvents() {
        return recentChanges.isEmpty() ? "Nenhuma mudança importante registrada."
                : String.join(" | ", recentChanges);
    }

    /** No scans in client render, each server tick or unloaded chunks. */
    public void tick(ServerLevel level) {
        if (!dragon.isTame() || !(dragon.getOwner() instanceof ServerPlayer owner)
                || !owner.isAlive() || owner.level() != level
                || !dragon.isAlive()) return;
        if (lastScanTick != Integer.MIN_VALUE
                && dragon.tickCount - lastScanTick < SCAN_INTERVAL) return;
        lastScanTick = dragon.tickCount;
        scan(level, owner);
    }

    /** The existing owner-only command can request up-to-date information. */
    public void refresh(ServerLevel level, ServerPlayer requester) {
        if (dragon.isOwnedBy(requester) && requester.level() == level
                && (lastScanTick == Integer.MIN_VALUE
                || dragon.tickCount - lastScanTick >= SCAN_INTERVAL)) {
            lastScanTick = dragon.tickCount;
            scan(level, requester);
        }
    }

    private void scan(ServerLevel level, ServerPlayer owner) {
        BlockPos ownerPos = owner.blockPosition();
        if (!level.hasChunkAt(ownerPos)
                || !level.hasChunkAt(dragon.blockPosition())) return;

        String biome = level.getBiome(ownerPos).unwrapKey()
                .map(key -> key.location().getPath().replace('_', ' '))
                .orElse("desconhecido");
        boolean sky = level.canSeeSky(ownerPos.above());
        // The topmost surface, not the block two blocks above the player's
        // head: natural caves often have HIGH ceilings of air underneath rock.
        int surfaceY = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ownerPos).getY();
        int roofDepth = surfaceY - ownerPos.getY();
        String environment = sky ? "a céu aberto"
                : roofDepth >= 7 ? "caverna" : "interior/abrigo";
        String weather = level.isThundering() ? "tempestade"
                : level.isRainingAt(ownerPos) ? "chuva" : "tempo limpo";
        long daytime = level.getDayTime() % 24000L;
        String time = daytime >= 12500L && daytime < 23000L ? "noite" : "dia";

        int monsters = 0, animals = 0, players = 0;
        double nearestSq = Double.POSITIVE_INFINITY;
        String nearestName = null;
        boolean danger = false;
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().inflate(ENTITY_RADIUS),
                living -> living.isAlive() && living != owner && living != dragon
                        && owner.distanceToSqr(living)
                           <= ENTITY_RADIUS * ENTITY_RADIUS);
        // A source-list iteration cap alone can silently ignore a zombie
        // standing next to the owner when dozens of distant mobs are loaded.
        // Always prioritize the nearest actual spherical-radius entities.
        nearby.sort(Comparator.comparingDouble(owner::distanceToSqr));
        int seen = 0;
        for (LivingEntity entity : nearby) {
            if (seen++ >= MAX_ENTITIES) break;
            if (entity instanceof Monster && !dragon.isAlliedTo(entity)) {
                ++monsters;
                double sq = owner.distanceToSqr(entity);
                if (sq < nearestSq) {
                    nearestSq = sq;
                    nearestName = entity.getType().getDescription().getString();
                    if (nearestName.length() > 40) nearestName = nearestName.substring(0, 40);
                }
                if (sq < 10.0D * 10.0D) danger = true;
            } else if (entity instanceof Animal) ++animals;
            else if (entity instanceof Player) ++players;
        }
        boolean lowHealth = owner.getHealth() <= owner.getMaxHealth() * 0.35F;
        boolean nearbyFire = owner.isOnFire();
        boolean nearbyLava = owner.isInLava();
        boolean bell = false;

        // Previously dx/dz started at -5 and advanced by 2: offset (0,0)
        // was NEVER checked! Exhaustively scan a bounded five-block disc,
        // including the owner's own column, odd/even offsets and floor.
        // At most ~405 block checks per two-second scan per Auren.
        for (int dx = -HAZARD_RADIUS; dx <= HAZARD_RADIUS; ++dx) {
            for (int dz = -HAZARD_RADIUS; dz <= HAZARD_RADIUS; ++dz) {
                if (dx * dx + dz * dz > HAZARD_RADIUS * HAZARD_RADIUS) continue;
                for (int dy = -2; dy <= 2; ++dy) {
                    BlockPos pos = ownerPos.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.LAVA)) nearbyLava = true;
                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE))
                        nearbyFire = true;
                    if (state.is(Blocks.BELL)) bell = true;
                }
            }
        }
        Snapshot observed = new Snapshot(level.getGameTime(), biome, environment,
                weather, time, monsters, animals, players, nearestName,
                nearestName == null ? -1 : (int) Math.round(Math.sqrt(nearestSq)),
                danger, lowHealth, nearbyFire, owner.isOnFire(), nearbyLava, bell);
        String areaKey = biome + "/" + environment + "/" + weather + "/" + time;
        String dangerKey = danger + "/" + lowHealth + "/" + owner.isOnFire()
                + "/" + nearbyLava;
        if (latest != null) {
            if (!Objects.equals(lastAreaKey, areaKey))
                remember("Ambiente mudou: " + environment + " em " + biome);
            if (!Objects.equals(lastDangerKey, dangerKey))
                remember("Risco alterado: " + observed.summary());
            if (latest.villageClue() != bell)
                remember(bell ? "Sino de vila detectado." : "Sino de vila fora do alcance.");
            // Report notable hostile arrivals/departures once, rather than
            // spamming every entity-count change in crowded environments.
            if ((latest.monsters() == 0) != (monsters == 0))
                remember(monsters == 0 ? "Monstros se afastaram."
                        : "Monstros detectados nas proximidades.");
        }
        lastAreaKey = areaKey;
        lastDangerKey = dangerKey;
        latest = observed;
    }

    private void remember(String event) {
        if (recentChanges.size() == MAX_HISTORY) recentChanges.removeFirst();
        recentChanges.addLast(event);
    }
}
