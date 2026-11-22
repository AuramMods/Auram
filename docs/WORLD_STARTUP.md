# World startup performance

The November 22 singleplayer capture covers the Create World screen, integrated server startup, login, and the first rendered frame. The initial datapack load took 13.82 seconds. The first request for a full Overworld spawn chunk took 28.66 seconds, then the initial player spawn waited another 4.83 seconds for a neighboring chunk. Forge's in-process config sync took about 12 seconds. JEI took 36.03 seconds after login, including 23.20 seconds registering recipes and 8.99 seconds building the search filter.

## Changes

- AuramCore skips Forge's server config payloads when the transport is the integrated server's in-process connection. Forge still negotiates the mod list and sends configs on remote transports.
- AuramCore defers the biome search around each concentric ring candidate until a placement query reaches that candidate's seven-chunk range. Full-list lookups, including structure location, start any remaining searches and keep vanilla ordering and candidate random sources. The original eager calculation is available with `-Dauram.worldgen.eagerStructureRings=true` for comparisons.
- Cyclic's JEI integration retains the Packager and machine categories and drops its duplicate registration of the standard crafting list. Psychedelicraft's fluid layouts use the installed JEI method for linking recipe slots.
- AuramCore fixes WorldEdit's property cache, JERM's missing repair ingredients, Mekanism More Machines' unavailable replicator map, JER's cartographer preview, Verdant's missing wood variants, and Upgradable Tools' smithing entries for absent output items.
- The AuramCore bundled sticks recipe uses the registered item ID. The pack fixes the Olympic Citadel's potion loot, Starbunclemania's optional crucible drop, Verdant's growth predicate names, optional Cyclic blood slime recipes, Evolved Mekanism's Ender IO alloying data, Nameless Trinkets' icons, and Terramity's advancement parent. Satiscraftory 1.1.3 removes the two retired conveyor loot tables. ProjectE still maps EMC and skips the per-recipe warning spam.

## Verification

The Forge 47.4.10 headless fixture used the reobfuscated AuramCore 1.0.26 artifact, ModernFix, Blueprint, TerraBlender, Thaumaturge, Atmospheric, Autumnity, Environmental, and the shipped 219-placement DnT datapack. All **876 ordered positions** matched the eager implementation across four ring seeds. The eager ring calculation took **4.17 to 6.12 seconds** per seed in that fixture. Each near-spawn membership check completed in under one millisecond. These are timings for the ring calculation and placement check.

The full Auram pack matched all 876 positions as well. The eager calculations took 10.2 to 13.0 seconds per seed in that run. The near-spawn checks again completed in under one millisecond. The full pack reached its first frame in 139 seconds on a fresh world and 91 seconds on reopen. The original capture used a different pack snapshot and world seed, so those two figures describe the validation run.

Forge's in-process handshake took 0.35 seconds in the full pack and sent zero config packets. The headless fixture verified that Forge still builds remote config payloads, and a loopback dedicated server sent the expected packets. End-to-end LAN and dedicated client login remain unverified.

Cyclic's JEI registration took 0.98 seconds on the first login and 0.55 seconds on reopen. The full client no longer emitted the targeted Cyclic, Psychedelicraft, WorldEdit, JERM, Mekanism More Machines, Verdant, or Upgradable Tools errors. ProjectE mapped 19,585 EMC values with exploit diagnostic logging disabled.

A separate fixture ran the final release jar with JEI, Just Enough Resources, Supplementaries, and Moonlight. JER registered its categories during both world loads without a cartographer error.

## Follow-ups

- The first full spawn chunk waited 14.1 seconds in the patched full pack, and the neighboring login chunk waited another 7.2 seconds. Ksyxis already skips bulk spawn preparation. Test prefetch of the actual spawn candidates with fixed seeds and the same pack build. Loading speculative candidates can put more work on the startup path.
- JEI still took 40.23 seconds on first login and 37.60 seconds on reopen. Flight Recorder found tooltip extraction in 305 of 358 samples that contained JEI index insertion during initial filter construction. Suffix-tree work appeared in 32 samples. These are sampled stacks across both logins. A change that preserves tooltip search should focus on extraction first and account for world switches, resource reloads, and searches made during indexing.
- Thaumaturge rebuilt its aspect index on both logins, with 0.53 and 0.86 seconds between its cache-miss and cache-write messages. The same-save reopen reported different recipe fingerprints in 14 groups, including Auram, Create, and Draconic Evolution. Trace which recipes change between the two loads before modifying cache invalidation.
- Distant Horizons initializes data stores and generator queues for all loaded server dimensions. Benchmark with distant generation disabled in the fixture to distinguish queue activity from database work. Lazy database initialization needs to preserve chunk hooks, a player's first visit to a dimension, and shutdown ordering. DH 3.3.3 also moved `WorldGenerationQueue`, so the older AuramCore queue-priority mixin no longer reaches that target. Verify the new queue's scheduling before porting it.
- The remaining datapack warnings include Malum's incomplete generated recipe unlock definitions, unresolved optional tag entries, Apotheotic Additions' loot rules, missing Curios slots, and Tinkers' recipe edge cases. Each needs a source-specific correction or a balance decision about the intended outcome.
- Redirected and ModernFix intercept the same block-state cache allocation. The observed run used ModernFix's implementation. Flywheel's fallback and DH's OpenGL and custom-generator warnings require their own graphics or dimension tests. TT20's singleplayer warning needs a separate A/B test of its tick acceleration behavior.
