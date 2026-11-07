"""Verify each Release rule by removing exactly one protection."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import re
import subprocess

sample = Path(__file__).resolve().parent
root = sample.parents[2]
wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
cases = json.loads((sample / "cases.json").read_text(encoding="utf-8"))


def run(command, log, timeout=300):
    with log.open("w", encoding="utf-8") as stream:
        try:
            return subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT,
                                  cwd=root, timeout=timeout).returncode
        except subprocess.TimeoutExpired:
            stream.write(f"Timed out after {timeout}s\n")
            return -1


def remove_rule(text, prefix):
    pattern = re.compile(r"^" + re.escape(prefix) + r"[^\n{]*(?:\{[^}]*\})?\n", re.M)
    updated, count = pattern.subn("", text)
    if count != 1:
        raise ValueError(f"Expected exactly one rule beginning {prefix!r}, found {count}")
    return updated


def verify_scenario(scenario):
    case = cases[scenario]
    directory = sample.parent / "build" / "verification" / "release-rules" / scenario
    directory.mkdir(parents=True, exist_ok=True)
    build = run([str(wrapper), "-p", str(sample.parent), ":release-rules:prepareReleaseProbes",
                 f"-PreleaseScenario={scenario}", "--console=plain"], directory / "prepare.log")
    if build:
        print(f"FAIL {scenario} preparation", flush=True)
        return {scenario: False}
    inputs = json.loads((directory / "runtime.json").read_text(encoding="utf-8"))
    java = [inputs["java"], "-XX:ActiveProcessorCount=2", "-Xmx3g" if scenario == "bossterm" else "-Xmx2g"]
    source = (directory / "input.pro").read_text(encoding="utf-8")
    original = "\n".join(Path(fragment).read_text(encoding="utf-8") for fragment in inputs["fragments"])

    def execute(label, omission):
        output = directory / label
        output.mkdir(exist_ok=True)
        if label == "plain":
            return all(run(java + ["-Xverify:all", "-cp", inputs["plainClasspath"], "smoke.Main", probe],
                           output / f"{probe}.log", 40) == 0 for probe in case["probes"])
        rules = original
        for prefix in omission.get("rules", []):
            rules = remove_rule(rules, prefix)
        jar = output / "output.jar"
        mapping = output / "mapping.txt"
        usage = output / "usage.txt"
        config = output / "release.pro"
        config.write_text(source + rules + f"\n-outjars '{jar}'\n-printmapping '{mapping}'\n-printusage '{usage}'\n",
                          encoding="utf-8")
        log = output / "shrink.log"
        status = run(java + ["-cp", inputs["proguard"], "proguard.ProGuard", "@" + str(config)], log)
        if "build" in omission:
            return status > 0 and re.search(r"^Warning:.*(?:" + omission["build"] + ")",
                                            log.read_text(encoding="utf-8", errors="replace"), re.M) is not None
        if status != 0:
            return False
        if label == "positive":
            renamed = re.search(r"^smoke\.Probe -> (.+):$", mapping.read_text(encoding="utf-8"), re.M)
            if renamed is None or renamed[1] == "smoke.Probe" or "smoke.Unused" not in usage.read_text(encoding="utf-8"):
                return False
        classpath = os.pathsep.join(filter(None, [str(jar), inputs["releaseLibraries"]]))
        for probe in ([omission["probe"]] if omission else case["probes"]):
            log = output / f"{probe}.log"
            status = run(java + ["-Xverify:all", "-cp", classpath, "smoke.Main", probe], log, 40)
            if omission:
                return status > 0 and re.search(omission["error"], log.read_text(encoding="utf-8", errors="replace")) is not None
            if status != 0:
                return False
        return True

    matrix = [("plain", {}), ("positive", {}), *case["omissions"].items()]
    result = {}
    with ThreadPoolExecutor(max_workers=2) as pool:
        for (label, _), passed in zip(matrix, pool.map(lambda item: execute(*item), matrix)):
            result[f"{scenario}-{label}"] = passed
            print(f"{'PASS' if passed else 'FAIL'} {scenario}-{label}", flush=True)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scenarios", nargs="*", metavar="scenario")
    options = parser.parse_args()
    unknown = set(options.scenarios) - cases.keys()
    if unknown:
        parser.error(f"Unknown scenarios: {', '.join(sorted(unknown))}")
    results = {}
    for name in options.scenarios or cases:
        results.update(verify_scenario(name))
    report = sample.parent / "build" / "verification" / "release-rules" / "results.json"
    report.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(0 if all(results.values()) else 1)
