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

    /** Only successful actions update the recent-choice bias. */
    public void accepted(AurenLocalDecisionPolicy.Activity activity) {
        if (activity == null) return;
        previous = activity;
        ++localDecisions;
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
