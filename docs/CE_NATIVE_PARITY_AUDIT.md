# Auren × IceAndFire Community Edition 1.21.1 — architecture audit

Date: 2026-10-15. Scope: baseline from Auren PR #19 (v0.6.4-alpha)
vs original CE Git commit
`aa2f11a6b79acb48a1094765d610cdaca79e3d18`
(2.1.4; LGPL-3.0-or-later).
Original: https://github.com/IAFEnvoy/IceAndFire-CE/tree/aa2f11a6b79acb48a1094765d610cdaca79e3d18

## Technical result

**Behavioral identity is impossible while the original Auren remains an
independent `TamableAnimal` with manually reconstructed CE mechanics.**
The divergence is architectural, not only speed constants.
This patch **does not** claim to eliminate all differences in that entity.

### Verified differences

| Subsystem | Original CE | Auren 0.6.4 | Consequence |
|---|---|---|---|
| Entity root | `DragonBaseEntity` (3009 lines) and `LightningDragonEntity` / `FireDragonEntity` | `AurenDragon extends TamableAnimal` (~720 lines) | Base tick, entity parts, attributes, state & gameplay differ |
| AI goals | `DragonAIReturnToRoostGoal`, `DragonAIEscortGoal`, melee / attack and targets, sleep/hunger | dedicated `AurenAdultFollowOwnerGoal`, 15/8 and 50 teleport | Route, takeoff, targeting, escort and landing differ |
| Autonomous navigation | `IafDragonFlightManager` + CE `DragonUtils.getBlockInViewEscort` + `IafDragonLogic` | partial `AurenIafDragonFlightManager` + `AurenFlightController` gates / custom ceilings | Different altitude, state transition, gravity, path and targets |
| Rider travel | CE `DragonBaseEntity.travel`, `updateRider`, `updatePitch` | copied formulas but separate `TAKING_OFF/FLYING/GROUNDED`, no CE full state | Hover/glide, timing, effects and collisions not guaranteed identical |
| Stamina | synced stamina, drain, climb cost, glide state | absent | Long flight and exhaustion differ |
| Mounted combat | strike + charge, bite, tackle, attack animations, hunger/health effects | only user-held stream | Different fighting behavior |
| Fire breath | 1/5 charge branch in `FireDragonEntity.riderShootFire`; `performNormalBreathAttack`, destruction manager, real CE particles / sounds | continuous-only `AurenFireBreath`, hardcoded ~12 FLAME samples, 5-tick damage, no projectile or terrain change | Not equivalent visually or mechanically |
| Lightning | `LightningDragonEntity` synchronizes lightning arc endpoints, effects and projectiles | Auren has Lightning CE model but FIRE imitation | Attack type is not CE Lightning |
| Animations | CE animator stack and chain buffers / all native state | selected Tabula keyframes plus partial motion & custom neck pose | Tail, wing, body, pitch and transitions differ |
| Physical dragon | CE dynamic size, age, multipart hitboxes and collision | fixed entity hitbox and two forms / size transforms | Riding, collision, environmental response differ |
| Aging/items | growth, hunger, equipment, inventory, eggs, config and other gameplay | deliberately custom one-Auren system | Non-identical gameplay |

### Decision: native reference variant, no overwriting working Auren

Two OP-only commands spawn CE's actual **registered entity types**, not
subclasses and not replicas:

- `/auren original`: `IafEntities.LIGHTNING_DRAGON`, black, age 50.
- `/auren original_fogo`: `IafEntities.FIRE_DRAGON`, red, age 50.

Both spawn owned/named 'Auren' and follow using CE command=2.
No overrides of CE movement, AI, animation, target/combat or model.
These are **native parity reference dragons**, not feature-complete Auren
replacements. Their original IDs remain `iceandfire:lightning_dragon`
or `iceandfire:fire_dragon`; this is why CE's exact internal behavior
can be retained. Existing `aurenminecraft:auren` is left intact.

The reference stage/color/owner and command are set on spawn; all behavior
thereafter is delegated to the genuine CE 2.1.4 engine. The mod must
require that same binary at runtime (CurseForge file 9128508). Auren's
existing embedded Uranus and CE's Jupiter requirement must coexist.

### Runtime validation required

1. Install original CE 2.1.4 and its required dependencies, plus this
   experimental Auren JAR on NeoForge 1.21.1 in a *backup test world*.
2. Verify CE itself loads and original vanilla CE dragon rendering works.
3. In open terrain run both commands; verify the right black lightning /
   red fire models, stage 3, mounted movement, original controls and attacks.
4. Compare their video/flight path to an ordinary CE stage-3 dragon;
   both should be the same type and share behavior implementation.
5. Test save/reload, pet ownership, death, eggs, startup, combat, armor.
6. Confirm the previous Auren still works without overwriting its save.
7. Assess FPS/latency on Zalith and possible conflict between Uranus Jar-in-Jar
   and the Uranus/Jupiter requirements of the CE 2.1.4 mod.

**Limits:** CI compile and source assertions do not establish runtime
identical conditions/config, and spawning an entity with different age/owner
state is not an identical experiment unless these match on both sides.
Native baselines do not implement the user's unique Auren creature, rituals,
transformation or custom shoulder behavior. They are a migration path to
those features, not the finished migration. No claim of full parity is made
for the existing independent entity.

## Follow-up architecture

1. Validate the native CE reference first.
2. Choose lightning native dragon as authoritative mechanics baseline; add
   companion identity via stored metadata/events, NOT rewritten `travel`.
3. Adapt one-Auren ritual & lifecycle without replacing CE flight, AI or combat.
4. Treat a fire/lighting hybrid as explicitly **non-identical** to either
   original CE elemental dragon unless implemented as a supported original
   CE mechanic.
5. Plan a world-data migration separately. Changing existing Auren entity IDs
   directly can break saved worlds.
