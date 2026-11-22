# World startup performance

The November 22 singleplayer capture covers the Create World screen, integrated server startup, login, and the first rendered frame. The initial datapack load took 13.82 seconds. The first request for a full Overworld spawn chunk took 28.66 seconds, then the initial player spawn waited another 4.83 seconds for a neighboring chunk. Forge's in-process config sync took about 12 seconds. JEI took 36.03 seconds after login, including 23.20 seconds registering recipes and 8.99 seconds building the search filter.

## Changes

- AuramCore skips Forge's server config payloads when the transport is the integrated server's in-process connection. Forge still negotiates the mod list and sends configs on remote transports.
- AuramCore defers each concentric ring's biome search until a placement query reaches that candidate's seven-chunk range. Full-list lookups, including structure location, start any remaining searches and keep vanilla ordering and candidate random sources. The original eager calculation is available with `-Dauram.worldgen.eagerStructureRings=true` for comparisons.
- Cyclic's JEI integration retains the Packager and machine categories and drops its duplicate registration of the standard crafting list. Psychedelicraft's fluid layouts use the installed JEI method for linking recipe slots.
- AuramCore fixes WorldEdit's property cache, JERM's missing repair ingredients, Mekanism More Machines' unavailable replicator map, JER's cartographer preview, Verdant's missing wood variants, and Upgradable Tools' smithing entries for absent output items.
- The pack fixes the Olympic Citadel's potion loot, Starbunclemania's optional crucible drop, Verdant's growth predicate names, optional Cyclic blood slime recipes, Evolved Mekanism's Ender IO alloying data, Nameless Trinkets' icons, and Terramity's advancement parent. Satiscraftory 1.1.3 removes the two retired conveyor loot tables. ProjectE still maps EMC and skips the per-recipe warning spam.

## Follow-ups

- Benchmark spawn again after the ring optimization. Ksyxis already skips bulk spawn preparation. A prefetch of the actual player-spawn candidate needs enough work between identifying and requesting the chunk to offset its complexity. Prefetching speculative spawn chunks can move more work onto startup.
- Keep JEI tooltip search available. The hidden client harness can record the index build with Flight Recorder and disable tooltip search in its disposable fixture. Any incremental index needs to account for world switches, resource reloads, and searches made before indexing finishes.
- Thaumaturge invalidates its persisted aspect index on item and recipe changes. The captured miss alone does not justify weakening the fingerprint. A repeated miss on an unchanged reopen would warrant comparing the reported input groups and patching the unstable source.
- Distant Horizons initializes data stores and generator queues for all loaded server dimensions. Benchmark with distant generation disabled in the fixture to distinguish queue activity from database work. Lazy database initialization needs to preserve chunk hooks, a player's first visit to a dimension, and shutdown ordering.
- The remaining datapack warnings include Malum's incomplete generated recipe unlock definitions, unresolved optional tag entries, Apotheotic Additions' loot rules, missing Curios slots, and Tinkers' recipe edge cases. Each needs a source-specific correction or a balance decision about the intended outcome.
- Redirected and ModernFix intercept the same block-state cache allocation. The observed run used ModernFix's implementation. Flywheel's fallback and DH's OpenGL and custom-generator warnings require their own graphics or dimension tests. TT20's singleplayer warning needs a separate A/B test of its tick acceleration behavior.
