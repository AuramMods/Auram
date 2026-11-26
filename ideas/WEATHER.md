# Auram weather

AuramCore runs weather as a continuous, server-authoritative field over the Overworld. A player can cross from dry air into drizzle and then into a downpour without crossing a biome boundary. The same field drives local gameplay, AuramBliss, Vapor, and the map. Weather extends over Voxy terrain even where regular Minecraft chunks are absent.

The Overworld is the first implementation. Profiles for other dimensions will be designed individually. The Nether could expose airborne ash in place of humidity and give that layer its own effects.

## Weather measurements

A weather cell covers one 16×16 block column. The worker samples its procedural fields at the center, `(chunkX * 16 + 8, chunkZ * 16 + 8)`. Cells are keyed by dimension and chunk coordinates. They are virtual and have their own lifetime. Players looking at the same position share the same weather.

Each measurement has its own seeded noise, spatial scale, and rate of change. Mix the world seed, dimension, and field ID to make the patterns reproducible and independent. Sample all cells in one pass against the same clock. The humidity cycle follows Minecraft time of day. Moving noise uses a continuous world clock so sleeping shifts the daily humidity target without jumping the fronts or the wind pattern.

| Measurement | Model |
| --- | --- |
| Wind | A signed horizontal vector. Take the curl of a domain-warped field of ring-shaped ridges so the flow follows broad, winding bands. Add a separately warped gust field with tighter bands and faster temporal variation. Where gusts are weak, wind changes smoothly over longer periods. Where the bands are strong, gusts can arrive while nearby places stay calm. Store vector components so the wind interpolates cleanly across compass directions. |
| Humidity | Use Forge's biome climate `downfall` to tune the typical humidity range and fluctuation amplitude. Make the daily curve lowest in early afternoon and highest in late evening. Add a separately seeded, drifting noise field and clamp to the calibrated biome range and to `[0, 1]`. Tune dry-biome caps, forest drizzle, and jungle showers from sampled distributions across many cells and days. |
| Pressure | A normalized, broad, drifting noise field with a different texture and cadence from humidity or wind. Low values correspond to stronger support for precipitation. Biome, temperature, and time of day have no direct input. |
| Temperature | Combine the sampled surface climate and elevation with the daily and seasonal adjustments and a small independent fluctuation. Normalize it for heat-driven effects such as dust. Retain a temperature suitable for deciding rain versus snow, using local altitude and Serene Seasons where applicable. |

### Precipitation

Compute precipitation from the chased humidity and pressure values so the displayed drivers explain the result. With humidity `H` and pressure `P` both in `[0, 1]`, zero pressure gives the strongest contribution.

```text
lowPressure = 1 - P
raw = (H * lowPressure)²
precipitation = clamp((raw - 0.25) / 0.75, 0, 1)
```

| Humidity | Pressure | Raw | Published precipitation |
| --- | --- | --- | --- |
| 0.15 | 0.00 | 0.0225 | 0 |
| 0.50 | 0.00 | 0.2500 | 0, the onset boundary |
| 0.50 | 0.10 | 0.2025 | 0 |
| 0.60 | 0.00 | 0.3600 | 0.1467 |
| 0.90 | 0.00 | 0.8100 | 0.7467 |
| 1.00 | 0.00 | 1.0000 | 1 |

Rain is possible when `H * (1 - P) > 0.50`. A desert capped at `H = 0.15` stays dry even at the lowest pressure. A biome intended to drizzle needs to spend some time just above the threshold. Wetter biomes can reach shower conditions more often, and pressure creates dry gaps and moving rain bands inside those biomes. The humidity bounds must include the full effect of the day cycle and fluctuations, so noise cannot accidentally lift a desert over its cap. Mixed-biome cells naturally blend at the boundary.

Derive dust from hot air, low humidity, and strong wind, with precipitation suppressing it. Humid air adds weaker haze. A tropical storm candidate combines strong wind, high humidity, and low pressure. Give storm onset a minimum duration and use different thresholds to enter and leave that state. That lets lightning, particles, and other consumers agree on a storm without flickering around a threshold.

## Terrain data without chunk activity

- **Capture four surface samples.** Use local block coordinates `(4,4)`, `(4,12)`, `(12,4)`, and `(12,12)`. Read the surface height and biome from the chunk or column already in hand. Keep all four samples so a riverbank or biome edge contributes in proportion. Store biome identifiers and surface elevation, and resolve the climate constants against the loaded modded biome registry.
- **Populate the baseline from existing terrain paths.** Capture new terrain when its full column is already available, including columns Voxy generates. For old terrain, attempt a bounded, read-only lookup of existing Voxy LOD0 surface and biome data where the original sample positions can be recovered. If that data is absent or too coarse, capture it on an ordinary server chunk load once the chunk is ready. Read from the supplied chunk directly. The weather system issues zero chunk loads, generation requests, tickets, or extra chunk ticks.
- **Track missing data.** A cell becomes eligible once it has a baseline. Until then the map shows an unknown cell, and gameplay has no procedural weather to apply there. Cache baselines by save and dimension. Reuse them after restart. Keep live field values and active-window bookkeeping in memory. Initialize a newly active cell from its current target and blend it into the client's view.
- **Keep the field independent of render distance.** A Voxy column can supply a baseline with no Minecraft chunk resident. The same 2D field continues across that column. The weather range is configurable independently of the near render distance, Voxy distance, and server simulation distance.

## Server scheduler and diagnostics

| Setting | Default | Range |
| --- | --- | --- |
| Player weather radius | 64 chunks | 5–256 chunks |
| Update interval and per-pass deadline | 1 second | 250 ms–10 seconds |
| Chase toward each target | 15% per second | 1–100% |

Take the union of the player-centered squares in each active dimension and update each eligible cell once per pass. A square uses `(2r + 1)²` cells, so radius 5 requests 121 cells, radius 64 requests 16,641, and radius 256 requests 263,169 for one isolated player. Multiple players increase work by the additional area they cover.

A single server-side worker visits cells in square rings, starting at the players and expanding by distance to the nearest player. Interleave equally near work across players or dimensions. Track covered tiles as players cross chunk boundaries so stationary players reuse their coverage and small moves only change the edges. The server thread hands off player positions and snapshots of the metadata. The worker owns the simulation and publishes versioned results without blocking the server thread.

Stop when the update interval ends, publish the cells finished so far, and restart at the centers at the next interval. Outer cells hold their last value if a pass cannot reach them. Maintain at most one active pass and discard obsolete scheduling work after a configuration or world change. Use `alpha = 1 - (1 - chasePerSecond)^intervalSeconds` for each completed update. A cell reached again after being skipped takes one configured interval's step. This preserves the requested chase without a large catch-up jump.

Expose these measurements through a debug readout and map layers.

- **Update completeness** is refreshed eligible cells divided by all eligible cells in the current requested area.
- **Climate coverage** is eligible cells divided by all requested cells. Reporting it separately shows why a pass can finish even while some terrain is unknown.
- **Update age** shows when each cell last refreshed. Also report requested and refreshed counts, wall time for the pass, worker time spent gathering or evaluating, and network bytes. These counters should make it obvious whether work is limited by missing baselines, CPU time, or delivery.

## Local weather and commands

Local weather is authoritative in the Overworld from the first playable version. Hook positional gameplay decisions onto the field, including whether rain reaches a position, snow and ice opportunities, and lightning eligibility. Where vanilla checks a dimension-wide rain or thunder bit while ticking a chunk, resolve the decision for that column instead. Apply exposure and block rules at the actual position and reuse vanilla's existing tick opportunities, effects, and probabilities where they fit. Keep Serene Seasons' rain-versus-snow influence and AuramCore's natural snow accumulation rules. Distant weather stays visible without ticking distant chunks.

Sleeping advances time as usual. Bypass the Overworld sleep path that clears the vanilla weather cycle so sleeping never resets the simulated storm. The new time of day moves the humidity target through its ordinary chase.

`/weather clear` is a personal shader override. It disables the weather-driven shader changes for the issuing player. Server weather, gameplay, other players, and the diagnostic map keep their current state. Keep the override separate from synchronized cell data and provide a client setting to resume shader weather. The initial command tree exposes only `/weather clear`. Local rain and storm gameplay continue even when a player has cleared their shader effects.

Reserve future commands such as `/weather affect humidity -15% [radius=5]`. A future implementation can feed a local modifier into humidity's target so changes propagate through the same chase and precipitation calculation. Define duration, permissions, whether the percentage is relative or additive, and how overlapping modifiers combine when implementing these commands.

## Networking and other layers

Transmit compact, versioned changed tiles under a byte budget, with nearby updates first. Include the tile origin, dimension and session scope, snapshot sequence, and validity. Send initial snapshots when joining or moving into a new area, and recover if a delta no longer has a base. Clear the old view on dimension change. A client can blend between received snapshots in time and sample between cell centers in space. Fade effects near missing data and at the weather view boundary. As a sizing example, a full uncompressed map at six bytes per cell would be about 98 KiB at radius 64 and 1.51 MiB at radius 256 per client. At a 250 ms cadence those costs would recur four times per second, so measure real changed-tile throughput before tuning the send budget.

Keep the built-in values in packed buffers and put optional layers behind namespaced IDs. A layer descriptor declares scalar or vector data, range, interpolation, display scale, and whether it is available to shaders or only to diagnostics. Providers can publish layer updates through a bounded API. Flux or radiation can then use the same world coordinates, transport, and overlays when their own integrations arrive. Add only shader-bound layers requested by the active shader to avoid spending Oculus sampler slots on debug data.

## Map and shader integrations

### FTB Chunks

Add a layer picker on the large map for wind, humidity, pressure, temperature, precipitation, storm intensity, update age, and any registered layers. Keep the selected layer visible on the minimap after closing the large map. Draw weather as a map-aligned one-pixel-per-cell texture, with optional blur and toggleable opacity. Use a direction color wheel for wind, brightness for speed, and sparse arrows when zoom permits. Show a legend and exact values under the cursor. Unknown terrain and stale values need distinct treatment. The installed FTB Chunks version exposes map icon hooks for both displays. A small version-specific draw adapter can put continuous textures below icons and claims while reusing the map's position, zoom, and clipping.

### AuramBliss and Vapor

- **Bindings.** Use AuramCore's existing Oculus sampler and uniform integration as the model for publishing a packed 2D weather texture, with signed wind, humidity, precipitation, and the other data used by active shader passes. Confirm sampler allocation in the full GLA/Oculus preset and share a world-space coordinate helper across normal, Voxy, and shadow passes. Use the current near and Voxy depth inputs and respect the active render scale. Audit uses of the vanilla global rain, thunder, and wetness uniforms so weather-dependent shading in the Overworld follows the local field. Use camera-local values for whole-view effects and world-position samples where the effect can vary across the scene. [AuramBliss renderer notes](../docs/AURAMBLISS.md)
- **Foliage and fog.** Bias leaf and grass motion in the wind direction, and vary wave speed and bend strength using a smooth animation phase. Keep visible and shadow geometry in sync. Add modest humid haze and a stronger, low-reaching dust component in hot, dry gusts. Drive them from the field along the ray so a nearby dust band can be visible from clear air. Stop integration at the same visible near or LOD surface used by the existing fog calculation.
- **Clouds.** Start by varying local cloud coverage and density with humidity and storms at the cloud sample's world position. Then try modest wind advection of the density noise. Keep a continuous phase to avoid jumps when the weather changes. Check cloud shadows and the cost of repeated texture reads in the raymarch.
- **Rain and snow.** Move the nearby vanilla precipitation draw and sound to local values for the initial playable version. Add dedicated shader precipitation afterward for both near and Voxy terrain. Use wind direction and speed to tilt rain and carry snow sideways. Depth-test against both renderers and use local surface exposure so roofs, caves, and terrain continue to occlude precipitation correctly. Switch off the overlapping vanilla visual where shader precipitation takes ownership. Treat falling precipitation, wet materials, and actual accumulation as separate effects driven by the same field. Apply the personal clear override to weather-driven shader changes, including foliage, fog, cloud, and precipitation adjustments.
- **Vapor.** Feed a gentle background wind into the existing connected-air solver on both CPU and compute paths. Weight the input by outdoor exposure and let existing vents and gust sources continue to influence local flow. Feed local humidity and precipitation into the existing weather-dependent erosion inputs where useful. The personal shader override changes the weather-driven presentation, while Vapor's simulation continues reading the shared world field.

## Delivery and validation

1. **Build the reusable field and fixture.** Use the Overworld's horizontal surface biome layout from a fixed seed or an existing region. Flatten land into a nine-block-thick grass-topped platform at a normal surface elevation, preserving the original desert, forest, and jungle bands and the horizontal footprint of oceans and rivers. Put water and shallow beds where the source map has them. Prepare the fixture separately from the weather system so the fixture's generation cannot be mistaken for weather issuing chunk tickets. Add a small roof, an underground room, and recognizable landmarks on both sides of the three-chunk handoff.
2. **Make the first playable Overworld loop.** Add the scheduler, baseline capture, local gameplay and sleep behavior, personal clear, tile sync, and basic large-map and minimap overlays. Add weather bindings, foliage, fog, and Vapor so we can tune the look against the field. Verify two players separated by one front get different gameplay and visuals, and one player's shader clear leaves the other player's view and the shared simulation alone.
3. **Extend the visual range and fidelity.** Add clouds, wind-angled shader rain and snow, and wetness where it should cross the regular-to-Voxy boundary. Compare clear, drizzle, hard rain, snowy high terrain, and dusty wind. Test covered and open areas, mixed near/LOD depth at the shoreline, shader reload, dimension changes, teleport, and reconnect. Use the full pack with GLA and the existing isolated replay for render comparisons. [AuramBliss replay tools](../tools/aurambliss-qa/README.md)
4. **Add storm consequences.** Start with stronger motion particles and audio. Falling trees and subtle destruction come later as opt-in gameplay. Work within existing ticking chunks and normal claim and modification checks. Bound the work and the number of changes per storm. Voxy continues to display the surrounding storm beyond the area where world changes can occur.

Benchmark one player, overlapping players, and separated players at the default and maximum ranges, including an intentionally too-short update interval. Verify that the simulation revisits nearby cells first, leaves skipped cells at their stored values, reports stale outer rings accurately, and issues zero chunk tickets. Check the precipitation formula at the threshold and at the desert and jungle extremes. Use the flat fixture to tune the distribution, then use a normal Overworld for rain and fog occlusion tests against real terrain.
