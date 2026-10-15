"""Strict build gate: one Auren entity, zero whole-IceAndFire runtime content.

We may copy selected Tabula models and derive small portions of behavior
under LGPL. That is NOT the same as bundling/launching the IceAndFire mod.
"""
from pathlib import Path
from zipfile import ZipFile
import tomllib

root = Path(__file__).resolve().parents[1]
java = root / "src/main/java/br/com/auren/minecraft"
registration = (java / "AurenMinecraft.java").read_text()
commands = (java / "AurenCommands.java").read_text()
build = (root / "build.gradle").read_text()
metadata_template = (root / "src/main/templates/META-INF/neoforge.mods.toml").read_text()
assert registration.count('ENTITIES.register("auren"') == 1
assert registration.count("ENTITIES.register(") == 1, "Only Auren should register an entity"
assert "DeferredRegister.Items" not in registration, "IceAndFire item registry forbidden"
assert "IafEntities" not in commands and "spawnOriginal" not in commands
assert 'Commands.literal("original")' not in commands
assert 'Commands.literal("original_fogo")' not in commands
assert 'modId="iceandfire"' not in metadata_template, "Full IceAndFire mod dependency forbidden"
assert "jupiter" not in metadata_template.lower(), "Jupiter only needed for the full CE runtime"
assert "curse.maven:iceandfire" not in build.lower()
assert "curse.maven:jupiter" not in build.lower()
jars = list((root / "build/libs").glob("aurenminecraft-*.jar"))
assert len(jars) == 1, f"Expected one Auren JAR: {jars}"
with ZipFile(jars[0]) as jar:
    names = set(jar.namelist())
    assert jar.testzip() is None
    assert "br/com/auren/minecraft/entity/AurenDragon.class" in names
    assert not any(name.startswith(("com/iafenvoy/iceandfire/", 
                                    "assets/iceandfire/", "data/iceandfire/")) for name in names), (
        "Full CE classes/items/assets accidentally bundled"
    )
    meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
    assert len(meta["mods"]) == 1 and meta["mods"][0]["modId"] == "aurenminecraft"
    dependencies = {dep["modId"] for dep in meta["dependencies"]["aurenminecraft"]}
    assert "iceandfire" not in dependencies and "jupiter" not in dependencies
    assert "uranus" in dependencies
print("PASS: one Auren dragon, no full IceAndFire mod, entities, items, creative tab or Jupiter")
