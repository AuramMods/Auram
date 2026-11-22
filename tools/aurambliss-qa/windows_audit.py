"""Prepare a Windows Oculus source audit and link the exported GLSL."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
import json
from pathlib import Path
import shutil
import subprocess

HERE = Path(__file__).resolve().parent


def prepare(shader, work):
    work.mkdir(parents=True)
    shutil.copytree(shader / "shaders", work / "shaders")
    heavy = (HERE / "fixtures/heavy.properties").read_text()
    heavy += "\nTRANSLUCENT_ENTITIES=true\nCLOUDS_INTERSECT_TERRAIN=false\nPOM=true\n"
    cases = [(f"{vendor.lower()}-{mode}-dh", vendor, mode, "true")
             for vendor in ("NVIDIA", "AMD", "INTEL") for mode in ("default", "compute", "compat")]
    cases.extend((f"nvidia-{mode}-no-dh", "NVIDIA", mode, "false") for mode in ("compute", "compat"))
    with (work / "cases.csv").open("w", newline="") as manifest:
        csv.writer(manifest).writerows(cases)
    (work / "default.properties").write_text("")
    (work / "compute.properties").write_text(heavy)
    (work / "compat.properties").write_text(heavy + "FORCE_BLISS_COMPAT=true\n")
    (work / "lifecycle.txt").write_text(f"audit-windows {work.resolve()}\n")


def verify(work, compiler):
    exports = work / "export"
    report = {}
    with (work / "cases.csv").open(newline="") as manifest, ThreadPoolExecutor(max_workers=6) as pool:
        for name, vendor, mode, dh in csv.reader(manifest):
            case = exports / name
            images = (case / "images.txt").read_text().splitlines()
            groups = sorted({shader.parent for shader in case.rglob("shader.*")})
            compute = sum((group / "shader.comp").exists() for group in groups)
            if not groups or (mode == "compute" and (len(images) != 4 or compute != 6)) or (mode != "compute" and (images or compute)):
                raise RuntimeError(f"Unexpected shader or image coverage in {case}")

            def link(group):
                stages = sorted(group.glob("shader.*"))
                result = subprocess.run([compiler, "-l", *map(str, stages)], capture_output=True, text=True)
                if result.returncode:
                    (group / "link.log").write_text(result.stdout + result.stderr)
                    return str(group.relative_to(work))
                return None

            failures = [failure for failure in pool.map(link, groups) if failure]
            report[name] = {"vendor": vendor, "mode": mode, "dh": dh == "true", "graphics": len(groups) - compute,
                            "compute": compute, "images": len(images), "failures": failures}
    (work / "link-results.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"Linked {sum(case['graphics'] for case in report.values())} graphics programs and "
          f"{sum(case['compute'] for case in report.values())} compute stages across {len(report)} cases")
    if any(case["failures"] for case in report.values()):
        raise SystemExit(f"Link errors are listed in {work / 'link-results.json'}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    initialize = commands.add_parser("prepare")
    initialize.add_argument("shader", type=Path)
    initialize.add_argument("work", type=Path)
    link = commands.add_parser("verify")
    link.add_argument("work", type=Path)
    link.add_argument("--compiler", default="glslangValidator")
    args = parser.parse_args()
    if args.action == "prepare":
        prepare(args.shader, args.work)
    else:
        verify(args.work, args.compiler)
