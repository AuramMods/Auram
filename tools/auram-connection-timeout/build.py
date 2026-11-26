"""Build the bundled timeout mod with a JDK 17 or later."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

source = Path(__file__).resolve().parent
pack = source.parents[1]
libraries = pack / "server/libraries"
mods = pack / "minecraft/mods"

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--jdk-bin", type=Path, help="JDK bin directory when it is outside PATH")
args = parser.parse_args()

components = json.loads((pack / "mmc-pack.json").read_text())["components"]
minecraft = next(component["version"] for component in components if component["uid"] == "net.minecraft")
forge = next(component["version"] for component in components if component["uid"] == "net.minecraftforge")
forge_version = f"{minecraft}-{forge}"
classpath = os.pathsep.join(map(str, (
    libraries / f"net/minecraftforge/javafmllanguage/{forge_version}/javafmllanguage-{forge_version}.jar",
    libraries / "org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar",
    mods / "packetfixer-3.3.1-1.18-1.20.4-merged.jar",
)))
javac = str(args.jdk_bin / "javac") if args.jdk_bin else "javac"
jar = str(args.jdk_bin / "jar") if args.jdk_bin else "jar"
output = mods / "auram-connection-timeout-1.0.0.jar"

with tempfile.TemporaryDirectory() as classes:
    subprocess.run([
        javac, "--release", "17", "-cp", classpath, "-d", classes,
        str(source / "AuramConnectionTimeout.java"),
    ], check=True)
    subprocess.run([
        jar, "--create", "--file", str(output), "--date=2025-01-01T00:00:00Z",
        "-C", classes, "dev", "-C", str(source), "META-INF",
    ], check=True)

print(output)
