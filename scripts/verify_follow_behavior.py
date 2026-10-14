"""Static regression tests for the baby's vanilla and adult's custom follow rules.

Run with the existing GitHub CI. In-game pathing/teleporting must still be tested.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenDragon.java"
ADULT = ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenAdultFollowOwnerGoal.java"
PROPS = ROOT / "gradle.properties"


def verify():
    entity = ENTITY.read_text(encoding="utf-8")
    adult = ADULT.read_text(encoding="utf-8")
    props = PROPS.read_text(encoding="utf-8")

    assert "new FollowOwnerGoal(this, 1.15D, 4.5F, 1.7F)" in entity, "baby settings changed"
    assert "return AurenDragon.this.isSmallForm()" in entity, "baby gating missing"
    assert "&& !AurenDragon.this.isOnShoulder()" in entity, "shoulder gating missing"
    assert "new AurenAdultFollowOwnerGoal(this)" in entity, "adult goal not registered"
    assert "START_DISTANCE = 15.0D" in adult
    assert "STOP_DISTANCE = 8.0D" in adult
    assert "EMERGENCY_TELEPORT_DISTANCE = 50.0D" in adult
    assert ">= EMERGENCY_TELEPORT_DISTANCE * EMERGENCY_TELEPORT_DISTANCE" in adult
    assert "!this.dragon.isSmallForm()" in adult
    assert "!this.dragon.isOnShoulder()" in adult
    assert "this.navigation.moveTo(this.owner, WALK_SPEED)" in adult
    for safety in ("level.hasChunkAt(candidate)", "Direction.UP",
                   "level.noCollision(this.dragon, destinationBox)",
                   "level.getFluidState(candidate).isEmpty()"):
        assert safety in adult, f"missing teleport safety: {safety}"
    chunkloader = (ROOT / "src/main/java/br/com/auren/minecraft/entity/AurenAdultChunkLoader.java").read_text(encoding="utf-8")
    assert "new AurenAdultChunkLoader()" in entity
    assert "this.adultChunkLoader.tick(this, serverLevel)" in entity
    assert "this.adultChunkLoader.release(this, serverLevel)" in entity
    assert "TicketType.create" in chunkloader
    assert "TICKET_TIMEOUT_TICKS = 100" in chunkloader
    assert "TICKET_REFRESH_TICKS = 20" in chunkloader
    assert "addRegionTicket" in chunkloader
    assert "removeRegionTicket" in chunkloader
    assert "!dragon.isSmallForm()" in chunkloader
    assert "owner.level() == level" in chunkloader
    assert "mod_version=0.5.5-alpha" in props
    print("PASS: baby follows at 4.5/1.7; adult at 15/8; teleports only at 50+ with safe landing")


if __name__ == "__main__":
    verify()
