"""Prove original IceAndFire CE source is COMPLETE and pinned, not copied fragments."""
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
CE = ROOT / "vendor" / "iceandfire-ce"
SHA = "aa2f11a6b79acb48a1094765d610cdaca79e3d18"
NEEDED = (
  "LICENSE", "build.gradle", "gradle.properties",
  "src/main/java/com/iafenvoy/iceandfire/entity/DragonBaseEntity.java",
  "src/main/java/com/iafenvoy/iceandfire/entity/FireDragonEntity.java",
  "src/main/java/com/iafenvoy/iceandfire/entity/IceDragonEntity.java",
  "src/main/java/com/iafenvoy/iceandfire/entity/LightningDragonEntity.java",
  "src/main/java/com/iafenvoy/iceandfire/entity/util/dragon/IafDragonFlightManager.java",
  "src/main/java/com/iafenvoy/iceandfire/entity/util/dragon/IafDragonLogic.java",
  "src/main/java/com/iafenvoy/iceandfire/entity/util/dragon/IafDragonDestructionManager.java",
  "src/main/java/com/iafenvoy/iceandfire/entity/ai/DragonAIEscortGoal.java",
  "src/main/java/com/iafenvoy/iceandfire/render/entity/LightningDragonEntityRenderer.java",
  "src/main/java/com/iafenvoy/iceandfire/render/model/animator/LightningTabulaDragonAnimator.java",
  "src/main/java/com/iafenvoy/iceandfire/network/payload/DragonControlC2SPayload.java",
  "src/main/resources/assets/iceandfire/models/tabula/lightningdragon/lightningdragon_ground.tbl"
)
def git(directory, *args):
    return subprocess.check_output(["git", "-C", str(directory), *args], encoding="utf8").strip()
def main():
    modules = (ROOT/".gitmodules").read_text(encoding="utf8")
    assert "https://github.com/IAFEnvoy/IceAndFire-CE.git" in modules
    assert "path = vendor/iceandfire-ce" in modules
    assert f"160000 commit {SHA}" in git(ROOT, "ls-tree", "HEAD", "vendor/iceandfire-ce")
    assert (CE / ".git").exists(), "Missing submodule: git submodule update --init --recursive"
    assert git(CE, "rev-parse", "HEAD") == SHA, "Unreviewed CE source revision"
    assert not git(CE, "status", "--porcelain", "--untracked-files=no"), "Original CE source was modified"
    missing = [name for name in NEEDED if not (CE / name).is_file()]
    assert not missing, f"Incomplete native dragon source: {missing}"
    gp = (CE / "gradle.properties").read_text(encoding="utf8")
    for expected in ("minecraft_version=1.21.1","neo_version=21.1.248","mod_version=2.1.4"):
        assert expected in gp, f"Unexpected upstream version: {expected}"
    dragon = (CE/"src/main/java/com/iafenvoy/iceandfire/entity/DragonBaseEntity.java").read_text()
    for symbol in ("void travel(", "void updateRider(", "void updatePitch(", "void positionRider(", "void breathAttack("):
        assert symbol in dragon, f"Missing original behavior: {symbol}"
    java_count = len(list((CE/"src/main/java").rglob("*.java")))
    assert java_count > 500, f"Only {java_count} Java files, expected full CE code"
    print(f"PASS: full native CE source, SHA {SHA}, {java_count} original Java files, {len(NEEDED)} key assets")
if __name__ == "__main__":
    main()
