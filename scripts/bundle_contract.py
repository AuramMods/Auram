"""Shared schema-1 distribution invariants; no working-tree payload reads."""
from __future__ import annotations

import base64
import json
from pathlib import PurePosixPath
import re
import unicodedata
from urllib.parse import unquote, urlsplit

REPOSITORY = "AuramMods/Auram"
OID = re.compile(r"[a-f0-9]{40}\Z")
SHA256 = re.compile(r"[a-f0-9]{64}\Z")
TAG = re.compile(r"v?\d+(?:\.\d+){0,2}\Z")
MAX_METADATA = 32 * 1024 * 1024


def canonical(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def safe_path(value: str) -> str:
    if not isinstance(value, str) or not value or value.startswith("/") or "\\" in value:
        raise ValueError(f"Unsafe distribution path: {value!r}")
    for part in value.split("/"):
        if (not part or part in (".", "..") or part.endswith((".", " "))
                or any(ord(c) < 32 or c in ':<>"|?*' for c in part)
                or part.split(".")[0].upper() in {"CON", "PRN", "AUX", "NUL", *[f"{p}{n}" for p in ("COM", "LPT") for n in range(1, 10)]}):
            raise ValueError(f"Unsupported distribution path: {value!r}")
    return unicodedata.normalize("NFC", value).lower()


def selected(path: str, policy: dict) -> bool:
    if path in policy["roots"]:
        return True
    if path.startswith("minecraft/"):
        return not any(path[len("minecraft/"):].startswith(p) for p in policy["excludedMinecraftPrefixes"])
    if path.startswith("server_files/"):
        relative = path[len("server_files/"):]
        return (PurePosixPath(relative).name not in policy["excludedServerNames"]
                and not any(relative.startswith(p) for p in policy["excludedServerPrefixes"]))
    return False


def identity(tag: dict) -> None:
    if set(tag) != {"name", "sha"} or not TAG.fullmatch(tag["name"]) or not OID.fullmatch(tag["sha"]):
        raise ValueError("Invalid release identity")


def entry(file: dict) -> None:
    if (not OID.fullmatch(file["sha"]) or not SHA256.fullmatch(file["sha256"])
            or file["mode"] not in ("100644", "100755") or type(file["size"]) is not int or file["size"] < 0):
        raise ValueError("Invalid distribution file")


def inventory(files: dict, policy: dict) -> None:
    names = set()
    for path, file in files.items():
        name = safe_path(path)
        if not selected(path, policy) or name in names:
            raise ValueError(f"Unsafe/colliding inventory: {path}")
        names.add(name)
        entry(file)
    for name in names:
        if any(parent.as_posix() in names for parent in PurePosixPath(name).parents if parent.as_posix() != "."):
            raise ValueError("File/directory collision")


def validate(document: dict, policy: dict) -> None:
    if document["schema"] != 1 or document["policy"] != policy["version"] or document["repository"] != REPOSITORY:
        raise ValueError("Unsupported distribution schema, policy, or repository")
    identity(document["target"])
    if not OID.fullmatch(document["producer"]):
        raise ValueError("Invalid producer identity")
    inventory(document["files"], policy)
    if "mmc-pack.json" not in document["files"]:
        raise ValueError("Missing mmc-pack.json")
    current = document["target"]
    seen = set()
    snapshot = dict(document["files"])
    for transition in document["history"]:
        if "packIgnoreBefore" in transition and not isinstance(transition["packIgnoreBefore"],str):
            raise ValueError("Invalid prior client filter")
        identity(transition["target"])
        if transition["target"] != current or current["sha"] in seen:
            raise ValueError("Broken/cyclic distribution history")
        seen.add(current["sha"])
        for path, change in transition["changes"].items():
            safe_path(path)
            if not selected(path, policy) or not any(change.get(k) is not None for k in ("before", "after")):
                raise ValueError("Invalid history path/change")
            for side in ("before", "after"):
                if change.get(side) is not None:
                    entry(change[side])
            if snapshot.get(path) != change.get("after"):
                raise ValueError("History disagrees with its target snapshot")
            if change.get("before") is None:
                snapshot.pop(path, None)
            else:
                snapshot[path] = change["before"]
        current = transition["base"]
        if current is not None:
            identity(current)
            if current["sha"] in seen:
                raise ValueError("Cyclic distribution predecessor")
    for sha, bundle in document["bundles"].items():
        if not SHA256.fullmatch(sha) or sha != bundle["sha256"] or not 0 < bundle["size"] < policy["maximumBundleBytes"]:
            raise ValueError("Invalid bundle identity/size")
        location = urlsplit(bundle["url"])
        parts = unquote(location.path).split("/")
        if (location.scheme != "https" or location.netloc != "github.com" or location.query or location.fragment
                or len(parts) != 7 or parts[1:5] != ["AuramMods", "Auram", "releases", "download"]
                or not TAG.fullmatch(parts[5]) or parts[6] != f"pack-{sha}.zip"):
            raise ValueError("Bundle URL is not an immutable pack release asset")
        for oid, file in bundle["members"].items():
            entry({"sha": oid, "mode": "100644", **file})
    for oid, location in document["objects"].items():
        if not OID.fullmatch(oid) or location not in document["bundles"] or oid not in document["bundles"][location]["members"]:
            raise ValueError("Invalid object locator")
    for file in document["files"].values():
        if (location := document["objects"].get(file["sha"])) is not None:
            if document["bundles"][location]["members"][file["sha"]] != {k: file[k] for k in ("size", "sha256")}:
                raise ValueError("Object locator disagrees with target file")


def envelope(payload: bytes, signature: str) -> bytes:
    return canonical({"payload": base64.b64encode(payload).decode("ascii"), "signature": signature})
