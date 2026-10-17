"""Verify isolated optimized/obfuscated Release runs and meaningful negative controls."""
import argparse
import sys
import json
import os
import re
import shutil
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--remote-version", help="Download this KeepKit version from JitPack instead of includeBuild")
parser.add_argument("--only", nargs="+", help="Run selected case names")
parser.add_argument("--list-cases", action="store_true", help="Print case names without building")
arguments = parser.parse_args()

sample = Path(__file__).resolve().parent
wrapper = sample.parents[1] / ("gradlew.bat" if os.name == "nt" else "gradlew")
sys.path.insert(0, str(sample.parents[1] / "scripts/ci"))
from check_inputs import check_inputs
output = sample / "build" / "verification"
results = []
coordinates = {
    "datastore": "androidx.datastore:datastore-preferences-core-jvm",
    "oshi": "com.github.oshi:oshi-core",
}
cases = [(scenario, False) for scenario in ("datastore", "datastore-okio", "oshi")]
cases += [(scenario, True) for scenario in coordinates]
known = {scenario + ("-negative" if negative else "-positive") for scenario, negative in cases}
if arguments.list_cases:
    print(json.dumps(sorted(known)))
    raise SystemExit(0)
if arguments.only and not set(arguments.only) <= known:
    parser.error("Unknown --only result name: " + ", ".join(sorted(set(arguments.only) - known)))
output.mkdir(parents=True, exist_ok=True)
(output / "results.json").write_text("[]\n", encoding="utf-8")
for scenario, negative in cases:
    name = scenario + ("-negative" if negative else "-positive")
    if arguments.only and name not in arguments.only:
        continue
    command = [str(wrapper), "-p", str(sample), "runRelease", "-Pscenario=" + scenario, "--console=plain"]
    if arguments.remote_version:
        command.append("-PkeepKitRemoteVersion=" + arguments.remote_version)
    if negative:
        command.append("-PexcludeRules=" + coordinates[scenario])
    log = output / (name + ".log")
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=wrapper.parent, stdout=stream, stderr=subprocess.STDOUT, timeout=300)
    text = log.read_text(encoding="utf-8", errors="replace")
    if result.returncode == 0 and not negative:
        check_inputs("floatclock-dependencies", scenario, sample.parents[1])
    # Preserve each run's diagnostics before the next scenario overwrites them.
    for kind in ("mapping", "usage"):
        source = output / f"{scenario}-{kind}.txt"
        if source.exists():
            shutil.copyfile(source, output / f"{name}-{kind}.txt")
    if negative:
        expected = "java.lang.NullPointerException: Cannot load from object array" if scenario == "oshi" else "Field "
        passed = (result.returncode != 0 and "> Task :runRelease FAILED" in text and expected in text)
        if scenario != "oshi":
            passed = passed and "not found. Known fields" in text
    else:
        passed = result.returncode == 0 and "PASS " in text
        mapping = (output / (scenario + "-mapping.txt")).read_text(encoding="utf-8")
        usage = (output / (scenario + "-usage.txt")).read_text(encoding="utf-8")
        renamed = any(original != renamed for original, renamed in re.findall(
            r"^(oshi\.[^ ]+|androidx\.datastore\.[^ ]+) -> ([^:]+):$", mapping, re.MULTILINE))
        passed = passed and renamed and "smoke.UnusedSmokeMarker" in usage
    results.append(dict(name=name, passed=passed, exitCode=result.returncode))
    (output / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(f"{'PASS' if passed else 'FAIL'} {name}", flush=True)
(output / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
raise SystemExit(0 if all(result["passed"] for result in results) else 1)
