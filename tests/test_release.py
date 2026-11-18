"""Exercise the actual release CLI against temporary Git repositories."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "release.py"


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "pack"
        self.remote = Path(self.temp.name) / "remote.git"
        self.root.mkdir()
        self.env = {
            **os.environ,
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_TERMINAL_PROMPT": "0",
        }
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "release@example.invalid")
        self.git("init", "--bare", str(self.remote))
        self.git("remote", "add", "origin", str(self.remote))
        (self.root / "scripts").mkdir()
        shutil.copyfile(SCRIPT, self.root / "scripts/release.py")

    def run_command(self, *args, check=True, cwd=None, input=None):
        return subprocess.run(
            args, cwd=cwd or self.root, env=self.env, input=input,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check,
        )

    def git(self, *args, cwd=None, input=None):
        return self.run_command("git", *args, cwd=cwd, input=input).stdout.decode().strip()

    def write(self, name, content="test\n"):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, bytes):
            path.write_bytes(content)
        else:
            path.write_text(content, encoding="utf-8")
        return path

    def commit(self, message):
        self.git("add", "-A")
        self.git("commit", "-m", message)
        return self.git("rev-parse", "HEAD")

    def release(self, *args, check=True):
        return self.run_command(sys.executable, "scripts/release.py", "--git-only", *args, check=check)

    def seed(self):
        self.write("auram.json", json.dumps({"version": "1.0.7", "name": "Auram"}))
        self.write("binary.jar", b"old\x00binary")
        self.write("deleted.txt")
        self.write("old name.txt")
        self.write("reverted.txt", "original\n")
        self.write("staged.txt", "original\n")
        self.write("unstaged.txt", "original\n")
        base = self.commit("Initial pack")
        self.git("tag", "1.0.7")
        self.git("push", "origin", "main", "--tags")
        return base

    def manifest(self):
        metadata = json.loads((self.root / "auram.json").read_text())
        return metadata, json.loads((self.root / metadata["changelist"]).read_text())

    def assert_published(self, version, head=None):
        head = head or self.git("rev-parse", "HEAD")
        self.assertEqual(str(version), self.git("rev-list", "--count", head))
        self.assertEqual(head, self.git("rev-parse", f"refs/tags/{version}^{{commit}}"))
        self.assertEqual(head, self.git("rev-parse", "refs/heads/main", cwd=self.remote))
        self.assertEqual(head, self.git("rev-parse", f"refs/tags/{version}^{{commit}}", cwd=self.remote))

    def test_net_delta_release_and_next_build(self):
        base = self.seed()
        special_path = "minecraft/new space\t雪\nfile.txt"
        self.write(special_path)
        self.write("binary.jar", b"new\x00binary")
        (self.root / "deleted.txt").unlink()
        (self.root / "old name.txt").rename(self.root / "new name.txt")
        self.write("reverted.txt", "temporary\n")
        self.commit("Pack changes")
        self.write("reverted.txt", "original\n")
        content = self.commit("Revert one change")

        # A larger tag on an unrelated history is not a release of this pack.
        tree = self.git("rev-parse", "HEAD^{tree}")
        orphan = self.git("commit-tree", tree, input=b"Unrelated release\n")
        self.git("tag", "999", orphan)

        # Only committed payload is released; local work stays available.
        self.write("staged.txt", "staged edit\n")
        self.git("add", "staged.txt")
        self.write("unstaged.txt", "unstaged edit\n")
        self.write("untracked.txt")
        self.release()
        metadata, manifest = self.manifest()
        self.assertEqual({"version": 4, "name": "Auram", "changelist": "changelist/4.json"}, metadata)
        self.assertEqual("1.0.7", manifest["previousVersion"])
        self.assertEqual(base, manifest["previousCommit"])
        self.assertEqual(content, manifest["contentCommit"])
        expected = {
            "*auram.json", "*binary.jar", "+changelist/4.json", "-deleted.txt",
            "+" + special_path, "+new name.txt", "-old name.txt",
        }
        self.assertEqual(sorted(expected, key=lambda entry: entry[1:]), manifest["changes"])
        status = self.git("status", "--porcelain")
        self.assertIn("M  staged.txt", status)
        self.assertIn(" M unstaged.txt", status)
        self.assertIn("?? untracked.txt", status)
        self.assertEqual("original", self.git("show", "HEAD:staged.txt"))
        self.assertEqual("original", self.git("show", "HEAD:unstaged.txt"))
        self.assert_published(4)

        self.commit("More pack work")
        previous = self.git("rev-parse", "4^{commit}")
        self.release()
        metadata, manifest = self.manifest()
        self.assertEqual(6, metadata["version"])
        self.assertEqual("4", manifest["previousVersion"])
        self.assertEqual(previous, manifest["previousCommit"])
        self.assertEqual(
            ["*auram.json", "+changelist/6.json", "*staged.txt", "*unstaged.txt", "+untracked.txt"],
            manifest["changes"],
        )
        self.assertEqual("", self.git("status", "--porcelain"))
        self.assert_published(6)

    def test_dry_run_and_first_release(self):
        self.write("minecraft/config/example.json", "{}\n")
        self.commit("Initial pack")
        before = self.git("rev-parse", "HEAD")
        preview = self.release("--dry-run")
        self.assertIn(b"+minecraft/config/example.json", preview.stdout)
        self.assertFalse((self.root / "auram.json").exists())
        self.assertFalse((self.root / "changelist").exists())
        self.assertEqual(before, self.git("rev-parse", "HEAD"))
        self.assertEqual("", self.git("tag", "--list"))
        self.assertEqual("", self.git("status", "--porcelain"))

        self.release()
        metadata, manifest = self.manifest()
        self.assertIsNone(manifest["previousVersion"])
        self.assertIsNone(manifest["previousCommit"])
        self.assertEqual(
            ["+auram.json", "+changelist/2.json", "+minecraft/config/example.json", "+scripts/release.py"],
            manifest["changes"],
        )
        self.assert_published(2)

    def test_atomic_push_rejection_and_retry(self):
        before = self.seed()
        self.write("minecraft/new.txt")
        self.commit("Pack update")
        hook = self.remote / "hooks/update"
        hook.write_text('#!/bin/sh\ncase "$1" in refs/tags/*) exit 1;; esac\nexit 0\n')
        hook.chmod(0o755)
        failed = self.release(check=False)
        self.assertNotEqual(0, failed.returncode)
        self.assertIn(b"--push-only", failed.stderr)
        local_release = self.git("rev-parse", "HEAD")
        self.assertEqual(before, self.git("rev-parse", "main", cwd=self.remote))
        self.assertEqual("", self.git("tag", "--list", "3", cwd=self.remote))
        self.assertEqual(local_release, self.git("rev-parse", "3^{commit}"))

        hook.unlink()
        self.write("later-local-work.txt")
        unreleased = self.commit("Work after failed publication")
        self.release("--push-only")
        self.assertEqual(unreleased, self.git("rev-parse", "HEAD"))
        self.assert_published(3, head=local_release)

    def test_shallow_clone_uses_full_history_for_build_and_base(self):
        base = self.seed()
        self.write("minecraft/new.txt")
        self.commit("Pack update")
        self.git("push", "origin", "main")
        clone = Path(self.temp.name) / "shallow"
        self.git("clone", "--depth=1", "--branch=main", self.remote.as_uri(), str(clone))
        self.root = clone
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "release@example.invalid")
        self.assertEqual("true", self.git("rev-parse", "--is-shallow-repository"))
        self.assertEqual("1", self.git("rev-list", "--count", "HEAD"))

        self.release()
        metadata, manifest = self.manifest()
        self.assertEqual("false", self.git("rev-parse", "--is-shallow-repository"))
        self.assertEqual(3, metadata["version"])
        self.assertEqual(base, manifest["previousCommit"])
        self.assertEqual("1.0.7", manifest["previousVersion"])
        self.assert_published(3)


if __name__ == "__main__":
    unittest.main()
