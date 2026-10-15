"""Inspect native compiled CE JAR instead of trusting a 'build success' label."""
from pathlib import Path
from zipfile import ZipFile
import shutil
import tomllib
ROOT = Path(__file__).resolve().parents[1]
LIBS = ROOT / "vendor/iceandfire-ce/build/libs"
REQUIRED = {
    "META-INF/neoforge.mods.toml",
    "com/iafenvoy/iceandfire/entity/DragonBaseEntity.class",
    "com/iafenvoy/iceandfire/entity/FireDragonEntity.class",
    "com/iafenvoy/iceandfire/entity/IceDragonEntity.class",
    "com/iafenvoy/iceandfire/entity/LightningDragonEntity.class",
    "com/iafenvoy/iceandfire/entity/util/dragon/IafDragonFlightManager.class",
    "com/iafenvoy/iceandfire/entity/util/dragon/IafDragonLogic.class",
    "com/iafenvoy/iceandfire/entity/util/dragon/IafDragonDestructionManager.class",
    "com/iafenvoy/iceandfire/render/model/animator/LightningTabulaDragonAnimator.class",
    "com/iafenvoy/iceandfire/network/payload/DragonControlC2SPayload.class",
}
match = []
for jar in sorted(LIBS.glob("*.jar")):
    if jar.name.endswith(("-sources.jar","-javadoc.jar","-plain.jar")):
        continue
    with ZipFile(jar) as z:
        if not REQUIRED.issubset(set(z.namelist())):
            continue
        mods = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode())["mods"]
        if len(mods) == 1 and mods[0]["modId"] == "iceandfire":
            match.append(jar)
assert len(match) == 1, f"Need one complete original CE JAR, found {match}"
auren = list((ROOT/"build/libs").glob("aurenminecraft-*.jar"))
assert len(auren) == 1, f"Need Auren bridge JAR, found {auren}"
dist = ROOT/"build/ce-native-dist"
dist.mkdir(parents=True, exist_ok=True)
for path in (match[0], auren[0]):
    shutil.copy2(path, dist/path.name)
shutil.copy2(ROOT/"vendor/iceandfire-ce/LICENSE", dist/"ICEANDFIRE_CE_LICENSE.txt")
(dist/"README_INSTALL.txt").write_text(
    "Auren 0.7 — unmodified original IceAndFire CE 2.1.4 engine (EXPERIMENTAL)\n"
    "NeoForge 1.21.1. Backup worlds. Copy BOTH mod JARs to mods/.\n"
    "Install Jupiter 2.3+ as required by original CE.\n"
    "Do NOT install an additional IceAndFire CE or Auren JAR.\n"
    "Uranus 3.0-beta.1 is already bundled inside Auren.\n"
    "With OP: /auren original (black lightning CE dragon),\n"
    "         /auren original_fogo (red fire CE dragon).\n"
    "Legacy Auren keeps right-click size & shoulder controls.\n"
    "Complete original CE source (LGPL-3.0-or-later):\n"
    "https://github.com/IAFEnvoy/IceAndFire-CE/tree/aa2f11a6b79acb48a1094765d610cdaca79e3d18\n"
)
print(f"PASS: packaged original CE binary ({match[0].name}) and Auren ({auren[0].name})")
