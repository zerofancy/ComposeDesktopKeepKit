"""Run isolated shrink/obfuscation fixtures and check the stage of each failure."""
import json
import os
from pathlib import Path
import subprocess

sample = Path(__file__).resolve().parent
wrapper = sample.parents[1] / ("gradlew.bat" if os.name == "nt" else "gradlew")
output = sample / "build" / "verification"
output.mkdir(parents=True, exist_ok=True)
results = []
cases = [(scenario + "-positive", scenario, [], None) for scenario in
         ("coroutines-core", "coroutines-swing", "jsoup")]
cases += [(scenario + "-negative", scenario, ["-Pnegative=true"], "verifySmoke") for scenario in
          ("coroutines-core", "coroutines-swing")]
cases += [("jsoup-warnings-negative", "jsoup", ["-Pnegative=true"], "shrinkSmoke"),
          ("jsoup-reflection-negative", "jsoup", ["-Pnegative=true", "-PwarningsOnly=true"], "verifySmoke")]
for name, scenario, properties, failure_stage in cases:
    command = [str(wrapper), "-p", str(sample), "verifySmoke", "-Pscenario=" + scenario,
               "--console=plain", *properties]
    log = output / (name + ".log")
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=wrapper.parent, stdout=stream, stderr=subprocess.STDOUT,
                                timeout=240)
    text = log.read_text(encoding="utf-8", errors="replace")
    passed = result.returncode == 0 if failure_stage is None else (
        result.returncode != 0 and f"> Task :{failure_stage} FAILED" in text)
    # An arbitrary Gradle failure must never count as a successful negative control.
    if name == "jsoup-warnings-negative":
        passed = passed and "can't find referenced class com.google.re2j." in text
    if name == "jsoup-reflection-negative":
        passed = passed and "ClassNotFoundException: org.jsoup.helper.RequestAuthHandler" in text
    results.append(dict(name=name, passed=passed, exitCode=result.returncode, failureStage=failure_stage))
    print(f"{'PASS' if passed else 'FAIL'} {name}", flush=True)
(output / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
raise SystemExit(0 if all(result["passed"] for result in results) else 1)
