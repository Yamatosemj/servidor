# Auren Minecraft — v0.5.5-alpha (Minecraft 1.21.1 / NeoForge)

## v0.5.5 — curled baby resting on the shoulder, not standing behind the head

Based on the user's Zalith front-facing screenshot, the baby was visibly
**upright like a dragon standing on the ground**, with outstretched wings
behind the player's head. This was NOT an idle animation problem.
Inspection of the actual CE Tabula files revealed the original
`lightningdragon_sitting_shoulder.tbl` pose is largely upright (body changes
only ~23 degrees from ground). The original CE `lightningdragon_sleeping.tbl`
contains a more pronounced neck curl, folded wings and lowered torso.

- **No invented animation/keyframes.** Switch the shoulder's reference pose
  to the original `sleeping.tbl` from the same pinned IceAndFire CE commit.
  Retain the 20-tick bone interpolation and the original CE idle motion
  already added in v0.5.4.
- Correct the shoulder anchor based on screenshot: horizontal lateral offset
  **0.36 blocks**, slightly back **0.10 blocks**, and lower it to **1.16
  blocks** above player's feet (previously 0.5/0.04/1.4).
- Keep the original model size 0.42. No scale adjustments, no handmade
  torso/neck/wing/tail rotations, no animation changes while walking or flying.
- **Known limitation:** this uses the CE's sleeping pose as a curled *rest
  posture*; it may resemble a sleeping dragon. We still need a Zalith photo
  to verify the actual orientation and possible clipping into the player.
  If visually wrong, restore v0.5.4 and refine the anchor rather than adding
  more improvised skeletal angles.

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
unloaded *before installing v0.5.5*, you need to approach his last known
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

1. Remove earlier Auren JARs from `mods` and install the one for v0.5.5.
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
