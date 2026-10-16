import br.com.auren.minecraft.entity.AurenLocalDecisionPolicy;
import br.com.auren.minecraft.entity.AurenLocalDecisionPolicy.Activity;
import br.com.auren.minecraft.entity.AurenLocalDecisionPolicy.Context;
import java.util.List;

/** Executable independent of Minecraft/NeoForge/LLM, Java 21. */
public final class LocalDecisionPolicyTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Context context(double energy, double curiosity,
                                   double vigilance, boolean night,
                                   boolean rain, boolean animal, Activity last) {
        return new Context(energy, curiosity, vigilance,
                night, rain, animal, last, 42);
    }
    public static void main(String[] args) {
        List<AurenLocalDecisionPolicy.Rated> curious =
                AurenLocalDecisionPolicy.rank(
                        context(94, 93, 6, false, false, false, null));
        require(curious.getFirst().activity() == Activity.EXPLORE,
                "high-curiosity daylight should favor exploring");
        require(curious.stream().noneMatch(x -> x.activity() == Activity.INVESTIGATE),
                "must not investigate an absent animal");
        require(curious.stream().noneMatch(x -> x.activity() == Activity.SEEK_SHELTER),
                "must not look for shelter when dry");

        List<AurenLocalDecisionPolicy.Rated> sleepy =
                AurenLocalDecisionPolicy.rank(
                        context(18, 20, 10, true, false, false, null));
        require(sleepy.getFirst().activity() == Activity.REST,
                "low energy + night should favor resting");

        List<AurenLocalDecisionPolicy.Rated> wet =
                AurenLocalDecisionPolicy.rank(
                        context(75, 70, 12, false, true, true, null));
        require(wet.getFirst().activity() == Activity.SEEK_SHELTER,
                "exposed rain should favor a safe shelter plan");

        List<AurenLocalDecisionPolicy.Rated> alert =
                AurenLocalDecisionPolicy.rank(
                        context(70, 30, 95, false, false, false, null));
        require(alert.getFirst().activity() == Activity.OBSERVE,
                "high vigilance should avoid carefree wandering");

        Context different = context(80, 80, 10, false, false, true, null);
        Context repeated = context(80, 80, 10, false, false, true,
                Activity.INVESTIGATE);
        double a = AurenLocalDecisionPolicy.score(Activity.INVESTIGATE, different);
        double b = AurenLocalDecisionPolicy.score(Activity.INVESTIGATE, repeated);
        require(Math.abs(a - b - 19.0D) < 0.0001D,
                "same-choice penalty prevents robotic endless repetition");

        List<AurenLocalDecisionPolicy.Rated> ranked =
                AurenLocalDecisionPolicy.rank(different);
        require(ranked.size() == 5, "only available options may be ranked");
        require(AurenLocalDecisionPolicy.score(Activity.APPROACH_OWNER, different)
                        > AurenLocalDecisionPolicy.score(Activity.OBSERVE, different),
                "energized daytime companion must consider actually approaching owner");
        Context tiredDay = context(35, 35, 12, false, false, false, null);
        require(AurenLocalDecisionPolicy.score(Activity.REST, tiredDay)
                        > AurenLocalDecisionPolicy.score(Activity.OBSERVE, tiredDay),
                "daytime rest must be a meaningful visible alternative");
        for (int i = 1; i < ranked.size(); i++)
            require(ranked.get(i-1).utility() >= ranked.get(i).utility(),
                    "descending ranking must be stable");

        List<AurenLocalDecisionPolicy.Rated> invalid =
                AurenLocalDecisionPolicy.rank(
                        context(Double.NaN, Double.POSITIVE_INFINITY,
                                -50, false, false, false, null));
        require(!invalid.isEmpty()
                        && invalid.stream().allMatch(v -> Double.isFinite(v.utility())),
                "corrupt preferences must not propagate NaN/Infinity");
        System.out.println("PASS: offline intent scores, rain/night, threat, variation and invalid-value handling");
    }
}
