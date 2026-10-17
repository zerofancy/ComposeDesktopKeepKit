"""Run isolated shrink/obfuscation fixtures and check the stage of each failure."""
import argparse
import sys
import json
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--only", nargs="+", help="Run selected case names")
parser.add_argument("--list-cases", action="store_true", help="Print case names without building")
arguments = parser.parse_args()

sample = Path(__file__).resolve().parent
wrapper = sample.parents[1] / ("gradlew.bat" if os.name == "nt" else "gradlew")
sys.path.insert(0, str(sample.parents[1] / "scripts/ci"))
from check_inputs import check_inputs
output = sample / "build" / "verification"
results = []
cases = [(scenario + "-positive", scenario, [], None) for scenario in
         ("coroutines-core", "coroutines-swing", "jsoup")]
cases += [(scenario + "-negative", scenario, ["-Pnegative=true"], "verifySmoke") for scenario in
          ("coroutines-core", "coroutines-swing")]
cases += [("jsoup-warnings-negative", "jsoup", ["-Pnegative=true"], "shrinkSmoke"),
          ("jsoup-reflection-negative", "jsoup", ["-Pnegative=true", "-PwarningsOnly=true"], "verifySmoke")]
known = {case[0] for case in cases}
if arguments.list_cases:
    print(json.dumps(sorted(known)))
    raise SystemExit(0)
if arguments.only and not set(arguments.only) <= known:
    parser.error("Unknown --only result name: " + ", ".join(sorted(set(arguments.only) - known)))
output.mkdir(parents=True, exist_ok=True)
(output / "results.json").write_text("[]\n", encoding="utf-8")
for name, scenario, properties, failure_stage in cases:
    if arguments.only and name not in arguments.only:
        continue
    command = [str(wrapper), "-p", str(sample), "verifySmoke", "-Pscenario=" + scenario,
               "--console=plain", *properties]
    log = output / (name + ".log")
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=wrapper.parent, stdout=stream, stderr=subprocess.STDOUT,
                                timeout=240)
    text = log.read_text(encoding="utf-8", errors="replace")
    if result.returncode == 0 and failure_stage is None:
        check_inputs("kzagent-dependencies", scenario, sample.parents[1])
    passed = result.returncode == 0 if failure_stage is None else (
        result.returncode != 0 and f"> Task :{failure_stage} FAILED" in text)
    # An arbitrary Gradle failure must never count as a successful negative control.
    if name == "coroutines-core-negative":
        passed = passed and "NoSuchElementException: Collection is empty" in text
    if name == "coroutines-swing-negative":
        passed = passed and "Module with the Main dispatcher is missing" in text
    if name == "jsoup-warnings-negative":
        passed = passed and "can't find referenced class com.google.re2j." in text
    if name == "jsoup-reflection-negative":
        passed = passed and "ClassNotFoundException: org.jsoup.helper.RequestAuthHandler" in text
    results.append(dict(name=name, passed=passed, exitCode=result.returncode, failureStage=failure_stage))
    (output / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(f"{'PASS' if passed else 'FAIL'} {name}", flush=True)
(output / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
raise SystemExit(0 if all(result["passed"] for result in results) else 1)
