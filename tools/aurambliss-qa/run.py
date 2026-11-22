"""Launch a disposable, offline Prism Forge client and observe its renderer log."""

import argparse
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent
GAME_VERSION = "1.20.1"
CLIENT_REVISION = "1.20.1-20230612.114412"
ASM_EXPORT = "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED"
RENDER_ERROR = re.compile(
    r"Failed to create shader rendering pipeline|A fatal error has been detected|"
    r"MixinTransformerError|Mod Loading has failed"
)


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("instance", type=Path, help="PrismLauncher instance directory")
    parser.add_argument("world", type=Path, help="Existing test save to copy")
    parser.add_argument("--shader", type=Path, required=True, help="Shader directory or ZIP")
    parser.add_argument("--java", type=Path, required=True, help="Native Java 17 executable")
    parser.add_argument("--prism", type=Path, help="PrismLauncher data directory")
    parser.add_argument("--work", type=Path, help="New work directory for the copy and logs")
    parser.add_argument("--forge", default="47.4.10")
    parser.add_argument("--dh", default="3.3.3")
    parser.add_argument("--installer", type=Path, help="Forge installer used by the Prism instance")
    parser.add_argument("--options", type=Path, help="Shader properties fixture")
    parser.add_argument("--dh-config", type=Path, help="Distant Horizons config fixture")
    parser.add_argument("--companion", type=Path, help="Compatibility JAR built from this branch")
    parser.add_argument("--probe", type=Path, action="append", default=[], help="Test-only Forge JAR")
    parser.add_argument("--readback", choices=("depth", "light", "entities", "perf"),
                        help="Ask the AuramBliss test probe to sample live textures")
    parser.add_argument("--no-dh", action="store_true")
    parser.add_argument("--force-compat", action="store_true", help="Use the macOS rendering path")
    parser.add_argument("--full", action="store_true", help="Copy the complete Minecraft pack")
    parser.add_argument("--size", default="854x480")
    parser.add_argument("--heap", default="5g")
    parser.add_argument("--java-option", action="append", default=[])
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--seconds", type=int, default=12)
    return parser.parse_args()


def copy_file(source, target):
    if platform.system() == "Darwin":
        result = subprocess.run(["/bin/cp", "-cp", source, target], capture_output=True)
        if result.returncode == 0:
            return target
    return shutil.copy2(source, target)


def prepare(args, work):
    game = work / "minecraft"
    instance = args.instance / "minecraft"
    if args.full:
        shutil.copytree(instance, game, ignore=shutil.ignore_patterns("saves", "logs", "crash-reports"),
                        copy_function=copy_file)
    for part in ("mods", "config", "shaderpacks"):
        (game / part).mkdir(parents=True, exist_ok=True)
    if not args.full:
        required = ["embeddium-0.3.31+mc1.20.1.jar", "oculus-mc1.20.1-1.8.0.jar"]
        if not args.no_dh:
            required.append(f"DistantHorizons-{args.dh}-1.20.1-fabric-forge.jar")
        for name in required:
            copy_file(instance / "mods" / name, game / "mods" / name)
    if args.no_dh:
        for mod in (game / "mods").glob("DistantHorizons-*.jar"):
            mod.unlink()
    if args.companion:
        for old in (game / "mods").glob("auramphoton-compat-*.jar"):
            old.unlink()
        copy_file(args.companion, game / "mods" / args.companion.name)
    for source in args.probe:
        copy_file(source, game / "mods" / source.name)
    if args.dh_config:
        copy_file(args.dh_config, game / "config/DistantHorizons.toml")

    shader = game / "shaderpacks" / args.shader.name
    if args.shader.is_dir():
        shutil.copytree(args.shader, shader, dirs_exist_ok=True, copy_function=copy_file)
    else:
        copy_file(args.shader, shader)
    shader_options = shader.with_name(shader.name + ".txt")
    if args.options:
        copy_file(args.options, shader_options)
    if args.force_compat:
        with shader_options.open("a") as options:
            options.write("\nFORCE_BLISS_COMPAT=true\n")
    (game / "config/oculus.properties").write_text(
        f"shaderPack={shader.name}\nenableShaders=true\nenableDebugOptions=true\n"
        "disableUpdateMessage=true\nmaxShadowRenderDistance=12\ncolorSpace=SRGB\n"
    )
    (game / "config/fml.toml").write_text("earlyWindowControl=false\n")
    profile = game / "options.txt"
    resource_settings = []
    if profile.exists():
        resource_settings = [line for line in profile.read_text().splitlines()
                             if line.startswith(("resourcePacks:", "incompatibleResourcePacks:"))]
    profile.write_text(
        "version:3465\nrenderDistance:8\nsimulationDistance:5\nmaxFps:90\nenableVsync:false\nfullscreen:false\n"
        "pauseOnLostFocus:false\nsoundCategory_master:0.0\nnarrator:0\nonboardAccessibility:false\ntutorialStep:none\n"
        + "\n".join(resource_settings) + "\n"
    )
    shutil.copytree(args.world, game / "saves/ShaderLab", copy_function=copy_file)
    return game


def build_window_agent(work):
    classes = work / "agent"
    classes.mkdir()
    subprocess.run(["javac", "-source", "17", "-target", "17", "--add-exports", ASM_EXPORT,
                    "-d", classes, ROOT / "HiddenWindow.java"], check=True)
    agent = work / "hidden-window.jar"
    manifest = work / "manifest.mf"
    manifest.write_text("Manifest-Version: 1.0\nPremain-Class: HiddenWindow\n\n")
    subprocess.run(["jar", "--create", "--file", agent, "--manifest", manifest, "-C", classes, "."], check=True)
    return agent


def launch_command(args, work, game):
    prism = args.prism or args.instance.parent.parent
    libraries = prism / "libraries"
    forge = f"{GAME_VERSION}-{args.forge}"
    host = platform.system()
    family = {"Darwin": "osx", "Windows": "windows", "Linux": "linux"}[host]
    machine = platform.machine().lower()
    os_key = family + "-arm64" if machine in ("arm64", "aarch64") else family

    def manifest(component, version):
        return json.loads((prism / "meta" / component / f"{version}.json").read_text())

    def artifact(coordinate):
        group, name, version, *classifier = coordinate.split(":")
        path = "-".join((name, version, *classifier)) + ".jar"
        return libraries / group.replace(".", "/") / name / version / path

    def allowed(library):
        result = not library.get("rules")
        for rule in library.get("rules", []):
            if rule.get("os", {}).get("name", os_key) == os_key:
                result = rule["action"] == "allow"
        return result

    minecraft = manifest("net.minecraft", GAME_VERSION)
    components = (manifest("net.minecraftforge", args.forge), minecraft, manifest("org.lwjgl3", "3.3.1"))
    dependencies = [artifact(lib["name"]) for component in components for lib in component["libraries"]
                    if allowed(lib) and not lib["name"].startswith("io.github.zekerzhayard:")]
    dependencies.extend(artifact(f"net.minecraftforge:{module}:{forge}")
                        for module in ("fmlcore", "javafmllanguage", "lowcodelanguage", "mclanguage"))
    dependencies.extend((artifact(f"net.minecraft:client:{CLIENT_REVISION}:extra"),
                         artifact(f"net.minecraftforge:forge:{forge}:universal")))
    classpath = os.pathsep.join(map(str, dependencies))
    installer = args.installer or args.instance / f"forge-{forge}-installer.jar"
    with ZipFile(installer) as archive:
        launch = json.loads(archive.read("version.json"))["arguments"]
    substitutions = {"library_directory": str(libraries), "version_name": forge, "classpath_separator": os.pathsep}
    jvm = [re.sub(r"\$\{([^}]+)\}", lambda key: substitutions[key[1]], arg) for arg in launch["jvm"]]
    mac = ["-XstartOnFirstThread", "-Dapple.awt.UIElement=true"] if host == "Darwin" else []
    width, height = args.size.split("x")
    return [str(args.java.resolve()), *mac, "--add-exports", ASM_EXPORT, f"-javaagent:{build_window_agent(work)}",
            "-Xms512m", f"-Xmx{args.heap}", *args.java_option, "-DlegacyClassPath=" + classpath, "-cp", classpath,
            *jvm, "cpw.mods.bootstraplauncher.BootstrapLauncher", *launch["game"], "--username", "AuramShaderLab",
            "--version", forge, "--gameDir", str(game), "--assetsDir", str(prism / "assets"),
            "--assetIndex", minecraft["assetIndex"]["id"], "--uuid", "c353450e38ce3f339d996096d2a2ac45",
            "--accessToken", "0", "--userType", "legacy", "--versionType", "release",
            "--width", width, "--height", height, "--quickPlaySingleplayer", "ShaderLab"]


def observe(args, command, work, game):
    (work / "launch-arguments.json").write_text(json.dumps(command, indent=2) + "\n")
    log = work / "console.log"
    ready_at = None
    readback_requested = False
    entity_fixture_requested = False
    deadline = time.monotonic() + args.timeout
    with log.open("w") as output:
        child = subprocess.Popen(command, cwd=game, stdout=output, stderr=subprocess.STDOUT)
        try:
            while time.monotonic() < deadline:
                trace = log.read_text(errors="replace")
                if error := RENDER_ERROR.search(trace):
                    raise RuntimeError(f"{error[0]} in {log}")
                if child.poll() is not None:
                    raise RuntimeError(f"Client exited with {child.returncode}. See {log}")
                world_loaded = (
                    f"Using shaderpack: {args.shader.name}" in trace
                    and "Creating pipeline for dimension" in trace
                    and "joined the game" in trace
                    and (args.no_dh or "Renderer setup complete" in trace)
                )
                if world_loaded and ready_at is None:
                    if args.readback == "entities" and "Bliss fixture entities ready" not in trace:
                        if not entity_fixture_requested:
                            (game / "bliss-fixture-entities").touch()
                            entity_fixture_requested = True
                    else:
                        ready_at = time.monotonic()
                if ready_at is not None and time.monotonic() - ready_at >= args.seconds:
                    if args.readback:
                        if not readback_requested:
                            (game / f"bliss-read-{args.readback}").touch()
                            readback_requested = True
                        events = [json.loads(row.removeprefix("QA_EVT ")) for row in trace.splitlines()
                                  if row.startswith("QA_EVT ")]
                        if len(events) < (2 if args.readback == "depth" else 1):
                            time.sleep(0.5)
                            continue
                        (work / "readback.json").write_text(json.dumps(events, indent=2) + "\n")
                    print(f"Observed {args.seconds} seconds in world. See {log}")
                    return
                time.sleep(0.5)
            raise TimeoutError(f"Client startup timed out. See {log}")
        finally:
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=12)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait()


if __name__ == "__main__":
    args = parse_args()
    work = args.work.resolve() if args.work else Path(tempfile.mkdtemp(prefix="aurambliss-qa-")).resolve()
    if args.work:
        work.mkdir(parents=True)
    print(f"Disposable client at {work}", flush=True)
    game = prepare(args, work)
    observe(args, launch_command(args, work, game), work, game)
