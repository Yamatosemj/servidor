"""Package the complete original CE engine with only inherited resource errors fixed."""
from collections import Counter
import json
from pathlib import Path
import shutil
import tomllib
from zipfile import ZipFile

from repair_ce_resources import plan_repairs, patch_jar

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
matches = []
for jar in sorted(LIBS.glob("*.jar")):
    if jar.name.endswith(("-sources.jar", "-javadoc.jar", "-plain.jar")):
        continue
    with ZipFile(jar) as archive:
        if not REQUIRED.issubset(set(archive.namelist())):
            continue
        mods = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())["mods"]
        if len(mods) == 1 and mods[0]["modId"] == "iceandfire":
            matches.append(jar)
assert len(matches) == 1, f"Expected exactly one original compiled CE JAR: {matches}"

auren_jars = sorted((ROOT / "build/libs").glob("aurenminecraft-*.jar"))
assert len(auren_jars) == 1, f"Expected exactly one companion Auren mod: {auren_jars}"
dist = ROOT / "build/ce-native-dist"
dist.mkdir(parents=True, exist_ok=True)

overrides, renames, records = plan_repairs()
assert dict(Counter(row["kind"] for row in records)) == {
    "extension": 18, "encoding": 11, "png_crc": 8
}, "Unexpected inherited resource-fix inventory"
fixed = dist / (matches[0].stem + "-auren-resources-fixed.jar")
metadata = patch_jar(matches[0], fixed, overrides, renames)
metadata.update({
    "upstream_source_commit": "aa2f11a6b79acb48a1094765d610cdaca79e3d18",
    "counts": dict(Counter(row["kind"] for row in records)),
    "status": "REPAIRED_DISTRIBUTION_ONLY",
    "records": records
})
(dist / "ce-resource-patch-manifest.json").write_text(
    json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
shutil.copy2(auren_jars[0], dist / auren_jars[0].name)
shutil.copy2(ROOT / "vendor/iceandfire-ce/LICENSE", dist / "ICEANDFIRE_CE_LICENSE.txt")
(dist / "README_INSTALL.txt").write_text(
    "Auren 0.7 — CE original native dragon engine, repaired RESOURCE-only JAR\n"
    "NeoForge 1.21.1 only. EXPERIMENTAL — back up worlds.\n"
    "Copy BOTH JARs in this folder to mods/.\n"
    "Jupiter 2.3.7 and Uranus 3.0-beta.1 are already inside Auren.\n"
    "No other copies of CE, Auren, Jupiter or Uranus in mods/.\n"
    "36? NO: EXACTLY 37 inherited original CE resource defects are repaired:\n"
    "18 bestiary names, 11 pt-BR encodings, and 8 PNG CRC checksums.\n"
    "All original CE Java .class files are preserved byte-identical.\n"
    "CE remains a separate runtime mod; Auren unique dragon behavior not merged.\n"
    "OP test: /auren original (black lightning), /auren original_fogo (fire).\n"
    "Legacy Auren keeps right-click size changing and shoulder mode.\n"
    "See ce-resource-patch-manifest.json for per-file provenance.\n"
    "LGPL-3.0-or-later CE: https://github.com/IAFEnvoy/IceAndFire-CE\n",
    encoding="utf-8",
)
assert len(list(dist.glob("*resources-fixed.jar"))) == 1
assert len(list(dist.glob("aurenminecraft-*.jar"))) == 1
print("PASS: original native CE class parity retained:",
      metadata["preserved_classes"], "classes; repaired", len(records), "runtime assets")
