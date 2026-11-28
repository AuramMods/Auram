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

### Initial copied-save replay at view 5 and simulation 10

This comparison used the shipped view and simulation distances with the same 96-request fixture, CPU limit, mod list, and heap as the view-10 run.

| Measurement | Published 1.0.77 | Queued fills at `9aac8d6` |
| --- | ---: | ---: |
| Measured ticks | 1,200 | 9,318 |
| p95 MSPT | 11.22 | 12.22 |
| p99 MSPT | 1,239.03 | 40.26 |
| p99.9 MSPT | 11,225.72 | 120.72 |
| Maximum MSPT | 17,273.73 | 444.15 |
| Tick counts above 50 / 100 / 250 / 1,000 ms | 42 / 37 / 36 / 16 | 69 / 16 / 2 / 0 |
| FULL became ready inside the callback | 36 of 36 | 0 of 70 |
| Median cold callback wall time | 965.75 ms | 0.22 ms |
| Fresh FULL chunks per second | 1.32 | 0.21 |
| p95 time until an individual chunk was ready | 17.23 s | 41.25 s |

The queue removed the multi-second stalls at view 5 as well. The candidate ran for more ticks, and p95 rose by 1 ms. Across the Voxy reporting intervals the candidate generated 179 background columns and the control generated 110. Both the callback timing and background coverage varied, and the foreground block fingerprints differed in both view-distance comparisons. The targeted fixture verified the actual biome replacements. A separate replay now fixes the batch cadence and removes Voxy background terrain work from both arms to test foreground chunk readiness.

The follow-up in AuramCore `d02ae2f` asks the main-thread chunk scheduler to start the already-ticketed work without joining a future. Its six cold-border cases passed all the original replacement and reload checks, including the guard against synchronous waits. Median callback time was 0.30 ms and the maximum was 4.41 ms.

### Fixed-batch foreground replay at view 10 and simulation 10

Both arms used the same save copy, 24 batches of four FULL requests, one cold callback per batch, and no Voxy background terrain work. The queue waited for a batch to complete before submitting the next four requests. The harness also serialized 128 test entities on each measured tick.

| Measurement | Published 1.0.77 | Queued fills and native scheduling |
| --- | ---: | ---: |
| Measured ticks | 1,200 | 3,250 |
| p95 / p99 / p99.9 MSPT, all measured ticks | 5.46 / 1,199.52 / 3,684.06 | 14.93 / 45.65 / 112.46 |
| p95 / p99 / p99.9 MSPT, first 1,200 ticks | 5.46 / 1,199.52 / 3,684.06 | 28.64 / 71.41 / 298.27 |
| Maximum MSPT | 13,378.25 | 411.00 |
| Tick counts above 50 / 100 / 250 / 1,000 ms, all measured ticks | 26 / 24 / 22 / 13 | 28 / 5 / 2 / 0 |
| FULL became ready inside the callback | 24 of 24 | 0 of 24 |
| Median cold callback wall time | 1,195.82 ms | 0.30 ms |
| Fresh FULL chunks per second | 1.71 | 0.59 |
| p95 time until an individual chunk was ready | 4.00 s | 13.06 s |

The native scheduling change kept the large tick spikes out of both the shared and full measurement windows. Foreground throughput remained lower with Voxy background work removed. The published callback pumps the main-thread task queue while it waits. The two arms also completed different amounts of per-tick entity work while generation was active and produced different block fingerprints. The measured benefit is tick responsiveness, with a throughput tradeoff in this replay.

## Tom's Simple Storage

The installed 1.7.1 terminal passed 60 paired update cases, including 61 packet comparisons, both oversized-item fallbacks, two live viewers, a hopper insertion, and a removal. Across 250 measured synthetic updates with 128 powered Mekanism items, AuramCore's existing sync fast path reduced Forge provider comparisons from 96,500 to 64,250 and median time for ten updates from 3.41 to 2.88 ms. The live terminal fixture took a median of 10.03 ms for 300 rebuilds of a 26-slot snapshot.

The live chest fixture also changed a capability value in place. The slot retained the same stack object, item, count, and ordinary NBT, while Tom's original equality distinguished the two snapshots. Further rebuild caching is deferred because these inventory properties cannot invalidate the cache when a provider changes.

## Datapack tick functions

AuramCore `4fdff4e` adds the opt-in `auram.DatapackFunction` JFR event. It captures the parent function ID, tick number, duration, and command count. A nested call contributes to its enclosing invocation. The fixture verified event IDs and exact command counts for the installed Guns, Scorched, and Incendium functions.

In the repeatable fixture with 32 armor stands and Guns item entities, the median cost per direct invocation was 0.521 ms for `ggunz:loop`, 0.169 ms for `incendium:clocks/main`, and 0.062 ms for `scorched:tick`. The Guns loop's lightning-rod command had the most repeated global selector passes in the prior command trace for this workload. The candidate checks the block before it expands the second selector.

With 12 matching item entities, the original and candidate commands returned the same result and set invulnerability on the same items with zero, one, and two active lightning rods. The installed function also passed the conversion, consumption, projectile, scoreboard, and offhand checks. The timing below measures the single command with eight calls per batch. The scan count covers 72 calls per case.

| Active rods | Original median | Optimized median | Original global scans | Optimized global scans |
| --- | ---: | ---: | ---: | ---: |
| 0 | 7.35 ms | 0.26 ms | 2,808 | 216 |
| 1 | 8.35 ms | 2.65 ms | 3,672 | 1,296 |
| 2 | 6.31 ms | 3.37 ms | 4,536 | 2,376 |

## Voxy section encoding

The capture used Neo-Voxy 0.4.23-beta.1. The server and section-storage code matched 0.4.24-beta.1, which serves as the control for this change. The candidate writes bulk big-endian voxel data and reuses the deflater and buffers within each column transaction. It keeps the zlib level and stream write boundaries. SQLite uses compressed blob equality to determine whether a section revision changes.

The encoder replay used 80 real cached sections sampled equally from LOD levels 0 through 4. It checked every sample against the original stream and the on-disk entry. The parallel stage encoded 560 sections per arm with four workers and alternated order across five rounds.

| Encoder measurement | Current stream | Batched at current level | Batched at best speed |
| --- | ---: | ---: | ---: |
| Compressed bytes for the 80 samples | 336,806 | 336,806 | 614,469 |
| Median single-thread CPU per 240 sections | 697.52 ms | 624.00 ms | 122.43 ms |
| Median combined worker CPU per 560 sections | 1,817.29 ms | 1,575.27 ms | 311.17 ms |
| Median p95 queue delay | 451.55 ms | 388.96 ms | 77.08 ms |
| Median encoder throughput | 1,133.95 sections/s | 1,339.03 sections/s | 6,620.84 sections/s |

The default-level candidate reproduced the 80 stored blobs exactly. Best-speed compression increased sample size by 82.4% and changed the compressed bytes used to track revisions. The candidate retains the original compression level. A separate replay measures column commits on Voxy's SQLite writer, including on-disk size and revisions.

The SQLite replay ran the original writer alongside Voxy commit `61c81246` using a single storage worker and up to eight queued jobs. Both arms committed 160 columns per round, with eight sampled sections per column. The warmup was excluded, and the order alternated across five measured rounds on a JVM configured with four CPUs.

| Median SQLite writer measurement | Current | Batched |
| --- | ---: | ---: |
| Worker CPU per 160 columns | 2,195.53 ms | 1,951.78 ms |
| p95 queue delay | 111.75 ms | 101.51 ms |
| Committed LOD columns per second | 72.09 | 80.21 |
| On-disk database bytes | 1,187,840 | 1,187,840 |

Every round finished with identical stored section data and revisions. The production storage test also read legacy data in all three coverage modes, retained existing revisions for unchanged sections, and incremented only a changed section. These timings cover the compression and commit path. Terrain generation and extracting voxel data are upstream workloads.

## Lithostitched jigsaw piece collision

The experiment in AuramCore `c1547db` tracks accepted integer boxes separately and checks quarter-block-inset candidate boxes against them. It still checks the original enclosing shape and uses the stock voxel operation for unsupported geometry.

Across 1,728 structure-start cases on three fixed seeds, both arms produced 11 accepted starts and 674 rejected attempts. Ordered piece IDs, positions, rotations, saved data, and random draws matched. All three complete-chunk fingerprints matched, including the selected village-start chunk.

The structure-start workload took 1.73 s in the control and 1.67 s with the experiment. The three complete-chunk tests took a median of 2.82 s in the control and 3.28 s with the experiment.

The 96-request copied-save run used the Snowy Biomes change in both arms, four JVM-visible CPUs, view 10, simulation 10, and no Voxy background terrain work. The experiment completed 1.16 FULL chunks/s versus 0.59. Individual p95 readiness fell from 13.06 s to 5.00 s, and p95, p99, and p99.9 MSPT were 8.48, 24.47, and 106.45 ms. The worst tick was 114.25 ms, with 3 / 2 / 0 / 0 ticks above 50 / 100 / 250 / 1,000 ms and zero synchronous Snowy Biomes waits. The complete block fingerprints differed in this workload.

The release leaves the collision change on its experimental commit. The targeted complete chunks were slower, and the single larger replay used different resulting terrain. A repeatable full-pack comparison around several fixed structure starts would help isolate the geometry change.
