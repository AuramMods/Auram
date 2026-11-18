import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"scripts"))
from bundle_publish import publish
from bundle_build import verified_envelope
import test_bundle_build


class FakeGitHub:
    def __init__(self,root):
        self.root=root; self.states={}; self.calls=[]; self.latest=None; self.fail_upload=False
    def __call__(self,*args):
        self.calls.append(args)
        action=args[1]
        if action=="view":
            tag=args[2] if args[2]!="--json" else self.latest
            if tag not in self.states: raise subprocess.CalledProcessError(1,["gh",*args],stderr="release not found")
            state=dict(self.states[tag]); directory=self.root/tag
            state["assets"]=[{"name":p.name,"size":p.stat().st_size} for p in directory.iterdir()]
            return json.dumps(state)
        tag=args[2]
        if action=="create":
            self.states[tag]={"tagName":tag,"isDraft":True,"isPrerelease":False,"targetCommitish":args[args.index('--target')+1],"url":f"https://github.com/AuramMods/Auram/releases/tag/{tag}"}
            (self.root/tag).mkdir(); return ""
        if action=="upload":
            for name in args[3:-1]:
                source=Path(name); shutil.copyfile(source,self.root/tag/source.name)
                if self.fail_upload:
                    self.fail_upload=False
                    raise subprocess.CalledProcessError(1,["gh",*args],stderr="upload disconnected")
            return ""
        if action=="download":
            output=Path(args[args.index('--dir')+1]); output.mkdir(parents=True,exist_ok=True)
            for source in (self.root/tag).iterdir(): shutil.copyfile(source,output/source.name)
            return ""
        if action=="edit":
            self.states[tag]["isDraft"]=False
            if '--latest=true' in args: self.latest=tag
            return ""
        raise AssertionError(args)


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.fixture=test_bundle_build.BundleBuildTests(); self.fixture.setUp(); self.addCleanup(self.fixture.doCleanups)
        self.f=self.fixture; self.f.build(self.f.base)
        remote=Path(self.f.temp.name)/"github"; remote.mkdir()
        self.gh=FakeGitHub(remote)
        signer=patch('bundle_publish.verified_envelope',side_effect=lambda path,key,policy: verified_envelope(path,self.f.public,policy))
        signer.start(); self.addCleanup(signer.stop)
    def publish(self): return publish(self.f.root,self.f.base["name"],self.f.cache/self.f.base["name"],self.gh)

    def test_publish_verifies_before_exposure_and_retry_is_immutable(self):
        result=self.publish(); self.assertFalse(result["alreadyPublished"])
        tag=self.f.base['name']; self.assertFalse(self.gh.states[tag]["isDraft"])
        edits=[i for i,c in enumerate(self.gh.calls) if c[1]=='edit']
        downloads=[i for i,c in enumerate(self.gh.calls) if c[1]=='download']
        self.assertLess(downloads[0],edits[0])
        writes=len([c for c in self.gh.calls if c[1] in ('create','upload','edit')])
        self.assertTrue(self.publish()["alreadyPublished"])
        self.assertEqual(writes,len([c for c in self.gh.calls if c[1] in ('create','upload','edit')]))

    def test_interrupted_upload_retains_draft_and_retries_without_a_new_tag(self):
        head=self.f.git('rev-parse','HEAD'); self.gh.fail_upload=True
        with self.assertRaises(subprocess.CalledProcessError): self.publish()
        self.assertTrue(self.gh.states[self.f.base['name']]["isDraft"])
        self.assertFalse(any(c[1]=='edit' for c in self.gh.calls))
        self.publish()
        self.assertEqual(head,self.f.git('rev-parse','HEAD'))
        self.assertEqual(len([c for c in self.gh.calls if c[1]=='create']),1)

    def test_corrupt_local_payload_fails_before_upload(self):
        payload=next((self.f.cache/self.f.base['name']).glob('pack-*.zip'))
        payload.write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError,'integrity'): self.publish()
        self.assertFalse(self.gh.calls)

    def test_older_retry_does_not_demote_latest_and_wrong_commit_is_rejected(self):
        self.gh.states['1000']={"tagName":"1000","isDraft":False,"isPrerelease":False,"targetCommitish":"a"*40,"url":"newer"}
        (self.gh.root/'1000').mkdir(); self.gh.latest='1000'
        self.publish(); self.assertEqual(self.gh.latest,'1000')
        self.gh.states[self.f.base['name']]['targetCommitish']='b'*40
        with self.assertRaisesRegex(ValueError,'different commit'): self.publish()


if __name__=='__main__': unittest.main()
