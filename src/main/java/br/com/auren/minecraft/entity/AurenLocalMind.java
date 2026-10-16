package br.com.auren.minecraft.entity;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persistent, local-only simulated tendencies. Deliberately NOT a replacement
 * for Minecraft GoalSelector, server-validated owner orders or CE flight AI.
 * The first integration is exclusively the peaceful routine's selector.
 */
public final class AurenLocalMind {
    private static final String ENERGY = "AurenMindEnergy";
    private static final String CURIOSITY = "AurenMindCuriosity";
    private static final String VIGILANCE = "AurenMindVigilance";
    private static final String PREVIOUS = "AurenMindPrevious";
    private static final int UPDATE_INTERVAL = 20;

    private final AurenDragon dragon;
    private float energy = 75.0F;
    private float curiosity = 45.0F;
    private float vigilance = 12.0F;
    private AurenLocalDecisionPolicy.Activity previous;
    private int localDecisions;
    // Live server-only diagnostics. Do not NBT-save transient Goals or
    // last path failures; saved preferences are already preserved separately.
    private int idleStarts;
    private int failedIdlePaths;
    private int stalledIdleRoutes;
    private String lastRouteResult = "nenhum";
    private String idleStatus = "aguardando oportunidade";
    private String lastBlocked = "nenhum";
    private String lastCompleted = "nenhuma";

    public AurenLocalMind(AurenDragon dragon) { this.dragon = dragon; }

    /**
     * Read-only observation; one cheap update per Minecraft second, only
     * while the owner is online and in the dragon's dimension.
     */
    public void tickServer() {
        if (dragon.level().isClientSide() || dragon.tickCount % UPDATE_INTERVAL != 0
                || !dragon.isTame()
                || !(dragon.getOwner() instanceof ServerPlayer owner)
                || !owner.isAlive() || owner.level() != dragon.level()) return;

        boolean resting = dragon.getNaturalRestPose() != 0;
        if (resting) {
            energy = clamp(energy + (dragon.getNaturalRestPose() == 2 ? 0.70F : 0.43F));
            curiosity = clamp(curiosity + 0.08F);
        } else {
            boolean active = dragon.getDeltaMovement().horizontalDistanceSqr() > 0.015D
                    || dragon.getFlightMode() != AurenFlightController.GROUNDED;
            energy = clamp(energy + (active ? -0.21F : 0.03F));
            curiosity = clamp(curiosity + (active ? 0.08F : 0.24F));
        }

        AurenWorldPerception.Snapshot snapshot = dragon.getAwareness().latest();
        if (snapshot != null && (snapshot.dangerNearOwner()
                || snapshot.lowOwnerHealth() || snapshot.ownerBurning())) {
            vigilance = clamp(vigilance + 4.0F);
        } else {
            vigilance = clamp(vigilance - 0.37F);
        }
    }

    public List<AurenLocalDecisionPolicy.Rated> rankIdle(
            boolean night, boolean rainExposed, boolean passiveAnimalNearby) {
        AurenLocalDecisionPolicy.Context context = new AurenLocalDecisionPolicy.Context(
                energy, curiosity, vigilance, night, rainExposed,
                passiveAnimalNearby, previous, dragon.getRandom().nextInt(10000));
        return AurenLocalDecisionPolicy.rank(context);
    }

    /** The owner can diagnose a real idle rather than infer it from energy. */
    public void idleUnready(String reason) {
        if (reason != null && !reason.isBlank()) lastBlocked = reason;
        if (!idleStatus.startsWith("executando"))
            idleStatus = "aguardando: " + lastBlocked;
    }

    public void idlePathFailed() {
        failedIdlePaths = Math.min(99999, failedIdlePaths + 1);
    }

    /** Distinguish A* accepted from actual ground movement. */
    public void idleRouteEnded(String route, double progressBlocks) {
        boolean advanced = Double.isFinite(progressBlocks) && progressBlocks >= 0.75D;
        if (!advanced) stalledIdleRoutes = Math.min(99999, stalledIdleRoutes + 1);
        lastRouteResult = route + (advanced ? " avancou " : " sem avancar ") 
                + (Double.isFinite(progressBlocks)
                    ? Math.round(progressBlocks * 10.0D) / 10.0D : 0.0D)
                + " blocos";
        // The Goal remains active to observe, but is no longer navigating.
        idleStatus = "executando: observando (rota encerrada)";
    }

    public void idleFinished(String summary) {
        lastCompleted = summary == null ? "finalizada" : summary;
        idleStatus = "aguardando oportunidade";
    }

    /** Only successful actions update the recent-choice bias. */
    public void accepted(AurenLocalDecisionPolicy.Activity activity) {
        if (activity == null) return;
        previous = activity;
        ++localDecisions;
        ++idleStarts;
        lastBlocked = "nenhum"; // Do not show a stale cooldown while a routine is active.
        idleStatus = "executando: " + switch (activity) {
            case OBSERVE -> "observando";
            case EXPLORE -> "explorando";
            case INVESTIGATE -> "investigando animal";
            case REST -> "descansando";
            case SEEK_SHELTER -> "procurando abrigo";
            case APPROACH_OWNER -> "aproximando do dono";
        };
        if (activity == AurenLocalDecisionPolicy.Activity.EXPLORE
                || activity == AurenLocalDecisionPolicy.Activity.INVESTIGATE)
            curiosity = clamp(curiosity - 8.0F);
        else if (activity == AurenLocalDecisionPolicy.Activity.REST)
            energy = clamp(energy + 1.0F);
    }

    public String describe() {
        return "Mente local: energia " + Math.round(energy)
                + "/100, curiosidade " + Math.round(curiosity)
                + "/100, alerta " + Math.round(vigilance)
                + "/100 | ultima escolha: " + (previous == null ? "nenhuma"
                    : previous.name().toLowerCase(java.util.Locale.ROOT))
                + " | rotina: " + idleStatus
                + " | iniciadas: " + idleStarts
                + ", caminhos inviaveis: " + failedIdlePaths
                + ", rotas sem avanco: " + stalledIdleRoutes
                + " | ultimo trajeto: " + lastRouteResult
                + " | ultima interrupcao: " + lastCompleted
                + " | limite: " + lastBlocked
                + " | IA externa: nao necessaria";
    }

    public void save(CompoundTag tag) {
        tag.putFloat(ENERGY, energy);
        tag.putFloat(CURIOSITY, curiosity);
        tag.putFloat(VIGILANCE, vigilance);
        if (previous != null) tag.putString(PREVIOUS, previous.name());
    }

    public void load(CompoundTag tag) {
        if (tag.contains(ENERGY)) energy = clamp(tag.getFloat(ENERGY));
        if (tag.contains(CURIOSITY)) curiosity = clamp(tag.getFloat(CURIOSITY));
        if (tag.contains(VIGILANCE)) vigilance = clamp(tag.getFloat(VIGILANCE));
        previous = null;
        if (tag.contains(PREVIOUS)) {
            try {
                previous = AurenLocalDecisionPolicy.Activity.valueOf(tag.getString(PREVIOUS));
            } catch (IllegalArgumentException ignored) {
                // Legacy/corrupted/unknown future value is safely ignored.
            }
        }
    }

    public int decisionsThisSession() { return localDecisions; }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) return 50.0F;
        return Math.max(0.0F, Math.min(100.0F, value));
    }
}
