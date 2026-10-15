"""Verify isolated optimized/obfuscated Release runs and meaningful negative controls."""
import argparse
import json
import os
import re
import shutil
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--remote-version", help="Download this KeepKit version from JitPack instead of includeBuild")
arguments = parser.parse_args()

sample = Path(__file__).resolve().parent
wrapper = sample.parents[1] / ("gradlew.bat" if os.name == "nt" else "gradlew")
output = sample / "build" / "verification"
output.mkdir(parents=True, exist_ok=True)
results = []
(output / "results.json").write_text("[]\n", encoding="utf-8")
coordinates = {
    "datastore": "androidx.datastore:datastore-preferences-core-jvm",
    "oshi": "com.github.oshi:oshi-core",
}
cases = [(scenario, False) for scenario in ("datastore", "datastore-okio", "oshi")]
cases += [(scenario, True) for scenario in coordinates]
for scenario, negative in cases:
    name = scenario + ("-negative" if negative else "-positive")
    command = [str(wrapper), "-p", str(sample), "runRelease", "-Pscenario=" + scenario, "--console=plain"]
    if arguments.remote_version:
        command.append("-PkeepKitRemoteVersion=" + arguments.remote_version)
    if negative:
        command.append("-PexcludeRules=" + coordinates[scenario])
    log = output / (name + ".log")
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=wrapper.parent, stdout=stream, stderr=subprocess.STDOUT, timeout=300)
    text = log.read_text(encoding="utf-8", errors="replace")
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
