"""Map Bliss's profile and menu controls to source references and test scenes."""

import argparse
from collections import defaultdict
import json
from pathlib import Path
import re

SCENES = {
    "Direct_Light": "Fixed sun angle and daylight. Compare the penumbra, colored shadow, contact occlusion and leaf transmission.",
    "World": "Matched shore and forest views above and below water. Compare waves, plants, sky, fog and refraction.",
    "Ambient_light": "Dark room with a held lamp, tinted glass, candles, a loose item and nearby light-emitting entities. Read the LPV and surfaces.",
    "Fog": "Fixed route through weather, caves, water, the Nether and the End. Compare transmittance, light shafts and horizon fog.",
    "Post_Processing": "Matched frames of motion, defocus, a bright light and a dark surface. Compare both tone curves and temporal output.",
    "Clouds": "Fixed daytime and weather while comparing low and high layers, sun shadows, and foreground intersection.",
    "Resource_Pack_Support": "Identical views of labPBR materials, wet ground, emissive and reflective surfaces, and the resource-pack sky.",
    "Climate": "Use the same view of leaves, plants and biome layers at each season and weather condition.",
    "Misc_Settings": "Repeat the applicable rendering scene with the debug or experimental control changed and inspect the named buffer or effect.",
    "Mod_support": "Run a full Auram shoreline with shader-lit cached and fresh DH terrain and water. Compare matched frames and native depth buffers.",
}


def properties(text):
    joined = re.sub(r"\\[ \t]*\n[ \t]*", " ", text)
    return dict(re.findall(r"(?m)^[ \t]*([\w.]+)[ \t]*=[ \t]*([^\n]+)", joined))


def get_menu_controls(settings):
    controls = defaultdict(list)

    def visit(menu, parents):
        key = ".".join(("screen", menu)) if menu else "screen"
        for token in settings.get(key, "").split():
            if token.startswith("[") and token.endswith("]"):
                child = token[1:-1]
                if child not in parents:
                    visit(child, (*parents, child))
            elif re.fullmatch(r"[a-zA-Z_]\w*", token):
                controls[token].append(parents)
    visit("", ())
    return controls


def source_references(shader_root, options):
    declarations = defaultdict(list)
    references = defaultdict(list)
    macro = re.compile(r"^\s*(?://\s*)?#define\s+([A-Za-z_]\w*)\b")
    token = re.compile(r"\b[A-Za-z_]\w*\b")
    extensions = {".glsl", ".fsh", ".vsh", ".gsh", ".csh", ".properties"}
    for file in sorted(shader_root.rglob("*")):
        if file.suffix not in extensions or file.name in ("block.properties", "entity.properties", "item.properties"):
            continue
        name = file.relative_to(shader_root).as_posix()
        source = re.sub(r"/\*.*?\*/", lambda m: "\n" * m[0].count("\n"), file.read_text(), flags=re.S)
        for lineno, text in enumerate(source.splitlines(), 1):
            if name == "shaders.properties" and re.match(r"\s*(screen|sliders|profile)\b", text):
                continue
            if match := macro.match(text):
                if match[1] in options:
                    declarations[match[1]].append(f"{name}:{lineno}")
            value = text.partition("//")[0]
            for word in sorted(set(token.findall(value)) & options):
                if not match or word != match[1]:
                    references[word].append(f"{name}:{lineno}")
    return declarations, references


def inventory(shader_root):
    settings = properties((shader_root / "shaders.properties").read_text())
    controls = get_menu_controls(settings)
    declarations, references = source_references(shader_root, set(controls))
    return {
        "profiles": {key.removeprefix("profile."): value.split() for key, value in settings.items() if key.startswith("profile.")},
        "controls": {
            key: {
                "menus": ["/".join(path) for path in paths],
                "definitions": declarations[key],
                "references": references[key],
                "scenes": list(dict.fromkeys(SCENES.get(path[0], SCENES["Misc_Settings"]) for path in paths if path)),
            }
            for key, paths in sorted(controls.items())
        },
        "textures": {key: value for key, value in settings.items() if key.startswith(("texture.", "customTexture."))},
        "dimension_programs": {name: sorted(path.name for path in (shader_root / name).iterdir() if path.is_file())
                               for name in ("world-1", "world0", "world1")},
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pack", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    data = inventory(args.pack / "shaders")
    args.output.write_text(json.dumps(data, indent=2) + "\n")
