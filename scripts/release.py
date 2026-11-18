#!/usr/bin/env python3
"""Publish a commit-count Auram release and a delta from its previous release."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
RELEASE_TAG = re.compile(r"v?\d+(?:\.\d+){0,2}\Z")


def git(root: Path, *args: str, input: bytes | None = None) -> bytes:
    return subprocess.run(
        ["git", *args], cwd=root, input=input, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=True,
    ).stdout


def git_text(root: Path, *args: str) -> str:
    return git(root, *args).decode("utf-8").strip()


def fetch_releases(root: Path, remote: str) -> None:
    if git_text(root, "rev-parse", "--is-shallow-repository") == "true":
        git(root, "fetch", "--unshallow", "--tags", remote)
    else:
        git(root, "fetch", "--tags", remote)


def previous_release(root: Path, head: str) -> tuple[str | None, str | None]:
    # Only reachable release tags count. An unrelated branch's larger build
    # number must not become this release's delta base.
    tags = [
        name for name in git_text(
            root, "for-each-ref", f"--merged={head}",
            "--format=%(refname:strip=2)", "refs/tags",
        ).splitlines() if RELEASE_TAG.fullmatch(name)
    ]
    if not tags:
        return None, None
    matches = [arg for tag in tags for arg in ("--match", tag)]
    tag = git_text(root, "describe", "--tags", "--abbrev=0", *matches, head)
    return tag, git_text(root, "rev-parse", f"refs/tags/{tag}^{{commit}}")


def changed_paths(root: Path, base: str, head: str) -> dict[str, str]:
    # NUL delimiters preserve spaces, tabs, newlines and Unicode filenames.
    # Renames are deliberately represented by deletion + creation.
    fields = git(root, "diff", "--name-status", "--no-renames", "-z", base, head).split(b"\0")
    fields.pop()  # Git terminates its output with NUL (or returns b"").
    result = {}
    markers = {"A": "+", "D": "-", "M": "*", "T": "*"}
    for index in range(0, len(fields), 2):
        status = fields[index].decode("ascii")
        path = fields[index + 1].decode("utf-8")
        result[path] = markers[status]
    return result


def read_metadata(root: Path, head: str) -> dict:
    path = root / "auram.json"
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
    elif git(root, "ls-tree", head, "--", "auram.json"):
        data = json.loads(git(root, "show", f"{head}:auram.json"))
    else:
        data = {}
    if not isinstance(data, dict):
        raise ValueError("auram.json must contain a JSON object")
    return data


def plan_release(root: Path) -> dict:
    head = git_text(root, "rev-parse", "HEAD")
    # The release adds exactly one commit, so the version equals the count at
    # the resulting tagged commit, rather than lagging behind it by one.
    version = int(git_text(root, "rev-list", "--count", head)) + 1
    tag, commit = previous_release(root, head)
    base = commit or git(root, "mktree", input=b"").decode("ascii").strip()
    changes = changed_paths(root, base, head)
    changelist = f"changelist/{version}.json"
    base_paths = set(git(root, "ls-tree", "-r", "--name-only", "-z", base).decode("utf-8").split("\0"))
    for path in ("auram.json", changelist):
        changes[path] = "*" if path in base_paths else "+"

    metadata = read_metadata(root, head)
    metadata.update(version=version, changelist=changelist)
    manifest = {
        "schema": 1,
        "version": version,
        "previousVersion": tag,
        "previousCommit": commit,
        "contentCommit": head,
        "changes": [changes[path] + path for path in sorted(changes)],
    }
    return {"version": version, "changelist": changelist, "metadata": metadata, "manifest": manifest}


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def create_release(root: Path, plan: dict) -> None:
    write_json(root / "auram.json", plan["metadata"])
    write_json(root / plan["changelist"], plan["manifest"])
    paths = ("auram.json", plan["changelist"])
    git(root, "add", "--", *paths)
    # --only also leaves unrelated staged changes out of the release commit.
    git(root, "commit", "--only", "-m", f"Release Auram build {plan['version']}", "--", *paths)
    git(root, "tag", "-a", str(plan["version"]), "-m", f"Auram build {plan['version']}")


def push_release(root: Path, remote: str, version: str) -> None:
    branch = git_text(root, "symbolic-ref", "--short", "HEAD")
    # Publish branch + tag together. A rejected ref cannot expose half a release.
    # Resolve the tag so a retry cannot publish later, unreleased local commits.
    git(root, "push", "--atomic", remote, f"refs/tags/{version}^{{commit}}:refs/heads/{branch}", f"refs/tags/{version}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--remote", default="origin", help="Git remote to fetch and publish (default: origin)")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="Preview local committed changes without fetching or releasing")
    mode.add_argument("--push-only", action="store_true", help="Retry publishing the existing committed version and tag")
    mode.add_argument("--build-bundles", metavar="TAG", help="Build signed partial distribution assets locally for an existing tag")
    mode.add_argument("--publish-bundles", metavar="TAG", help="Build/verify/publish distribution assets for an existing tag without a new release commit")
    parser.add_argument("--output", type=Path, help="Local distribution output directory")
    parser.add_argument("--signing-key", type=Path, help="Private pack signing key outside Git")
    parser.add_argument("--previous-catalog", type=Path, help="Verified immediate predecessor catalog for immutable payload reuse")
    parser.add_argument("--git-only", action="store_true", help="Explicit legacy Git tag/changelist publication without distribution assets")
    args = parser.parse_args()

    try:
        if args.build_bundles or args.publish_bundles:
            fetch_releases(ROOT,args.remote)
            version=args.build_bundles or args.publish_bundles
            distribution(args,version,publish=bool(args.publish_bundles))
            return 0
        if args.push_only:
            version = str(json.loads(git(ROOT, "show", "HEAD:auram.json"))["version"])
        else:
            if not args.dry_run:
                fetch_releases(ROOT, args.remote)
            plan = plan_release(ROOT)
            version = str(plan["version"])
            previous = plan["manifest"]["previousVersion"] or "empty repository"
            print(f"Auram build {version}; changes since {previous}:", flush=True)
            for entry in plan["manifest"]["changes"]:
                print(entry, flush=True)
            if args.dry_run:
                print("Dry run: no release files, commits, tags or pushes created.")
                return 0
            create_release(ROOT, plan)
            print(f"Created release commit and tag {version}.", flush=True)

        push_release(ROOT, args.remote, version)
        if not args.git_only:
            distribution(args,version,publish=True)
        print(f"Published Auram build {version} to {args.remote}.")
        return 0
    except subprocess.CalledProcessError as error:
        print(f"Release command failed: {' '.join(error.cmd)}", file=sys.stderr)
        detail=error.stderr or error.stdout or ""
        print((detail.decode("utf-8",errors="replace") if isinstance(detail,bytes) else detail).strip(), file=sys.stderr)
        print("Existing local release files/commits/tags are retained. If only publication failed, resolve the Git error and rerun with --push-only.", file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        return 1


def distribution(args,version: str,publish: bool) -> None:
    from bundle_build import build, tagged
    from bundle_contract import safe_path
    from bundle_publish import gh
    key=args.signing_key or Path(os.environ.get("AURAM_PACK_SIGNING_KEY_FILE",Path.home()/".config/auram/pack-signing/pack.key"))
    output=args.output or ROOT/".auram-distribution"/version
    previous=args.previous_catalog
    target=tagged(ROOT,version)
    metadata=json.loads(git(ROOT,'show',f"{target['sha']}:auram.json"))
    if previous is None and 'changelist' in metadata:
        safe_path(metadata['changelist'])
        delta=json.loads(git(ROOT,'show',f"{target['sha']}:{metadata['changelist']}"))
        base=delta.get('previousVersion')
        if base:
            tagged(ROOT,base)
            cached=ROOT/'.auram-distribution'/'catalogs'/base
            cached.mkdir(parents=True,exist_ok=True)
            if (cached/'auram-pack.json').is_file():
                previous=cached/'auram-pack.json'
            elif publish:
                try:
                    gh('release','download',base,'--pattern','auram-pack.json','--dir',str(cached),'--clobber')
                    previous=cached/'auram-pack.json'
                except subprocess.CalledProcessError as error:
                    message=str(error.stderr).lower()
                    if not any(text in message for text in ('release not found','no assets match','http 404')): raise
    info=build(ROOT,version,output,key,previous)
    print(json.dumps(info,indent=2),flush=True)
    if publish:
        from bundle_publish import publish as publish_assets
        print(json.dumps(publish_assets(ROOT,version,output),indent=2),flush=True)


if __name__ == "__main__":
    sys.exit(main())
