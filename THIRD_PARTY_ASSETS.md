# Recursos externos — atribuição e licenças

**Fonte dos modelos/texturas:** IceAndFire Community Edition, <https://github.com/IAFEnvoy/IceAndFire-CE>, ramo 1.21.1, commit `aa2f11a6b79acb48a1094765d610cdaca79e3d18`.

O repositório publica seu código sob LGPL v3 ou posterior. Repositório e direitos dos recursos originais devem ser respeitados. Este projeto não reivindica autoria sobre os recursos do Ice and Fire.

**Arquivos incluídos no jar por tarefa de build:**

- `models/tabula/lightningdragon/lightningdragon_ground.tbl`
- `models/tabula/lightningdragon/lightningdragon_sitting_shoulder.tbl`
- `textures/entity/lightningdragon/black_1.png`
- `textures/entity/lightningdragon/black_5.png`

Os caminhos dos arquivos são remapeados ao namespace `aurenminecraft` no build. O downloader confere o identificador Git SHA-1 de cada arquivo. Não alteramos os bytes dos modelos/texturas.

**Dependência embutida:** Uranus 3.0-beta.1, <https://github.com/IAFEnvoy/Uranus>, sob LGPL-3.0; o JAR original de Uranus é incluído intacto em `META-INF/jarjar/` usando o mecanismo oficial do NeoForge. O usuário não precisa instalá-lo separadamente. Distribuir apenas a versão compatível para NeoForge 1.21.1. Código-fonte e licença disponíveis no upstream. O texto da LGPL está dentro do nosso JAR em `META-INF/licenses/LGPL-3.0.txt`.

**Identificação adicional:** a cópia embutida do Uranus possui seu próprio `META-INF/neoforge.mods.toml` com `modId="uranus"`, versão `3.0-beta.1`. O GitHub Actions verifica sua presença e a licença incluída antes da publicação do artefato.

**Licença do código próprio:** LGPL-3.0-or-later (escolhida para simplificar a compatibilidade entre componentes). Ao redistribuir, manter créditos e cumprir todos os avisos/licenças aplicáveis. A origem de recursos previamente derivados do Ice and Fire original deve ser revisada antes de publicar versões estáveis.


## Original animation poses — Auren v0.5

We additionally package **13 unchanged Tabula pose files** from the same immutable
IceAndFire Community Edition 1.21.1 commit already credited above:
`lightningdragon_walk1..4.tbl`, `lightningdragon_flight1..6.tbl`,
`lightningdragon_flying.tbl`, `lightningdragon_hovering.tbl` and
`lightningdragon_diving.tbl`.

The SHA-1 Git blob IDs for each file appear in `build.gradle` and are verified
before packaging. All source-model attribution and license conditions remain as
stated above. `AurenOriginalAnimations.java` adapts the original pose
interpolation technique, not the complete original animator class.


## More original CE Lightning Dragon posture resources (Auren v0.5.5)

The unchanged `lightningdragon_sitting.tbl` (Git blob
`45cab84a19e5f558f50581c5ec9ea36dde2a64da`) and
`lightningdragon_sleeping.tbl` (Git blob
`363a94e348621b6dd1cdd78eae7028d5e90a4465`) from the
same pinned IceAndFire-CE 1.21.1 commit are now also included.
The latter is used in shoulder mode for a natural curled/resting posture.
Original authorship/asset licensing is unchanged.


## Flight architecture and animation adaptation (v0.6.0)

The autonomous flight controller is project-specific code inspired by the
flight state/target/motion architecture in the **IceAndFire Community Edition
1.21.1** `IafDragonFlightManager`; the full CE entity or pathfinder is **not**
copied or shipped. Its algorithms are not a drop-in dependency because they
rely on `DragonBaseEntity`, its own control state and other IceAndFire classes.

The in-flight secondary animation in
`AurenOriginalFlightMotion.java` ports the CE
`DragonTabulaModelAnimator` continuous body/neck/tail/toe motions (flight
branch and shared idle), on top of the **unaltered original** 6 Lightning
Dragon flight keyframe `.tbl` files already attributed above.

Sources (same upstream project/branch as the pinned assets):
- https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/src/main/java/com/iafenvoy/iceandfire/entity/util/dragon/IafDragonFlightManager.java
- https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/src/main/java/com/iafenvoy/iceandfire/render/model/animator/DragonTabulaModelAnimator.java

The original authorship/licensing remains with the CE authors. The existing
LGPL license notices must be preserved. The Auren distribution still uses the
already bundled Uranus **3.0-beta.1** via NeoForge Jar-in-Jar. No additional
external mod dependency is required for this flight implementation.


## v0.6.1 — direct IceAndFire CE flight code port (LGPL-3.0-or-later)

`AurenIafDragonFlightManager.java` is an adapted port of **original code**
from IceAndFire Community Edition 1.21.1, distributed under LGPL v3 or
later. Original IAFEnvoy author attribution is included in its source header.

Source files:
- [IafDragonFlightManager.java](https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/src/main/java/com/iafenvoy/iceandfire/entity/util/dragon/IafDragonFlightManager.java): FlightMoveHelper, approach/turning/acceleration, target update
- [DragonUtils.java](https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/src/main/java/com/iafenvoy/iceandfire/entity/util/dragon/DragonUtils.java): getBlockInViewEscort sampling
- [DragonBaseEntity.java](https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/src/main/java/com/iafenvoy/iceandfire/entity/DragonBaseEntity.java): switchNavigator(ground/flying)

Only combat-specific references were removed; the CE algorithms and
navigational structure are retained as source-derived components, with
documented Auren-specific safety and size adjustments. The existing
Uranus `AdvancedPathNavigate` type is reused *directly*, not copied.

**Preserve LGPL attribution when redistributing or adapting this class.**
