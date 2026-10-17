"""0.7.26 offline-first local mind integration (static + packaged JAR).
Ranking behavior is tested separately by a real Java test, not simulated here.
"""
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
j=root/"src/main/java/br/com/auren/minecraft"
policy=(j/"entity/AurenLocalDecisionPolicy.java").read_text()
mind=(j/"entity/AurenLocalMind.java").read_text()
idle=(j/"entity/AurenNaturalIdleGoal.java").read_text()
dragon=(j/"entity/AurenDragon.java").read_text()
commands=(j/"AurenCommands.java").read_text()
for token in (
  "enum Activity { OBSERVE, EXPLORE, INVESTIGATE, REST, SEEK_SHELTER, APPROACH_OWNER }",
  "public static List<Rated> rank(Context context)",
  "public static double score(Activity activity, Context context)",
  "Double.NEGATIVE_INFINITY",
  "if (context.previous() == activity) base -= 19.0D;",
  "List.copyOf(options)",
  "case APPROACH_OWNER ->",
  "case REST -> 25.0D",
): assert token in policy, token
for token in (
  "public void tickServer()",
  "dragon.tickCount % UPDATE_INTERVAL != 0",
  "dragon.getOwner() instanceof ServerPlayer owner",
  "dragon.getAwareness().latest()",
  "public List<AurenLocalDecisionPolicy.Rated> rankIdle(",
  "public void accepted(AurenLocalDecisionPolicy.Activity activity)",
  "void save(CompoundTag tag)",
  "void load(CompoundTag tag)",
  'tag.putFloat(ENERGY, energy)',
  'tag.putFloat(CURIOSITY, curiosity)',
  'tag.putFloat(VIGILANCE, vigilance)',
  'tag.contains(PREVIOUS)',
  'Float.isFinite(value)',
): assert token in mind, token
for token in (
  "dragon.getMind().rankIdle(",
  "dragon.getMind().accepted(rated.activity())",
  "case SEEK_SHELTER ->",
  "case REST ->",
  "case INVESTIGATE ->",
  "case EXPLORE ->",
  "case OBSERVE ->",
  "if (cover != null && cover.canReach()",
  "if (shortRoute != null && shortRoute.canReach()",
  "dragon.getFlightMode() == AurenFlightController.GROUNDED",
): assert token in idle,token
for token in (
  "private final AurenLocalMind mind = new AurenLocalMind(this)",
  "public AurenLocalMind getMind()",
  "this.mind.tickServer()",
  "this.mind.save(tag)",
  "this.mind.load(tag)",
): assert token in dragon,token
for token in ('Commands.literal("mente")',
              "AurenCommands::mind", "dragon.getMind().describe()",
              "AurenDragon dragon = getDragon(context)"):
    assert token in commands,token
for forbidden in ("java.net.", "HttpClient", "DeepSeek", "markEnemy(",
                  "setTarget(", "setBlock(", "getFlightController()."):
    assert forbidden not in mind,forbidden
assert "markEnemy(" not in policy and "getFlightController()" not in policy
assert "mod_version=0.7.29-unified-ui-alpha" in (
    root/"gradle.properties").read_text()
jars=list((root/"build/libs").glob("aurenminecraft-*.jar"))
assert len(jars)==1,jars
with ZipFile(jars[0]) as jar:
    assert jar.testzip() is None
    for name in ("AurenLocalMind","AurenLocalDecisionPolicy",
                 "AurenLocalDecisionPolicy$Activity",
                 "AurenLocalDecisionPolicy$Context",
                 "AurenNaturalIdleGoal","AurenDragon"):
        assert f"br/com/auren/minecraft/entity/{name}.class" in jar.namelist(),name
print("PASS: offline local mind, owner-bound persistence and safe routine selection")
