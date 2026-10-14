# Auren Minecraft — v0.5.0-alpha (Minecraft 1.21.1 / NeoForge)

## Original IceAndFire CE lightning-dragon animations

**No hand-authored walking or wing-flapping animation.** The renderer now uses
the **original IceAndFire Community Edition Tabula assets** for the Lightning Dragon:

- `walk1`–`walk4`: the four source keyframes for ground locomotion, interpolated
  according to movement speed and limb cycle.
- `flight1`–`flight6`: the six source wing-flap keyframes.
- `flying`, `hovering` and `diving`: official source poses used as appropriate
  during jumps/falls, preparing for a future flight movement system.
- `ground` remains the default pose while standing.
- **Shoulder/neck pose is preserved**, including the v0.4's 20-tick transition
  and the custom living-scarf adjustment approved by the player.
- Baby scale remains **0.42**, adult scale remains **5.8**.

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

1. Remove earlier Auren JARs from `mods` and install the one for v0.5.
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
