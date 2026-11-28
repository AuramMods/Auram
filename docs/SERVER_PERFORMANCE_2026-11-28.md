# Auram server performance, November 28, 2026

The dedicated-server Spark capture `WLdGZeT5Hr.sparkprofile` covered ten minutes with two players and four logical CPUs. The server used Forge 47.4.10, AuramCore 1.0.77, Neo-Voxy 0.4.23-beta.1, and view and simulation distances of 10. Minute medians ranged from 21.9 to 33.6 ms. The two largest recorded ticks were 10.25 s and 13.24 s. The minute-resolution samples cannot assign an exact caller to the 13.24 s tick.

## Better Snowy Biomes

The shipped 2.5.1 datapack's `better_snowy_biomes:main` schedules `fill_normal/fill0` through `fill511`, or the corresponding `fill_snowy` batch IDs, according to the weather predicate. A dry batch has 8 player-relative `/fillbiome` commands and a rainy batch has 40. The capture attributed 39.2 sampled seconds to `/fillbiome` parked on chunk futures. Forge's command requests a FULL chunk with creation disabled, which can still wait when the chunk has an existing ticket.

AuramCore commit `9aac8d624f2c2fedf382a41a4505114db3d14f5b` captures those scheduled fills and runs their original edits after all affected chunks are ready. The queue keeps the order of intersecting edits and caps its work per tick. It drops edits if their chunks lose the ticket and clears old predicates after a datapack reload. The same command still handles replacements, dirty state, and client biome refreshes.

The initial fixture used production-mapped jars on Forge 47.4.10 with Java 17 and four JVM-visible processors. Both arms used view 10 and simulation 10 and the same installed datapack. Two player positions contributed overlapping requests. The fixture verified the actual dry and rainy batch IDs, a weather transition, a resource reload, unchanged quart cells, and a synchronous admin `/fillbiome` result.

| Targeted cold border fixture | Published 1.0.77 | Candidate |
| --- | ---: | ---: |
| Pending FULL chunks completed inside the scheduled callback | 6 of 6 | 0 of 6 |
| Median callback wall time | 7.35 ms | 0.47 ms |
| Maximum callback wall time | 28.11 ms | 8.12 ms |
| Biome replacements after chunks became ready | Pass | Pass |

The candidate test rejects a synchronous chunk lookup at the pending target during both the initial callback and deferred replay. These six trials measure command-path latency. Full-pack tick percentiles and throughput are measured separately.
