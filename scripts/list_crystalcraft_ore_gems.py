#!/usr/bin/env python3
"""List Crystalcraft gems that drop from its ore worldgen blocks (read-only)."""

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import zipfile


MOD_ID = "crystalcraft_unlimited_java"


def objects(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from objects(child)
    elif isinstance(value, list):
        for child in value:
            yield from objects(child)


def discover(jar):
    with zipfile.ZipFile(jar) as archive:
        tags, features, loot = {}, {}, {}
        for name in archive.namelist():
            if not name.endswith(".json"):
                continue
            if name.startswith("data/") and "/tags/items/" in name:
                prefix, path = name.split("/tags/items/", 1)
                tags[prefix.split("/")[1]+":"+path[:-5]] = json.loads(archive.read(name))
            elif name.startswith(f"data/{MOD_ID}/worldgen/configured_feature/"):
                features[name] = json.loads(archive.read(name))
            elif name.startswith(f"data/{MOD_ID}/loot_tables/blocks/"):
                loot[MOD_ID+":"+Path(name).stem] = json.loads(archive.read(name))
        language = json.loads(archive.read(f"assets/{MOD_ID}/lang/en_us.json"))

    def tag_items(tag, ancestors=()):
        if tag in ancestors:
            return set()
        result = set()
        for value in tags.get(tag, {}).get("values", []):
            value = value["id"] if isinstance(value, dict) else value
            if value.startswith("#"):
                result.update(tag_items(value[1:], (*ancestors, tag)))
            else:
                result.add(value)
        return result

    gems = set()
    for tag in tags:
        if tag == "forge:gems" or tag.startswith("forge:gems/"):
            gems.update(tag_items(tag))

    ores = set()
    for feature in features.values():
        if feature.get("type") in {"minecraft:ore", "minecraft:scattered_ore"}:
            for obj in objects(feature.get("config", {})):
                state = obj.get("state")
                if isinstance(state, dict) and state.get("Name", "").startswith(MOD_ID+":"):
                    ores.add(state["Name"])

    sources = defaultdict(set)
    for ore in sorted(ores):
        for entry in objects(loot.get(ore, {})):
            item = entry.get("name", "")
            if entry.get("type") == "minecraft:item" and item.startswith(MOD_ID+":") and item in gems:
                sources[item].add(ore)

    return {
        "jar": jar.name,
        "jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "count": len(sources),
        "items": [{
            "item": item,
            "name": language.get("item."+item.replace(":", "."), item),
            "ores": sorted(sources[item]),
        } for item in sorted(sources)],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path, help="Installed Crystalcraft mod jar")
    parser.add_argument("--json", action="store_true", help="Print provenance and item records as JSON")
    args = parser.parse_args()
    report = discover(args.jar)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print("Item ID\tDisplay name\tSource ore IDs")
        for item in report["items"]:
            print(item["item"]+"\t"+item["name"]+"\t"+", ".join(item["ores"]))
        print(f"\n{report['count']} distinct ore-drop gem items.")


if __name__ == "__main__":
    main()
