"""Regression gate for the first TRUE native-source movement-port stage.

Compare CE GroundMoveHelper logic with the Auren port. This is static
provenance/behavioral-invariant verification, not Minecraft gameplay QA.
"""
from pathlib import Path
from zipfile import ZipFile
import tomllib

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/br/com/auren/minecraft/entity"
control = (SRC / "AurenCeGroundMoveControl.java").read_text(encoding="utf-8")
entity = (SRC / "AurenDragon.java").read_text(encoding="utf-8")
flight = (SRC / "AurenFlightController.java").read_text(encoding="utf-8")
follow = (SRC / "AurenAdultFollowOwnerGoal.java").read_text(encoding="utf-8")
metadata = (ROOT / "src/main/templates/META-INF/neoforge.mods.toml").read_text()
build = (ROOT / "build.gradle").read_text()
assert "IafDragonFlightManager.GroundMoveHelper" in control
assert "aa2f11a6b79acb48a1094765d610cdaca79e3d18" in control
assert "extends MoveControl" in control
for source_fragment in (
    "this.operation == Operation.STRAFE",
    "this.operation == Operation.MOVE_TO",
    "this.operation == Operation.JUMPING",
    "new PathfindingContext(this.mob.level(), this.mob)",
    "!= PathType.WALKABLE",
    "this.strafeForwards = 1.0F;",
    "this.strafeRight = 0.0F;",
    "this.mob.setXxa(this.strafeRight);",
    "this.mob.setZza(this.strafeForwards);",
    "d3 < 2.500000277905201E-7D",
    "Mth.atan2(d1, d0) * (180D / Math.PI)",
    "this.rotlerp(this.mob.getYRot(), targetDegree, changeRange)",
    "this.speedModifier",
    "this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)",
    "d2 > (double) this.mob.maxUpStep()",
    "this.mob.getJumpControl().jump()",
    "if (this.mob.onGround()) this.operation = Operation.WAIT;",
):
    assert source_fragment in control, f"Missing original CE movement source branch: {source_fragment}"
for invariant in (
    "this.dragon.isVehicle() || this.dragon.isOnShoulder()",
    "this.dragon.getFlightMode() != AurenFlightController.GROUNDED",
    "this.dragon.isSmallForm()",
    "CE_REFERENCE_ADULT_AGE_DAYS = 50.0F",
    "CE_SMALL_TURN_DEGREES = 70.0F",
):
    assert invariant in control, f"Missing Auren-specific movement guard: {invariant}"
assert "this.moveControl = new AurenCeGroundMoveControl(this);" in entity
assert "this.savedGroundMoveControl = this.moveControl;" in entity
assert "this.moveControl = this.savedGroundMoveControl;" in entity
assert "this.moveControl = flightMoveControl;" in entity
assert "dragon.useOriginalGroundNavigation();" in flight
assert "this.dragon.getNavigation().moveTo(this.owner, WALK_SPEED)" in follow
assert "this.dragon.getFlightController().follow(this.owner)" in follow
assert 'modId="iceandfire"' not in metadata
assert "curse.maven:iceandfire" not in build
assert "curse.maven:jupiter" not in build
jars = list((ROOT / "build/libs").glob("aurenminecraft-*.jar"))
assert len(jars) == 1
with ZipFile(jars[0]) as z:
    assert z.testzip() is None
    paths = z.namelist()
    assert "br/com/auren/minecraft/entity/AurenCeGroundMoveControl.class" in paths
    assert "br/com/auren/minecraft/entity/AurenDragon.class" in paths
    assert not any(p.startswith("com/iafenvoy/iceandfire/") for p in paths)
    mods = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode())
    assert [x["modId"] for x in mods["mods"]] == ["aurenminecraft"]
print("PASS: complete CE grounded move state machine, Auren original-flight restoration and ONE dragon preserved")
