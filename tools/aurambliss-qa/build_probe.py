"""Build a disposable Forge readback probe against PrismLauncher's installed libraries."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile

HERE = Path(__file__).resolve().parent


def build(instance, output):
    libraries = instance.parent.parent / "libraries"
    classpath = [
        instance / "minecraft/mods/oculus-mc1.20.1-1.8.0.jar",
        libraries / "org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar",
        libraries / "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar",
        libraries / "org/lwjgl/lwjgl-glfw/3.3.1/lwjgl-glfw-3.3.1.jar",
        libraries / "org/lwjgl/lwjgl-opengl/3.3.1/lwjgl-opengl-3.3.1.jar",
        libraries / "com/google/guava/guava/31.1-jre/guava-31.1-jre.jar",
        libraries / "it/unimi/dsi/fastutil/8.5.9/fastutil-8.5.9.jar",
        libraries / "net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar",
    ]
    config = {
        "required": True,
        "minVersion": "0.8",
        "package": "dev.auram.bliss.test",
        "compatibilityLevel": "JAVA_17",
        "client": ["DepthProbe", "LightProbe", "EntityFixture", "PerfFrameProbe", "PerfVolumeProbe", "LifecycleProbe",
                   "AuditDefines", "PerfProgramNames", "PerfProgramProbe", "PerfFullscreenProbe", "PerfDhProbe"],
        "injectors": {"defaultRequire": 1},
    }
    metadata = """modLoader="lowcodefml"
loaderVersion="[47,)"
license="Local QA"
[[mods]]
modId="auram_bliss_probe"
version="1"
displayName="AuramBliss QA"
"""
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="aurambliss-probe-") as classes:
        subprocess.run(["javac", "--release", "17", "-proc:none", "-cp", os.pathsep.join(map(str, classpath)),
                        "-d", classes, *sorted((HERE / "probe").glob("*.java"))], check=True)
        with ZipFile(output, "w", ZIP_DEFLATED) as jar:
            jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMixinConfigs: auram_bliss_probe.mixins.json\n\n")
            jar.writestr("META-INF/mods.toml", metadata)
            jar.writestr("pack.mcmeta", json.dumps({"pack": {"description": "AuramBliss readback probe", "pack_format": 15}}))
            jar.writestr("auram_bliss_probe.mixins.json", json.dumps(config, indent=2) + "\n")
            for path in sorted(Path(classes).rglob("*.class")):
                jar.write(path, path.relative_to(classes).as_posix())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("instance", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.instance, args.output)
