"""Build signed partial distributions locally from pinned raw Git objects."""
from __future__ import annotations

import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

from bundle_contract import MAX_METADATA, canonical, envelope, identity, inventory, selected, validate

REPO = "AuramMods/Auram"


def git(root: Path, *args: str) -> bytes:
    return subprocess.run(["git", *args], cwd=root, check=True, capture_output=True).stdout


def tagged(root: Path, name: str) -> dict:
    result = {"name": name, "sha": git(root, "rev-parse", f"refs/tags/{name}^{{commit}}").decode().strip()}
    identity(result)
    if name.isdigit() and int(git(root, "rev-list", "--count", result["sha"])) != int(name):
        raise ValueError("Numeric pack tag disagrees with its reachable commit count")
    return result


def atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as pending:
        try:
            pending.write(data); pending.flush(); os.fsync(pending.fileno())
            pending.close(); os.replace(pending.name, path)
        finally:
            Path(pending.name).unlink(missing_ok=True)


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""): h.update(chunk)
    return h.hexdigest()


def verify_archive(path: Path, bundle: dict) -> None:
    if path.stat().st_size != bundle['size'] or digest(path) != bundle['sha256']:
        raise ValueError(f'Bundle archive failed integrity verification: {path.name}')
    seen = set()
    with zipfile.ZipFile(path) as archive:
        if len(archive.infolist()) != len(bundle['members']):
            raise ValueError('Unexpected archive member count')
        for item in archive.infolist():
            oid = item.filename.removeprefix('objects/')
            expected = bundle['members'].get(oid)
            mode = (item.external_attr >> 16) & 0o170000
            if (item.filename != f'objects/{oid}' or expected is None or oid in seen
                    or item.is_dir() or mode not in (0, 0o100000) or item.flag_bits & 1
                    or item.file_size != expected['size']):
                raise ValueError('Unsafe or duplicate archive member')
            seen.add(oid)
            git_hash = hashlib.sha1(f"blob {expected['size']}\0".encode())
            sha256 = hashlib.sha256()
            size = 0
            with archive.open(item) as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    size += len(chunk)
                    if size > expected['size']:
                        raise ValueError('Archive expansion exceeds its declaration')
                    git_hash.update(chunk)
                    sha256.update(chunk)
            if size != expected['size'] or git_hash.hexdigest() != oid or sha256.hexdigest() != expected['sha256']:
                raise ValueError('Archive member failed content verification')


def verified_envelope(path: Path, public: Path, policy: dict) -> dict:
    with path.open('rb') as stream:
        data = stream.read(MAX_METADATA + 1)
    if len(data) > MAX_METADATA: raise ValueError("Pack metadata exceeds its bound")
    signed = json.loads(data)
    payload = base64.b64decode(signed["payload"], validate=True)
    with tempfile.TemporaryDirectory() as directory:
        body = Path(directory) / "manifest.json"; signature = Path(directory) / "manifest.sig"
        body.write_bytes(payload); signature.write_text(signed["signature"], encoding="utf-8")
        failure=None
        keys=[line.strip() for line in public.read_text().splitlines() if line.strip() and not line.startswith('untrusted comment:')]
        if not keys: raise ValueError('No configured public pack verification keys')
        for key in keys:
            try:
                subprocess.run(["minisign", "-V", "-P", key, "-m", str(body), "-x", str(signature)], check=True, capture_output=True, timeout=30)
                failure=None; break
            except subprocess.CalledProcessError as error: failure=error
        if failure is not None: raise failure
    document = json.loads(payload)
    validate(document, policy)
    return document


class Builder:
    def __init__(self, root: Path, cache: Path, policy: dict):
        self.root = root
        self.cache = cache
        self.policy = policy
        self.memo = {}
        self.snapshots = {}
        self.cache.mkdir(parents=True, exist_ok=True)
        self.reader = subprocess.Popen(["git", "cat-file", "--batch"], cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def close(self):
        self.reader.stdin.close(); self.reader.stdout.close()
        try: self.reader.wait(timeout=10)
        except subprocess.TimeoutExpired: self.reader.kill(); self.reader.wait()
        self.reader.stderr.close()

    def object(self, sha: str, size: int) -> dict:
        if sha in self.memo:
            if self.memo[sha]["size"] != size: raise ValueError("Inconsistent Git object size")
            return self.memo[sha]
        path = self.cache / sha
        if path.is_symlink(): raise ValueError("Linked producer cache object")
        if path.is_file():
            git_hash = hashlib.sha1(f"blob {path.stat().st_size}\0".encode())
            sha256 = hashlib.sha256()
            with path.open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    git_hash.update(chunk); sha256.update(chunk)
            if path.stat().st_size == size and git_hash.hexdigest() == sha:
                self.memo[sha] = {"size":size,"sha256":sha256.hexdigest()}
                return self.memo[sha]
        self.reader.stdin.write((sha + "\n").encode()); self.reader.stdin.flush()
        header = self.reader.stdout.readline().decode().strip().split()
        if header != [sha, "blob", str(size)]: raise ValueError(f"Unexpected Git object header: {header}")
        git_hash = hashlib.sha1(f"blob {size}\0".encode()); sha256 = hashlib.sha256()
        with tempfile.NamedTemporaryFile(dir=self.cache, delete=False) as pending:
            try:
                remaining = size
                while remaining:
                    chunk = self.reader.stdout.read(min(remaining, 1024 * 1024))
                    if not chunk: raise ValueError("Git object stream ended early")
                    pending.write(chunk); git_hash.update(chunk); sha256.update(chunk); remaining -= len(chunk)
                if self.reader.stdout.read(1) != b"\n" or git_hash.hexdigest() != sha:
                    raise ValueError("Git object stream failed integrity verification")
                pending.flush(); os.fsync(pending.fileno()); pending.close(); os.replace(pending.name, path)
            finally: Path(pending.name).unlink(missing_ok=True)
        self.memo[sha] = {"size":size,"sha256":sha256.hexdigest()}
        return self.memo[sha]

    def snapshot(self, commit: str) -> dict:
        if commit in self.snapshots: return self.snapshots[commit]
        files = {}
        for raw in git(self.root, "ls-tree", "-r", "-l", "-z", commit).split(b"\0"):
            if not raw: continue
            attributes, path = raw.split(b"\t", 1); path = path.decode("utf-8")
            if not selected(path, self.policy): continue
            mode, kind, sha, size = attributes.decode().split()
            if kind != "blob" or mode not in ("100644", "100755"): raise ValueError(f"Unsupported selected Git entry: {path}")
            files[path] = {"sha":sha,"mode":mode,**self.object(sha,int(size))}
        inventory(files, self.policy)
        self.snapshots[commit] = files
        return files

    def metadata(self, tag: dict) -> tuple[dict, dict | None]:
        listing = git(self.root, "ls-tree", tag["sha"], "--", "auram.json")
        if not listing: return {}, None
        metadata = json.loads(git(self.root,"show",f"{tag['sha']}:auram.json"))
        if "changelist" not in metadata: return metadata, None
        path = metadata["changelist"]
        from bundle_contract import safe_path
        safe_path(path)
        delta = json.loads(git(self.root,"show",f"{tag['sha']}:{path}"))
        if delta["schema"] != 1 or str(delta["version"]) != tag["name"]: raise ValueError("Inconsistent schema-1 release metadata")
        parents = git(self.root,"rev-list","--parents","-n","1",tag["sha"]).decode().split()[1:]
        if parents != [delta["contentCommit"]]: raise ValueError("Release metadata provenance disagrees with commit parent")
        base = None
        if delta["previousVersion"] is not None:
            base = tagged(self.root,delta["previousVersion"])
            if base["sha"] != delta["previousCommit"]: raise ValueError("Previous pack tag moved")
        return metadata, base

    def history(self, target: dict) -> list:
        result = []; current = target; seen = set()
        for _ in range(64):
            if current["sha"] in seen: raise ValueError("Cyclic release history")
            seen.add(current["sha"])
            _, base = self.metadata(current)
            before = self.snapshot(base["sha"]) if base else {}
            after = self.snapshot(current["sha"])
            changes = {path:{"before":before.get(path),"after":after.get(path)}
                       for path in sorted(before.keys() | after.keys()) if before.get(path) != after.get(path)}
            ignore_before=(git(self.root,"show",f"{base['sha']}:.packignore").decode()
                           if base and git(self.root,"ls-tree",base["sha"],"--",".packignore") else "")
            result.append({"target":current,"base":base,"changes":changes,"packIgnoreBefore":ignore_before})
            if base is None: break
            current = base
        return result

    def archive(self, objects: dict, output: Path) -> tuple[str, dict]:
        output.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=output, suffix=".zip", delete=False) as pending:
            path = Path(pending.name)
        try:
            with zipfile.ZipFile(path,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as archive:
                for sha, member in sorted(objects.items()):
                    info = zipfile.ZipInfo(f"objects/{sha}", (1980,1,1,0,0,0))
                    info.create_system=3; info.external_attr=0o100644 << 16
                    info.compress_type = zipfile.ZIP_STORED if member["size"] >= self.policy["smallObjectBytes"] else zipfile.ZIP_DEFLATED
                    with archive.open(info,"w",force_zip64=True) as destination, (self.cache/sha).open("rb") as source:
                        shutil.copyfileobj(source,destination,1024*1024)
            if path.stat().st_size >= self.policy["maximumBundleBytes"]: raise ValueError("Bundle exceeds the supported asset bound")
            sha = digest(path); destination=output/f"pack-{sha}.zip"
            os.replace(path,destination)
            return sha,{"sha256":sha,"size":destination.stat().st_size,"members":objects}
        finally: path.unlink(missing_ok=True)


def build(root: Path, tag: str, output: Path, key: Path, previous: Path | None = None, public: Path | None = None) -> dict:
    tool_root = Path(__file__).resolve().parents[1]
    policy = json.loads((tool_root/"distribution/policy.json").read_bytes())
    public = public or tool_root/"distribution/pack.pub"
    target = tagged(root,tag)
    output.mkdir(parents=True,exist_ok=True)
    builder = Builder(root, output.parent/"objects",policy)
    try:
        files = builder.snapshot(target["sha"])
        if "mmc-pack.json" not in files: raise ValueError("Target is not a playable pack snapshot")
        history = builder.history(target)
        base = history[0]["base"]
        baseline = builder.snapshot(base["sha"]) if base else {}
        old_objects = {f["sha"] for f in baseline.values()}
        new = {f["sha"]:{k:f[k] for k in ("size","sha256")} for f in files.values() if f["sha"] not in old_objects}
        document = {"schema":1,"policy":policy["version"],"repository":REPO,"target":target,
                    "producer":git(tool_root,"rev-parse","HEAD").decode().strip(),"files":files,"history":history,
                    "packIgnore":git(root,"show",f"{target['sha']}:.packignore").decode() if git(root,"ls-tree",target["sha"],"--",".packignore") else "",
                    "objects":{},"bundles":{}}
        if previous is not None:
            older = verified_envelope(previous,public,policy)
            if older["target"] != base: raise ValueError("Previous catalog is not the immediate base")
            for file in files.values():
                location = older["objects"].get(file["sha"])
                if location:
                    document["objects"][file["sha"]] = location
                    document["bundles"][location] = older["bundles"][location]
        groups=[]; small={}; small_size=0
        for sha,member in sorted(new.items()):
            if member["size"] >= policy["smallObjectBytes"]:
                groups.append({sha:member})
            else:
                if small and small_size+member["size"] > policy["smallBundleBytes"]:
                    groups.append(small); small={}; small_size=0
                small[sha]=member; small_size+=member["size"]
        if small: groups.append(small)
        published=[]
        for group in groups:
            sha,bundle=builder.archive(group,output)
            bundle["url"]=f"https://github.com/{REPO}/releases/download/{tag}/pack-{sha}.zip"
            verify_archive(output/f'pack-{sha}.zip',bundle)
            document["bundles"][sha]=bundle
            for oid in group: document["objects"][oid]=sha
            published.append(f"pack-{sha}.zip")
        validate(document,policy)
        payload=canonical(document)
        if len(payload)*4//3+4096 > MAX_METADATA: raise ValueError("Pack metadata exceeds its bound")
        atomic(output/"manifest.json",payload)
        subprocess.run(["minisign","-S","-s",str(key),"-m",str(output/"manifest.json"),"-x",str(output/"manifest.minisig"),"-t","Auram pack distribution schema 1","-c","Auram pack distribution"],check=True,capture_output=True,timeout=30)
        atomic(output/"auram-pack.json",envelope(payload,(output/"manifest.minisig").read_text()))
        verified_envelope(output/"auram-pack.json",public,policy)
        published.append("auram-pack.json")
        atomic(output/"SHA256SUMS",("".join(f"{digest(output/name)}  {name}\n" for name in sorted(published))).encode())
        info={"target":target,"files":len(files),"selectedBytes":sum(f["size"] for f in files.values()),
              "newObjects":len(new),"reusedObjects":len({f["sha"] for f in files.values()})-len(new),
              "payloadBytes":sum((output/name).stat().st_size for name in published if name.endswith('.zip')),
              "assets":sorted([*published,"SHA256SUMS"])}
        atomic(output/"build.json",canonical(info))
        return info
    finally: builder.close()


if __name__ == "__main__":
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag",required=True)
    parser.add_argument("--root",type=Path,default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output",type=Path)
    parser.add_argument("--key",type=Path,default=Path(os.environ.get("AURAM_PACK_SIGNING_KEY_FILE",Path.home()/".config/auram/pack-signing/pack.key")))
    parser.add_argument("--previous-catalog",type=Path)
    args=parser.parse_args()
    output=args.output or args.root/".auram-distribution"/args.tag
    print(json.dumps(build(args.root,args.tag,output,args.key,args.previous_catalog),indent=2))
