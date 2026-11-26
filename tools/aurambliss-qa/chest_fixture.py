"""Generate the paired crystal-chest benchmark for the disposable shader client."""

import argparse
from pathlib import Path


CAMERA = "server tp @p 48 82 23 0 7"
MATERIALS = ("diamond", "emerald", "gold_ingot", "iron_ingot", "lapis_lazuli", "redstone", "amethyst_shard")
MIXED = ("minecraft:glass", "minecraft:gold_ingot", "ironchest:iron_chest", "minecraft:lapis_lazuli",
         "minecraft:iron_ingot", "minecraft:amethyst_shard", "minecraft:enchanted_book")


def write(path, lines):
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def items(ids):
    stacks = [f'{{Slot:{index}b,id:"{name}",Count:{64 - index * 4}b}}' for index, name in enumerate(ids)]
    return "{Items:[" + ",".join(stacks) + "]}"


def fixture(kind):
    clear = ["fill 32 74 40 63 89 40 minecraft:air"]
    chest = "ironchest:crystal_chest[facing=north]"
    height = 2 if kind == "small" else 16
    rows = []
    for y in range(74, 74 + height):
        contents = "" if kind == "empty" else items(
            MIXED if kind == "mixed" and y % 2 == 0 else (f"minecraft:{name}" for name in MATERIALS))
        rows.append(f"fill 32 {y} 40 63 {y} 40 {chest}{contents}")
    return clear + rows


def generate(folder, rounds, cases):
    folder = folder.resolve()
    folder.mkdir(parents=True, exist_ok=True)
    setup = folder / "setup.mcfunction"
    write(setup, [
        "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
        "gamerule doFireTick false", "gamerule doEntityDrops false", "gamerule doTileDrops false",
        "weather clear", "time set 4000", "gamemode spectator @p", CAMERA.removeprefix("server "),
        "fill 29 73 26 66 73 52 minecraft:stone",
    ])
    commands = [f"server-batch {setup}", "hide-hud"]
    for name in cases:
        scene = folder / f"{name}.mcfunction"
        write(scene, fixture(name))
        expected = {"empty": "512,0,0", "small": "64,64,0"}.get(name, "512,512,0")
        commands += [f"server-batch {scene}", "refresh-chests", CAMERA, "wait 7", f"verify-chests {expected}",
                     "snapshot light=positive"]
        for trial in range(1, rounds + 1):
            for enabled in (False, True) if trial % 2 else (True, False):
                backend = "cache" if enabled else "stock"
                label = f"{name}-{backend}-{trial}"
                commands += [f"chest-model-cache {str(enabled).lower()}", "wait 3", f"profile {label}"]
                if trial == 1:
                    commands += ["freeze-shader-time 37.25", "wait 2", f"capture {label}", "freeze-shader-time off"]

    if "mixed" in cases:
        commands += [f"server data merge block 32 74 40 {items(('minecraft:emerald', 'minecraft:glass'))}",
                     "refresh-chests", "wait 4", "verify-chests 512,512,0", "verify-chest-top 32 74 40 minecraft:emerald"]
        for enabled in (False, True):
            backend = "cache" if enabled else "stock"
            commands += [f"chest-model-cache {str(enabled).lower()}", CAMERA, "wait 4", f"profile moving-{backend} 7,0,-17"]
        commands += ["resources", CAMERA, "wait 7", "verify-chest-top 32 74 40 minecraft:emerald",
                     "profile resource-reload-cache"]
    write(folder / "lifecycle.txt", commands)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--case", choices=("empty", "small", "loaded", "mixed"), action="append")
    args = parser.parse_args()
    generate(args.folder, args.rounds, args.case or ("empty", "small", "loaded", "mixed"))
