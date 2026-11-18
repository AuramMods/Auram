"""Publish locally verified pack assets as one complete immutable GitHub release."""
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile

from bundle_build import digest, tagged, verified_envelope, verify_archive

REPOSITORY="AuramMods/Auram"


def gh(*args: str) -> str:
    return subprocess.run(["gh",*args,"--repo",REPOSITORY],check=True,capture_output=True,text=True).stdout.strip()


def release(tag: str | None, run=gh) -> dict | None:
    args=["release","view"]
    if tag is not None: args.append(tag)
    args.extend(["--json","tagName,isDraft,isPrerelease,targetCommitish,url,assets"])
    try: return json.loads(run(*args))
    except subprocess.CalledProcessError as error:
        if "release not found" in str(error.stderr).lower(): return None
        raise


def verify_local(directory: Path, expected: dict) -> tuple[dict,list[str]]:
    tools=Path(__file__).resolve().parents[1]
    policy=json.loads((tools/"distribution/policy.json").read_bytes())
    document=verified_envelope(directory/"auram-pack.json",tools/"distribution/pack.pub",policy)
    if document["target"] != expected: raise ValueError("Distribution identifies a different tag/commit")
    prefix=f"https://github.com/{REPOSITORY}/releases/download/{expected['name']}/"
    files=["auram-pack.json","SHA256SUMS"]
    for sha,bundle in document["bundles"].items():
        if bundle["url"].startswith(prefix):
            name=f"pack-{sha}.zip"; path=directory/name
            verify_archive(path, bundle)
            files.append(name)
    expected_sums="".join(f"{digest(directory/name)}  {name}\n" for name in sorted(files) if name != "SHA256SUMS")
    if (directory/"SHA256SUMS").read_text() != expected_sums: raise ValueError("Published checksum set is incomplete or inconsistent")
    return document, sorted(files)


def verify_remote(tag: str, target: dict, run=gh) -> tuple[dict,dict]:
    state=release(tag,run)
    if state is None: raise ValueError("GitHub release disappeared during publication")
    if state["tagName"] != tag: raise ValueError("GitHub returned a different release tag")
    with tempfile.TemporaryDirectory(prefix="auram-pack-published-") as directory:
        run("release","download",tag,"--dir",directory,"--clobber")
        document,files=verify_local(Path(directory),target)
        if sorted(asset["name"] for asset in state["assets"]) != files: raise ValueError("GitHub release asset set is incomplete or unexpected")
        for asset in state["assets"]:
            if asset["size"] != (Path(directory)/asset["name"]).stat().st_size: raise ValueError("GitHub asset size disagrees with the downloaded body")
    return state,document


def publish(root: Path, tag: str, directory: Path, run=gh) -> dict:
    target=tagged(root,tag)
    document,files=verify_local(directory,target)
    existing=release(tag,run)
    if existing is not None:
        # target_commitish is a creation hint and can legitimately be "main".
        # The fetched Git tag and signed downloadable inventory are authoritative.
        if existing['tagName'] != tag: raise ValueError('GitHub returned a different release tag')
        if not existing["isDraft"]:
            state,public=verify_remote(tag,target,run)
            if public["files"] != document["files"]: raise ValueError("Published pack inventory disagrees with the tagged source")
            return {"url":state["url"],"target":target,"alreadyPublished":True}
    inherited = {}
    for sha, bundle in document["bundles"].items():
        older = bundle["url"].split("/")[-2]
        if older != tag:
            inherited.setdefault(older, {})[sha] = bundle
    for older, references in sorted(inherited.items()):
        old_target=tagged(root,older)
        old_state, old_document = verify_remote(older, old_target, run)
        if old_state['isDraft']: raise ValueError('An inherited payload belongs to an unpublished predecessor; publish it before this release')
        if any(old_document["bundles"].get(sha) != bundle for sha, bundle in references.items()):
            raise ValueError(f"An inherited payload does not match the published catalog for {older}")
    if existing is None:
        with tempfile.TemporaryDirectory() as temporary:
            notes=Path(temporary)/"notes.md"
            notes.write_text(f"Auram pack {tag}\n\nCommit: {target['sha']}\n\nLocally built, signed partial pack payloads and a complete target inventory. Java/Minecraft/Forge runtime data is not included. Fresh snapshots use sparse Git retrieval; older launchers retain schema-1 tag/changelist support.\n",encoding="utf-8")
            run("release","create",tag,"--verify-tag","--target",target["sha"],"--title",f"Auram build {tag}","--notes-file",str(notes),"--draft")
    # Only drafts may replace assets. No workflow is dispatched or awaited.
    run("release","upload",tag,*[str(directory/name) for name in files],"--clobber")
    verify_remote(tag,target,run)
    # Check after upload and verification, so an older draft doesn't displace
    # a release that became latest while this draft was being uploaded.
    latest=release(None,run)
    latest_build=int(latest["tagName"]) if latest and latest["tagName"].isdigit() else 0
    make_latest=tag.isdigit() and int(tag)>=latest_build
    run("release","edit",tag,"--draft=false",f"--latest={'true' if make_latest else 'false'}")
    state,_=verify_remote(tag,target,run)
    if state["isDraft"] or state["isPrerelease"]: raise ValueError("Release did not become a public stable distribution")
    return {"url":state["url"],"target":target,"alreadyPublished":False}
