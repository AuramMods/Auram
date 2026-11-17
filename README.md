# Auram

## Publishing a pack update

Requires Git, Python 3.10+, and push access to this repository. Commit the pack
changes first, then run from the repository root:

```sh
python3 scripts/release.py
```

The script fetches release tags (and full history when starting from a shallow
clone), compares the committed pack against its nearest reachable release tag,
and creates one release commit containing `auram.json` and
`changelist/<build>.json`. The build is the total Git commit count **including
that release commit**. It creates an annotated numeric tag such as `139` and
pushes the current branch and tag together to `origin`, without force. Existing
`1.x.x` release tags remain valid delta bases.

Other staged, unstaged, and untracked pack changes stay local until you commit
them. The release commit includes only its two metadata files. Existing fields
in `auram.json` are preserved while `version` and `changelist` are updated:

```json
{
  "version": 139,
  "changelist": "changelist/139.json"
}
```

Preview the delta without fetching, writing release files, committing, or
pushing:

```sh
python3 scripts/release.py --dry-run
```

If the push fails, the local release commit and tag remain available. Resolve
the Git error, then publish that same release without making another version:

```sh
python3 scripts/release.py --push-only
```

Use `--remote <name>` to fetch and publish through a different Git remote. Push
rejection leaves both remote refs unchanged because the push is atomic.

## Changelist format

Each manifest describes the net difference from the preceding release to this
release, with paths relative to the repository root:

```json
{
  "schema": 1,
  "version": 139,
  "previousVersion": "1.0.7",
  "previousCommit": "<previous tagged commit>",
  "contentCommit": "<committed pack before the release metadata commit>",
  "changes": [
    "+auram.json",
    "+changelist/139.json",
    "*minecraft/config/example.toml",
    "-minecraft/mods/old-mod.jar"
  ]
}
```

`+` means created, `*` means changed, and `-` means deleted. Entries are sorted
by path. Binary files are included; a rename is a deletion and an addition.
Changes reverted before the release are omitted. The manifest includes its own
path and `auram.json`, so it covers the final release commit as well as the pack
commits. Without an earlier release tag, the previous fields are `null` and all
tracked files are additions.

These manifests supply the data for incremental launcher updates; the launcher
still needs to consume them. It can follow `previousVersion` through multiple
manifests when an installed pack skips builds, download added/changed paths
from each target tag, and remove deleted paths. Installations whose baseline
predates these manifests need a full manifest/tree comparison as a fallback.
The existing launcher's tag discovery already accepts numeric build tags.

Run the release integration tests with:

```sh
python3 -B -m unittest discover -s tests -v
```

## Mod update checks

Pack updates are distributed through the launcher. `minecraft/config/fml.toml`
sets `versionCheck = false`, stopping Forge's update requests for all mods that
register an `updateJSONURL`, including bundled dependencies.

Independent checks are disabled in Distant Horizons, Cyclops Core, Common
Capabilities, Colossal Chests, Integrated Dynamics, Integrated Crafting,
Integrated Terminals, and Dynamic Surroundings. Mods Optimizer uses its local
database instead of downloading database updates. Additional Structures,
Chunks Fade In, Oculus, Enigmatic Legacy, Majrusz Library, and Immersive
Engineering also have their update checks/notices disabled in their configs.
JourneyMap's `checkUpdates` and TT20's `automatic-updater` were already disabled
and remain so.

AuramCore 1.0.4 cancels KubeJS 2001.6.5-build.16's unconditional startup update
request, which has no configuration switch. Its script lifecycle still runs.
The server pack includes the same configs and Auram jar through
`server_config.conf`. Restart the client/server after installing an update to
apply the startup settings.
