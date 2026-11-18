# Local pack distribution builds

The producer uses Git trees and a single size-framed `cat-file --batch` process.
It never packages the live Prism working directory. Required tools are Python
3.10+, Git, and Minisign. No GitHub Actions job builds pack assets.

For an existing released tag (including tags predating these tools), the main
release entry point builds locally or backfills/publishes with no new version:

```sh
python3 scripts/release.py --build-bundles 161
python3 scripts/release.py --publish-bundles 161
```

Output/cache lives in ignored `.auram-distribution/`. `--output`, `--root`, and
`--previous-catalog` support separate artifact directories, source repositories,
and reuse of a verified predecessor distribution. The lower-level builder also
supports `python3 scripts/bundle_build.py --tag 161` without a remote fetch.
Signing uses
`AURAM_PACK_SIGNING_KEY_FILE` or `~/.config/auram/pack-signing/pack.key`.
`--signing-key` (`--key` in the lower-level builder) explicitly selects another
private file; it must match `distribution/pack.pub`.

Normal `python3 scripts/release.py` retains its existing metadata commit, numeric
tag and atomic push, then builds/publishes locally. `--push-only` resumes that same
release's Git and asset stages. `--dry-run` still performs no build, fetch, write,
commit, tag, or upload. `--git-only` is an explicit compatibility mode.
Publication requires authenticated `gh`. A published predecessor catalog is reused
when available. Referenced predecessor assets are verified before the new draft
can become public. Partial uploads remain drafts; retries can replace only draft
assets. Completed releases are read back and verified without rewriting them.

Keep the private directory mode 0700 and private key mode 0600. Back up the key
securely outside Git; never paste it into logs, fixtures, or repository secrets.
An unencrypted key relies on those host permissions. Public keys and signed
test fixtures are safe to commit; fixture private keys are discarded.

For a new installation, create a key with `minisign -G -W -s <private-file>
-p <public-file>` and install only the public file as `distribution/pack.pub` and
the launcher's `src-tauri/pack.pub`. Do not regenerate an existing release key.
To rotate, first ship launcher support for the new trust root while retaining
the old key for existing cached releases; then switch the producer and publish
new signed distributions. Never silently replace a published release's signer.

The signed catalog contains a full selected inventory and up to 64 verified
before/after release transitions. Partial ZIP payloads contain only distinct
objects introduced since the immediate predecessor. Identical jar renames need
no archive body. Objects at least 1 MiB have independent archives; smaller ones
share groups bounded by 1 MiB of uncompressed content. Archives stay below the
2,000,000,000-byte distribution bound. Cached/inherited payloads retain immutable
release URLs. Full snapshots and missing unbundled objects use pinned sparse Git.

Archives use fixed ordering, modes and 1980 timestamps. Catalogs use canonical
JSON and fixed Minisign comments, so identical inputs/key/producer identity yield
identical artifacts. Git blob hashes and SHA-256 verify cached bytes on reuse.
`SHA256SUMS` covers the public assets; `build.json` reports selected/new/reused
counts and payload sizes. Nothing in this command creates a pack version or tag.
