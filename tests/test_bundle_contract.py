import copy
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from bundle_contract import canonical, selected, validate


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.policy = json.loads((ROOT / "distribution/policy.json").read_text())
        self.document = json.loads((ROOT / "distribution/fixture.json").read_text())

    def test_shared_fixture_and_canonical_bytes(self):
        validate(self.document, self.policy)
        self.assertEqual(canonical(self.document), (ROOT / "distribution/fixture.json").read_bytes())

    def test_paths_identities_locators_and_cycles_are_rejected(self):
        for path in ("minecraft/../outside", "minecraft/CON.txt", "minecraft/a.", "minecraft/a:b", "/tmp/file", "minecraft/a\\b"):
            doc = copy.deepcopy(self.document)
            doc["files"][path] = next(iter(doc["files"].values()))
            with self.assertRaises(ValueError): validate(doc, self.policy)
        for mutate in (
            lambda d: d.update(repository="somebody/else"),
            lambda d: d["target"].update(sha="not-a-commit"),
            lambda d: d["files"]["mmc-pack.json"].update(mode="120000"),
            lambda d: d["files"].update({"Mmc-pack.json": d["files"]["mmc-pack.json"]}),
            lambda d: d["objects"].update({"0" * 40: "1" * 64}),
            lambda d: d["history"][0].update(base=d["target"]),
        ):
            doc = copy.deepcopy(self.document); mutate(doc)
            with self.assertRaises(ValueError): validate(doc, self.policy)

    def test_shared_scope_omits_runtime_and_author_data(self):
        for path in ("minecraft/libraries/a.jar", "minecraft/saves/world/level.dat", "server_files/ops.json", "server_files/eula.txt", "server_files/world/level.dat", "scripts/release.py"):
            self.assertFalse(selected(path, self.policy), path)
        for path in ("minecraft/mods/a.jar", "minecraft/config/a.toml", "minecraft/schematics/a.nbt", "server_config.conf", "server_files/server.properties"):
            self.assertTrue(selected(path, self.policy), path)


if __name__ == "__main__": unittest.main()
