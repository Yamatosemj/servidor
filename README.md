# Auren Minecraft — v0.7.0-engine-bootstrap (Minecraft 1.21.1 / NeoForge)

## 0.7.0 — começar com TODO o código original do Ice and Fire CE

**Código completo, sem podas:** `vendor/iceandfire-ce` é um submódulo Git
fixado no commit `aa2f11a6b79acb48a1094765d610cdaca79e3d18` do IceAndFire
Community Edition 2.1.4. Contém o código-fonte e os recursos originais,
incluindo TODOS os dragões, IA, física, voos, montaria, ataques, renderizadores,
animações, inventário, crescimento, registradores e dependências internas.

**Como testamos:** o workflow `native-engine.yml` compila o mod original
inteiro, em seu próprio projeto, e fornece um artefato com dois JARs:
`iceandfire` original e nosso `aurenminecraft`, mais instruções.
Jupiter 2.3.7 e Uranus 3.0-beta.1 já vêm embutidos dentro do JAR do Auren via
NeoForge Jar-in-Jar. Portanto, **só instalar os dois JARs do pacote:
Auren + IceAndFire CE**, sem Jupiter ou Uranus separados.

**Por que não copiamos tudo para `src/main/java` do Auren?** Isso
duplicaria registries, eventos, classes e identificadores do mod e arriscaria
corromper saves. O código completo continua intocado até termos validação e
um plano para convertê-lo numa única implementação independente.

**Estado:** temos o código original completo disponível e um build separado
para todas as funcionalidades, mas a nossa entidade antiga ainda NÃO foi
migrada para ele. Seus comandos de transformação por botão direito, ombro
e montaria continuam no Auren legado. A entidade nativa do CE pode ser
invocada por `/auren original` (raio) ou `/auren original_fogo`.
Nenhuma remoção de funcionalidade foi feita.

Clone completo: `git clone --recurse-submodules
https://github.com/Yamatosemj/servidor.git`, depois mude para esta branch.
Ou na cópia existente: `git submodule update --init --recursive`.

Documentação técnica: `docs/CE_FULL_SOURCE_MIGRATION.md`.


## Experimental native CE parity baseline (v0.6.5-native-probe)

**NOT A MIGRATION AND NOT YET A REPLACEMENT FOR AUREN.**
This experimental branch adds two privileged side-by-side test commands:
`/auren original` (black native CE lightning dragon) and
`/auren original_fogo` (red native CE fire dragon).
These spawn actual original `iceandfire` entities, owned by the caller,
age 50 (stage 3), following CE's own original movement, navigation,
mounted controls, full native combat, animations and renderers.
The existing `aurenminecraft:auren` is unchanged; `/auren ombro` still
applies ONLY to that legacy entity.

**Installation changes:** requires original **IceAndFire Community Edition
2.1.4 for NeoForge 1.21.1**, with Jupiter 2.3.7 and Uranus 3.0-beta.1 **both embedded inside Auren**. Do not install multiple Auren JARs.
Use a backed-up test world. Run OP-only commands on broad flat terrain
with no ceiling.

The native dragons use the original Ice and Fire texture/behavior:
the lightning one shoots **lightning**, and the fire one shoots **fire**.
They do not yet implement Auren's unique-per-world rule, shoulder form,
ritual, custom AI personality or automatic migration of old Auren saves.
Those are intentionally left OUT of this baseline to avoid claiming
identity from a partial imitation. Full engineering audit:
`docs/CE_NATIVE_PARITY_AUDIT.md`.


## v0.6.4-alpha — sopro de fogo do Fire Dragon original (montado)

**Novo comando:** segurar `R` montado na forma adulta domesticada: após
~1 segundo, um sopro contínuo sai do focinho na direção apontada pelo
jogador. No Zalith, mapear uma tecla virtual para `R` na tela de jogo.
Soltar a tecla, desmontar, morrer ou voltar à forma filhote encerra a ação.

**Código-fonte reutilizado** da IceAndFire Community Edition 1.21.1
(`aa2f11a6...`, LGPL-3.0-or-later):
- `FireDragonEntity.riderShootFire`: ramo do sopro contínuo, sem a
  variante aleatória `FireDragonChargeEntity` nesta etapa.
- `DragonBaseEntity.isActuallyBreathingFire`: 20 ticks de preparação;
  `rayTraceRider`: olho do piloto + direção da câmera, alcance
  `10 * estágio` (estágio fixo 3 = 30 blocos).
- `DragonBaseEntity.performNormalBreathAttack`: progresso do jato até
  `40`, origem calculada por `getHeadPosition` com adaptação do tamanho
  atual do Auren.
- `IafDragonDestructionManager.destroyAreaBreath` (ramo Fire do estágio
  3): efeito ao atingir a área de 3.5 blocos, dano de estágio 3 * 2,
  incendiar criaturas por `5 + estágio*5` segundos. Dano limitado a
  um pulso a cada 5 ticks para evitar excesso em Zalith.

**Compatibilidade:** a rede CE de controle do piloto passa o bit `strike=8`
junto com subir `1` e descer `2`, validando **dono montado** no servidor.
O cliente não escolhe coordenadas de ataque. Não atinge piloto, dragão,
outros animais domesticados pelo mesmo dono nem aliados. Proteção contra
chunks não carregados. A colocação de fogo no bloco de impacto só ocorre
com `mobGriefing=true` e suporte válido para fogo.

**Limitações declaradas:** o Fire Dragon original tem partículas e sons
próprios, blocos carbonizados e uma bola de fogo especial, que dependem de
recursos e entidades do mod completo. Aqui o jato original está adaptado
com partículas `FLAME` e som vanilla, sem detonações nem transformação
permanente do terreno. A posição da boca é calculada, não obtida dos ossos
animados; precisa de vídeo em terceira pessoa. A v0.6.3 montada também
continua em teste: CI valida código/build, não demonstra comportamento
idêntico ao Ice and Fire no mundo real.

**Teste em jogo:** montar, manter R, ver as chamas sair da frente da cabeça,
mirar um zumbi e conferir dano/fogo; soltar R e confirmar parada; mirar
bloco com mobGriefing ligada/desligada; testar um lobo domesticado, filhote,
desmontar durante o sopro e voar enquanto respira fogo. A comparação com
o Fire Dragon original fica pendente até a reprodução visual no Zalith.
## v0.6.3 — pilotar o Auren adulto com o sistema original da CE

### Correção de fidelidade após teste real (14/10/2026)

O primeiro JAR 0.6.3 montava, mas o voo era muito diferente da CE. **Não
reutilizar esse build antigo**. Revisamos o método `DragonBaseEntity.travel()`
no commit fixado da CE e restauramos suas rotinas:
- A Lightning Dragon original usa velocidade-base `0.15..0.4`; o
  multiplicador de voo agora usa `5.2 + Mth.map(speed, 0.15, 0.4, 0, 1.5)`
  em vez de um `5.2` fixo.
- `glidingSpeedBonus`: aceleração adicional vinculada à componente Y da
  velocidade, limites originais `-0.8..1.5` e decaimento `0.01`.
- Planar/sprint: produto da velocidade `1.5`, lateral `0.1`, seno/cosseno
  do pitch da câmera, teclas de subir/descer como na CE.
- Voo comum: modificadores CE `0.5` de ré e `0.4` de lateral,
  `moveRelative`, `MoverType.SELF`, atrito `(0.9,0.9,0.9)`.
- Decolagem: incremento `spacebarTicks += 2` e redução `--` por tick,
  estado inicial de pairar e transição para movimento à frente,
  preservando distinção entre hovering e flying.
- Assento segue as funções polinomiais `getRideHeightBase` e
  `getRideHorizontalBase` do original, com o mesmo offset de altura do
  passageiro. O corpo usa o cálculo CE de pitch no deslocamento.

**Fidelidade verificada por código, não por imagem**: Auren continua uma
`TamableAnimal` independente, sem `DragonBaseEntity`, e não tem o
conjunto de amortecedores de rotação visual/tail, stamina, ataque e crescimento
da CE. Portanto não afirmar que todos os aspectos do voo e da animação são
idênticos ao mod original; comparar lado a lado em Zalith depois do CI.
A base autônoma 0.6.2 permanece inalterada.



**Base testada pelo jogador:** v0.6.2 (caminhada prioritária, voo estável).
Esta branch parte da versão testada; **não** substitui `main` nem inclui o PR
separado de pose de ombro.

Usamos as partes relevantes do **IceAndFire Community Edition 1.21.1**:
- `DragonBaseEntity.mobInteract` (montagem pelo dono), `getControllingPassenger`,
  `positionRider`, `updateRider` (carregar o espaço para decolar) e
  `travel` (caminhada e voo controlado)
- `DragonControlC2SPayload` e `ClientEvents.onLivingUpdate` (bits originais
  de subir/descer), `NetworkManager` e `ServerNetworkHandlers`
- Teclas e direção da CE: **WASD** caminha/voa, **segurar espaço por ~1 s**
  decola, **espaço** sobe, **X** desce, **Shift** desmonta. Sprint durante
  voo permite pilotar a inclinação com a câmera.
- O joystick do Zalith deve alimentar os controles vanilla de movimento. Para
  descer, mapear a tecla X no painel de controles do Zalith, se necessário.

**Limites/salvaguardas:** apenas dono + forma adulta; um passageiro; servidor
valida jogador/montaria antes de aceitar subida/descida; pacote não recebe
posição do cliente. A IA de seguir e vaguear é pausada quando montado. Voo
autônomo v0.6.2, pouso/gravidade após desmontar, teleporte seguro a 50 blocos,
sistema de ombro e tickets continuam independentes.

**TESTE MANUAL OBRIGATÓRIO:** após instalar o JAR experimental, domar o
adulto com mão vazia e interagir de novo para montar. Em solo testar WASD,
câmera, colisão e desmontagem; decolar segurando espaço, subir/descer,
pousar sobre chão sólido e desmontar em voo (testar em ambiente seguro);
validar retorno ao seguimento, tamanho filhote e modo ombro. Build CI e
testes estruturais **não comprovam que o joystick touch e a câmera 3ª
pessoa funcionam dentro do Zalith**. Seguir observações/vídeo para ajuste
preciso de posição de assento e controle.



## v0.6.2 — ground-first follow and smoother CE flight (test build)

After the in-game video revealed two wing beats followed by apparent aerial
hopping, and the dragon taking off immediately instead of walking, code
inspection exposed two specific v0.6.1 problems:

1. The follow goal invoked `flightController.follow(owner)` EVERY tick, even
   while grounded. The method begins takeoff when it has headroom, bypassing
   regular ground pathfinding almost entirely.
2. The upstream CE flight manager injects +0.1 Y velocity when targeting a
   higher point. Our port did that with `noGravity=true`, so upward momentum
   could build without the gravity model used by the original dragon.

Fixes (no additional dependencies/features):
- Every follow begins with ordinary ground navigation. After ~30 ticks of
  failed/stalled progress, the Auren may switch to the CE flight mover.
  An extended chase can also trigger takeoff after at least ~40 ticks walking
  if still over 32 blocks away (adult) or 14 (baby).
- Keep the original CE `FlightMoveHelper` yaw, acceleration, pitch, waypoint
  sampling and the Uranus flying navigator; remove its incompatible repeated
  vertical boost. Stabilize Y velocity only toward the existing CE waypoint,
  leaving CE horizontal steering unchanged. Dampen movement briefly if the
  flight manager has no unobstructed waypoint.
- Match the CE animation cadence: its 6 flight poses advance by 2/10 frame
  per tick, rather than the prototype 0.16.
- Adult start/stop 15/8, baby 4.5/1.7, safe emergency teleport 50+, chunk
  ticket and shoulder positioning are unchanged.
- The PR remains experimental: CI builds and checks structural invariants,
  but only an actual Zalith test can verify smooth movement/landings.

**Zalith checks:** first walk 16–25 blocks away from the adult on flat terrain
(it should walk, not immediately fly); let it follow behind an obstacle;
try a prolonged 35+ block chase; film a sustained flight from the side;
then test a baby at 5 blocks, a 50+ block teleport and shoulder mode.

## v0.6.1 — directly ported IceAndFire CE flight engine (not a homegrown flight controller)

The user confirmed v0.6.0 actually flies, **but flies too slowly**. The old
v0.6.0 homegrown steering/velocity code has now been replaced by the original
IceAndFire Community Edition 1.21.1 algorithms, **adapted to AurenDragon**:

- `AurenIafDragonFlightManager.FlightMoveHelper`: CE's yaw turning,
  `approachDegrees`, `approach` acceleration, pitch and air movement.
- `AurenIafDragonFlightManager.update` plus `getBlockInViewEscort`:
  the CE escort target chooser, adapted for a player-owned Auren and smaller
  baby size. It samples candidate airborne destinations and checks that they
  are collision-free, line-of-sight reachable and already in loaded chunks.
- `AurenDragon.useIceAndFireFlightNavigation`: switches to the **same
  Uranus AdvancedPathNavigate MovementType.FLYING** used by the original CE;
  restores the original ground navigator/controller on landing. The Uranus
  dependency was already included in v0.5, so **NO new dependency**.
- CE waypoint heights are scaled to **2–3 blocks (baby)** and
  **4–6 blocks (adult)**, rather than the original large-dragon 8–24:
  this matches Auren's existing landing thresholds and prevents endless
  hovering. Waypoints refresh only after substantial owner movement or
  blockage so CE's smooth 4° turn/acceleration can build speed.
- The previous AurenFlightController is now **only a lifecycle adapter**:
  checking headroom, takeoff, landing, gravity, owner/disconnection and
  stuck timeout. **It no longer invents custom air acceleration and turns.**
- Minimal documented robustness changes from the upstream CE code: handle
  vertical division-by-zero, clamp horizontal sign and acceleration symmetrically,
  and avoid compounding slow-down into persistent speed every tick during
  close-up owner-follow (the CE was tuned for distant escort waypoints).
- Existing owner-follow thresholds remain **adult 15/8, baby 4.5/1.7**.
  The tested **50+ block safe teleport**, temporary chunk ticket and original
  CE Tabula flight models/secondary animation remain intact.
- PR #15's experimental shoulder pose remains **separate**; no shoulder,
  adult-size, combat, ritual or mounting changes are included.

### Verify in Zalith

1. Replace the previous JAR; summon and tame Auren.
2. Move **16–30 blocks** from the adult on flat ground. Observe flight speed,
   turning rate, altitude and landing compared with v0.6.0.
3. Test the baby beyond 4.5 blocks, both in open air and under low ceilings.
4. Try a wall/trees, then 55–65 blocks (50-block emergency teleport).
5. Test save/reload midflight, sitting midflight and shoulder toggle.
6. Send a short video or observations: this port compiles, but **the source
   CE algorithms need playtesting when adapted to Auren's smaller hitbox,
   following distances and different entity base**.

**Important scope:** the port does not automatically provide IceAndFire
riding, dragon combat, fire/lightning breath, inventory, full stage growth or
every AI goal. Those have separate dependencies and integration requirements.
The original CE flight movement and owner-escort target selection are now
real source-derived code rather than a brand-new imitation.

## v0.6.0 — flight foundation: autonomous follow, takeoff and landing

**Real flight** is now implemented for both forms. Auren uses velocity,
server-side flight state and native collision instead of merely changing the
renderer while falling. The controller adapts the IceAndFire CE separation of
flight manager / aerial target / move control, but does not require the
IceAndFire mod itself.

- **Adult** still starts following beyond **15 blocks**, stops within **8**.
- **Small form** keeps its proven comfortable **4.5/1.7** follow thresholds,
  but flies instead of using vanilla FollowOwnerGoal teleport at ~12 blocks.
- **Emergency teleport is 50+ blocks for both forms**, reusing the original
  safety tests and destination search. The previously working adult ticket
  also stays active for the small form when not on the shoulder, preventing
  unload before teleport.
- **Takeoff:** check headroom, initial vertical thrust, hover pose, then
  transition to six original lightning-dragon flight frames.
- **Cruise:** velocity-based pursuit of the owner's changing location;
  predictive upward detour when a forward bounding-box would collide.
  No phasing through walls and no force-loading target chunks.
- **Landing:** on reaching the owner horizontally (8 blocks adult, about
  2 blocks baby), losing the target or persistent collision, gravity is
  restored and speed is damped. The baby will not hover forever simply
  because cruising altitude exceeds its ground stop distance. Landing uses
  CE hovering and clears the state at touchdown.
- **Animation:** a synced integer flight state plus 10-tick blend selects
  the original CE hovering/flying/diving Tabula poses and 6 flight frames.
  It also ports the original CE continuous in-flight body bob, neck/tail
  waves, thighs and toes motion; the animation is not stiff keyframes alone.
  Ordinary jump no longer automatically triggers the flying state.
- Shoulder pose on main remains exactly v0.5.6. **The experimental v0.5.7
  shoulder revision stays isolated on PR #15** and is not included here.
- Existing **Uranus 3.0-beta.1 already supplies every Tabula dependency**;
  no additional mod dependency or IceAndFire installation is required.

### How to test flight in Zalith

1. Install a single Auren JAR (remove the previous version from `mods`).
2. Use `/summon aurenminecraft:auren` and tame with an empty hand.
3. In a flat clear area, walk more than **15 blocks** from the adult.
   It should rise, flap its ORIGINAL CE wings, follow in the air and land.
4. Walk away again, then test the **baby** beyond 4.5 blocks, not on shoulder.
5. After both move successfully, try a low obstacle, building wall and trees.
   The controller can choose a basic upward avoidance direction, but **is not
   yet the complete CE 3D pathfinder**. Some complex enclosures may trap it.
6. Travel 55–65 blocks away; it should still use the 50-block emergency
   teleport when a safe ground destination exists.
7. Test leaving/re-entering the world during/after flight; verify gravity and
   original shoulder interaction continue to work.

**Known limits:** stage-3 player riding and flight controls, fire/lightning
breath, advanced 3D A* navigation, stamina, combat and user-specific UI are
separate features. This version aims for working autonomous flying FIRST.
GitHub CI compile/static tests are NOT a substitute for runtime testing.
Do not mark aerial steering, player collision and landing as visually
approved until Zalith footage confirms.

## v0.5.6 — awake, folded-wing neck wrap from user illustration

The user supplied a front/back drawing: the baby Auren's head and neck
rest on the LEFT shoulder; torso lies behind the neck, wings are folded,
and tail extends toward the RIGHT shoulder. The previous version looked
asleep rather than alert, and v0.4 had the model sideways.

- Restore the v0.4 neck/tail wrap concept with corrected neck bend and no
  global 90-degree renderer rotation.
- Use the awake original CE `sitting_shoulder.tbl` pose as the base,
  rather than the full original `sleeping.tbl`.
- Interpolate ONLY the four principal wing bones from CE's original folded
  sleeping pose; wing keyframes are not improvised.
- Keep the continuous original CE idle movements already working.
- Reuse the comfortable nape anchor from v0.4 (0.075 left, 0.18 behind,
  Y = 0.76 × player height), with torso-aligned yaw.
- Keep baby scale 0.42, adult scale 3.25, normal animations and **fully
  unchanged adult follow (15 blocks), emergency teleport (50 blocks)
  and temporary chunk loader**.

**Must be checked in Zalith:** front and back screenshots beside the reference.
The build tests the bone mappings, but does not render the player/dragon
in-game. If the head or tail appears mirrored or intersects the model,
adjust the joint yaw/anchor only; do not change working follow AI.

## v0.5.5 — original CE curled/resting shoulder posture

The screenshot confirmed the baby is STILL standing upright behind the
player, even after adding the CE idle motion. Our earlier diagnosis was
incomplete: the official `sitting_shoulder.tbl` itself is an **upright pose**.
We compared the actual binary .tbl bones. Relative to `sitting_shoulder`,
the official CE `sleeping.tbl` crouches the body more, tucks the wings,
folds the hind legs, and bends the tail.

**Fix:** use the *original unmodified CE sleeping Tabula pose* while the baby
is perched, combined with the *original CE awake idle bone motion* already
present in v0.5.4. This is a resting dragon, NOT an authored new animation.
The 20-tick interpolation and the 0.42 small scale are preserved. We also
bring its anchor toward the neck (lateral 0.30 instead of 0.50; height 1.30
instead of 1.40). The old shoulder-only `BodyUpper.offsetZ=-12` correction
is inappropriate for the sleeping pose and is removed.

No changes to the adult (15-block follow, 50-block emergency teleport,
chunk ticket), baby normal follow, original CE walk/flight cycles or ritual.

**Zalith test:** compare against v0.5.4 in third person from front, back,
and side. The hatchling should visibly crouch/lie more with tucked wings,
no longer stand upright behind the shoulder. The CE idle breathing and tail
motion remain subtle. Test visually: CI checks pose source and skeletal
differences, not rendered pixels. The mod still cannot model a truly
neck-wrapping scarf without dedicated new Tabula animation assets.

## v0.5.4 — original IceAndFire CE shoulder idle animation

In v0.5.3 the baby correctly used the **original CE sitting_shoulder static
pose** but appeared frozen because the renderer returned **before applying the
original CE idle motion**.

This version ports the unchanged core of the CE animator's awake idle sequence
(speed = 0.05, degree = 0.5 and the original per-bone amplitudes/phase offsets)
to the already-posed baby: tail swinging and waving, neck waving, and subtle
body/leg/wing bobbing. The amplitude ramps with the existing 20-tick shoulder
transition. No new pose/animation is authored: the original IceAndFire routines
operate on the original Lightning Dragon skeleton using Uranus' built-in
animation methods.

**Scope:** while perched on the player's shoulder only. Follow behavior,
50-block adult emergency teleport, temporary chunk ticket, CE walk/flight
animations, size and shoulder anchor stay unchanged.

**Test on Zalith:** wait still with the baby perched for 10–20 seconds and
watch head/neck, tail, and body. It should no longer freeze in a rigid stance.
Its movements are deliberately subtle because CE uses slow idle animation.
Compilation and static checks do NOT replace visual testing on Android.

## v0.5.3 — original CE shoulder pose (fix for twisted baby on neck)

- Remove the extra hand-authored `AurenNeckPose` bone rotations and the
  **90-degree root rotation** that were twisting the baby while mounted.
- Use the original CE `lightningdragon_sitting_shoulder.tbl` for the 20-tick
  transition and reproduce CE's special `BodyUpper.offsetZ → -12` rider
  correction (previously missing).
- Attach at the original first shoulder passenger position: approximately
  **0.5 blocks to the left** of the player's center and **1.4 blocks high**,
  oriented using player's body yaw, not head/camera direction. Small backward
  correction: 0.04 blocks.
- Keep the small model scale at 0.42 and adult scale at 3.25; adult following,
  temporary chunk ticket, and original walk/flight keyframes unchanged.
- **Important visual distinction:** CE's original sitting-on-shoulder pose is
  a perching pose, not the wholly custom around-the-neck living-scarf concept.
  First correct the objectively twisted attachment, then use the user's Zalith
  screenshots to decide whether a new model asset is needed later.

## Correction v0.5.2 — keep the adult's current chunk loaded while following

The previous goal could not teleport once Minecraft **stopped ticking the
unloaded Auren entity**. The adult now acquires a small **temporary NeoForge
entity-ticking region ticket** centered on its current chunk when its tamed
owner is online in the same dimension. The ticket is renewed every 20 ticks,
has a 100-tick automatic expiry, and is removed on death, removal, sitting,
form change, logout or dimension change. As Auren moves, the ticket moves with
him; it does not leave permanent `/forceload` chunks behind.

This lets the adult keep evaluating the 50-block emergency teleport rule
instead of becoming frozen when simulation distance is low. The small Auren
and neck mechanic remain unchanged. This still does not implement flight.

**One-time caveat for existing saves:** if the adult's chunk was already
unloaded *before installing v0.5.6*, you need to approach his last known
location once to load him and start the first ticket. A durable unloaded
entity index is not part of this focused update.

### How to test in Zalith

1. Replace the previous JAR, then return to the adult Auren if he was left
   in an unloaded chunk before updating.
2. With the adult tamed, walk **16 blocks** away: he starts following;
   from within **8 blocks**, he stops.
3. Run/walk at least **60 blocks** away and wait a moment. He should now
   teleport to a safe location roughly 8–12 blocks away, even with short
   simulation distance. Terrain/collision can block emergency teleport if
   there is no safe destination.
4. Repeat as the small Auren to confirm no regression.
5. Turn the adult small or leave the world. The adult's temporary ticket
   should stop being maintained.

## Correction v0.5.1 — Adult following distance

- **Small form:** keeps its existing vanilla FollowOwnerGoal parameters (speed 1.15, start 4.5 blocks, stop 1.7 blocks). Shoulder mode unchanged.
- **Adult form:** separate `AurenAdultFollowOwnerGoal`. Starts following when the player exceeds **15 blocks** of separation, stops around **8 blocks**, with comfortable space to view the full dragon.
- **Emergency teleport:** adult only tries to teleport when the owner is **50+ blocks** away, not around 10–12 blocks. A valid destination must be on solid ground, collision-free and outside the owner's immediate space (roughly 8–12 blocks).
- **Adult visual size lowered from 5.8 to 3.25**, approximately the middle of the original CE stage-3 range (2.45–4.375); adult collision box adjusted to 1.05 × 1.3 blocks.
- **Flight and riding remain future work**: the dragon is still ground-bound. The requested flying follow behavior for both forms will replace terrestrial chasing and the baby's vanilla teleport in a separate, tested update.
- No change to original CE model/animations or the neck pose; the incorrect neck pose remains a known separate task.
- Check adult and baby behavior in game; compile checks cannot confirm visual/pathfinding behavior.

## Original IceAndFire CE lightning-dragon animations

**No hand-authored walking or wing-flapping animation.** The renderer now uses
the **original IceAndFire Community Edition Tabula assets** for the Lightning Dragon:

- `walk1`–`walk4`: the four source keyframes for ground locomotion, interpolated
  according to movement speed and limb cycle.
- `flight1`–`flight6`: the six source wing-flap keyframes.
- `flying`, `hovering` and `diving`: official source poses used as appropriate
  during jumps/falls, preparing for a future flight movement system.
- `ground` remains the default pose while standing.
- **Original CE shoulder pose** is interpolated across 20 ticks without
  custom 90-degree rotation or hand-authored body twisting.
- Baby scale remains **0.42**, adult scale changed to **3.25** (similar to the original CE stage 3).

Source: [IAFEnvoy / IceAndFire-CE, 1.21.1](https://github.com/IAFEnvoy/IceAndFire-CE/tree/1.21.1).
The assets are downloaded from an immutable commit and their Git blob hashes
checked during every build. The Uranus **3.0-beta.1** dependency is embedded
via NeoForge Jar-in-Jar. You do not need to install IceAndFire CE or Uranus
separately.

**Flight is NOT functional yet.** This version imports original animation
**assets** and blends them like the source mod. It does not implement
aerial navigation, takeoff, landing, player mounting or powered flight.
The original CE's whole `DragonTabulaModelAnimator` depends on its custom
entity states, which are not present in Auren; it is not copied wholesale.

## Playing and testing

1. Remove earlier Auren JARs from `mods` and install the one for v0.5.6.
2. Minecraft Java **1.21.1**, NeoForge **21.1.248+**.
3. Summon with `/summon aurenminecraft:auren`; empty-hand interact to tame.
4. Observe the big dragon walking: the feet/body should now use the original
   four frame cycle (no procedural replacement).
5. Carefully test when it jumps/falls: its wings should use the original
   airborne poses; **it still cannot fly under its own power**.
6. Test baby form (sneak + interact), `/auren ombro`, and dismount to check
   that the original shoulder behavior has not regressed.
7. Send short recordings if wing movement or the model looks incorrect. The
   source poses are original, but their timing and state selection still need
   visual calibration in Zalith.

## Scope

- Not implemented: ritual, unique-Auren world persistence, aerial pathfinding,
  true flight, advanced combat, voice/AI integration, riding.
- Future ritual requirements are documented separately in
  `docs/FUTURO_RITUAL_LUNAR_AUREN.md`; no ritual code is touched by v0.5.
- Original asset licensing: `THIRD_PARTY_ASSETS.md`.
