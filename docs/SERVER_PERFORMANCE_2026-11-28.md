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

### Initial copied-save replay at view 10 and simulation 10

Both arms loaded a private copy of the save with 570 reported mods, four JVM-visible processors, 8–12 GiB heap, and the pack's 15-worker pool. Each queued the same 96 fresh FULL requests. Two synthetic player positions were present during real dry and rainy datapack callbacks. Voxy generation remained active.

| Measurement | Published 1.0.77 | Queued fills at `9aac8d6` |
| --- | ---: | ---: |
| Measured ticks | 1,200 | 5,115 |
| p95 MSPT | 40.45 | 5.73 |
| p99 MSPT | 1,599.09 | 11.85 |
| p99.9 MSPT | 3,990.72 | 47.58 |
| Maximum MSPT | 21,974.87 | 204.06 |
| Tick counts above 50 / 100 / 250 / 1,000 ms | 54 / 39 / 35 / 20 | 5 / 1 / 0 / 0 |
| FULL became ready inside the callback | 35 of 35 | 0 of 77 |
| Median cold callback wall time | 1,152.70 ms | 0.18 ms |
| Fresh FULL chunks per second | 1.37 | 0.38 |
| p95 time until an individual chunk was ready | 5.79 s | 19.50 s |

The initial candidate removes the synchronous callback waits in this replay. The foreground generation rate was lower during the candidate, and its longer run gave Voxy more time to generate competing LOD chunks. The control also pumps the main-thread chunk task queue while the callback is waiting. A follow-up invokes the native scheduler for existing tickets without joining their futures. The next comparison needs to check throughput separately under a fixed amount of Voxy work.
