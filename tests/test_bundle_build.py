import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"scripts"))
from bundle_build import build, digest, verified_envelope


class BundleBuildTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix="Auram bundle 世界 "); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)/"pack"; self.root.mkdir()
        self.cache=Path(self.temp.name)/"output"
        self.key=Path(self.temp.name)/"pack.key"; self.public=Path(self.temp.name)/"pack.pub"
        subprocess.run(["minisign","-G","-W","-s",str(self.key),"-p",str(self.public)],check=True,capture_output=True)
        self.git("init","-b","main"); self.git("config","user.name","Bundle Test"); self.git("config","user.email","test@example.invalid")
        self.write("mmc-pack.json",b"{}\n"); self.write(".packignore",b"minecraft/ignored\nserver_files\n")
        self.write("minecraft/mods/old.jar",b"same jar"); self.write("minecraft/config/test.toml",b"old")
        self.write("minecraft/saves/world/level.dat",b"private world"); self.write("server_files/ops.json",b"author identity")
        self.write("server_config.conf",b"clientMods=[]\n"); self.write("server_files/server.properties",b"motd=Auram\n")
        self.commit("Initial payload"); self.base=self.release(None)

    def git(self,*args):
        return subprocess.run(["git",*args],cwd=self.root,check=True,capture_output=True).stdout.decode().strip()
    def write(self,path,data):
        target=self.root/path; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data); return target
    def commit(self,message): self.git("add","-A"); self.git("commit","-m",message); return self.git("rev-parse","HEAD")
    def release(self,base):
        content=self.git("rev-parse","HEAD"); name=str(int(self.git("rev-list","--count","HEAD"))+1)
        path=f"changelist/{name}.json"
        self.write("auram.json",json.dumps({"version":int(name),"changelist":path,"other":"preserved"}).encode())
        self.write(path,json.dumps({"schema":1,"version":int(name),"previousVersion":base["name"] if base else None,"previousCommit":base["sha"] if base else None,"contentCommit":content,"changes":[]}).encode())
        commit=self.commit(f"Release {name}"); self.git("tag","-a",name,"-m",name); return {"name":name,"sha":commit}
    def build(self,target,previous=None):
        output=self.cache/target["name"]
        info=build(self.root,target["name"],output,self.key,previous,public=self.public)
        policy=json.loads((ROOT/"distribution/policy.json").read_bytes())
        return info,verified_envelope(output/"auram-pack.json",self.public,policy)

    def test_partial_reproducible_build_reads_git_not_dirty_prism_files(self):
        (self.root/"minecraft/mods/old.jar").rename(self.root/"minecraft/mods/new.jar")
        self.write("minecraft/config/test.toml",b"changed"); self.commit("Rename and configure"); target=self.release(self.base)
        self.write("minecraft/config/test.toml",b"dirty not for distribution")
        info,doc=self.build(target)
        self.assertEqual(info["newObjects"],1); self.assertEqual(info["target"],target)
        self.assertNotIn("minecraft/saves/world/level.dat",doc["files"]); self.assertNotIn("server_files/ops.json",doc["files"])
        self.assertIn("server_files/server.properties",doc["files"])
        self.assertEqual(doc["files"]["minecraft/config/test.toml"]["sha256"],hashlib.sha256(b"changed").hexdigest())
        delta=doc["history"][0]["changes"]
        self.assertEqual(delta["minecraft/mods/old.jar"]["before"]["sha"],delta["minecraft/mods/new.jar"]["after"]["sha"])
        output=self.cache/target["name"]; first={name:(output/name).read_bytes() for name in info["assets"]}
        self.build(target)
        self.assertEqual(first,{name:(output/name).read_bytes() for name in info["assets"]})
        self.assertEqual((self.root/"minecraft/config/test.toml").read_bytes(),b"dirty not for distribution")

    def test_large_objects_are_independent_and_small_objects_grouped_with_bounds(self):
        self.write("minecraft/mods/large.jar",b"J"*(1024*1024+1))
        self.write("minecraft/config/a.json",b"a"*600_000); self.write("minecraft/config/b.json",b"b"*600_000)
        self.commit("New payload"); target=self.release(self.base)
        info,doc=self.build(target)
        self.assertEqual(len(doc["bundles"]),3)
        seen=set()
        for sha,bundle in doc["bundles"].items():
            path=self.cache/target["name"]/f"pack-{sha}.zip"
            self.assertEqual(digest(path),sha)
            with zipfile.ZipFile(path) as archive:
                self.assertEqual(set(archive.namelist()),{f"objects/{oid}" for oid in bundle["members"]})
                for oid,member in bundle["members"].items():
                    body=archive.read(f"objects/{oid}"); seen.add(oid)
                    self.assertEqual(hashlib.sha1(f"blob {len(body)}\0".encode()+body).hexdigest(),oid)
                    self.assertEqual(hashlib.sha256(body).hexdigest(),member["sha256"])
        self.assertEqual(len(seen),3)

    def test_inherited_locations_metadata_only_and_cache_corruption(self):
        _,base=self.build(self.base)
        self.write("README.md",b"Updated release instructions\n")
        self.commit("Only metadata/tooling work"); target=self.release(self.base)
        info,doc=self.build(target,self.cache/self.base["name"]/"auram-pack.json")
        self.assertEqual(info["newObjects"],0); self.assertEqual(info["payloadBytes"],0)
        self.assertEqual(doc["objects"],base["objects"])
        oid=doc["files"]["mmc-pack.json"]["sha"]
        (self.cache/"objects"/oid).write_bytes(b"damaged")
        self.build(target,self.cache/self.base["name"]/"auram-pack.json")
        self.assertEqual((self.cache/"objects"/oid).read_bytes(),b"{}\n")

    def test_signature_and_selected_symlink_fail_without_publication(self):
        info,_=self.build(self.base)
        output=self.cache/self.base["name"]
        envelope=json.loads((output/"auram-pack.json").read_bytes()); body=bytearray(base64.b64decode(envelope["payload"])); body[3]^=1
        envelope["payload"]=base64.b64encode(body).decode(); (output/"auram-pack.json").write_text(json.dumps(envelope))
        with self.assertRaises(subprocess.CalledProcessError):
            verified_envelope(output/"auram-pack.json",self.public,json.loads((ROOT/"distribution/policy.json").read_bytes()))
        os.symlink("/tmp/outside",self.root/"minecraft/config/link")
        self.commit("Unsupported link"); target=self.release(self.base)
        with self.assertRaisesRegex(ValueError,"Unsupported selected Git entry"): self.build(target)

    def test_retained_public_keys_allow_a_staged_signing_key_rotation(self):
        self.build(self.base)
        self.public.write_bytes((ROOT/'distribution/pack.pub').read_bytes()+b'\n'+self.public.read_bytes())
        document=verified_envelope(self.cache/self.base['name']/'auram-pack.json',self.public,json.loads((ROOT/'distribution/policy.json').read_bytes()))
        self.assertEqual(document['target'],self.base)


if __name__ == "__main__": unittest.main()
