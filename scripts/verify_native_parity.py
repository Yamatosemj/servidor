"""Static invariant for native CE baseline (not a substitute for game testing)."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
commands = (root / "src/main/java/br/com/auren/minecraft/AurenCommands.java").read_text()
gradle = (root / "build.gradle").read_text()
meta = (root / "src/main/templates/META-INF/neoforge.mods.toml").read_text()
version = (root / "gradle.properties").read_text()
assert 'IafEntities.LIGHTNING_DRAGON.get().create(level)' in commands
assert 'IafEntities.FIRE_DRAGON.get().create(level)' in commands
assert 'dragon.tame(player)' in commands
assert 'dragon.setAgeInDays(50)' in commands
assert 'dragon.setCommand(2)' in commands
assert 'dragon.setVariant(lightning ? "black" : "red")' in commands
assert 'Commands.literal("original")' in commands
assert 'Commands.literal("original_fogo")' in commands
assert '.requires(source -> source.hasPermission(2))' in commands
assert 'if (!level.noCollision(dragon, dragon.getBoundingBox()))' in commands
assert 'compileOnly \'curse.maven:iceandfire-ce-1040076:9128508\'' in gradle
assert 'modId="iceandfire"' in meta
assert 'type="required"' in meta
assert 'mod_version=0.7.0-engine-bootstrap' in version
print("PASS: native CE parity baseline uses original registered dragon entities (not copied movement)")
