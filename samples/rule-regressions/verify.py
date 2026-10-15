#!/usr/bin/env python3
"""Exercise each repaired contract, including one-rule-at-a-time negative controls."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--family", choices=("all", "jna", "logback", "dbus"), default="all")
arguments = parser.parse_args()
sample = Path(__file__).resolve().parent
root = sample.parents[1]
wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
output = sample / "build" / "verification"
output.mkdir(parents=True, exist_ok=True)
results = []
result_file = output / ("results.json" if arguments.family == "all" else f"results-{arguments.family}.json")
result_file.write_text("[]\n", encoding="utf-8")

cases = {
    "jna": ["lifecycle", "pointer-constructor", "native-mapped-enum", "byreference-string",
            "direct-mapping", "explicit-direct-mapping", "callback-gc", "shared-memory"],
    "logback": ["logback-enum", "logback-level", "logback-duration", "logback-xml"],
    "dbus": ["dbus-property", "dbus-annotation"],
}
# Every negative omits only its named rule. In particular, direct-stack still
# preserves native names, and dbus-annotation still preserves annotation enums.
negatives = {
    "jna": [
        ("lifecycle", "lifecycle", r"AssertionError: JNA disposed while Native class is live"),
        ("pointer-constructor", "pointer-constructor", r"NoSuchMethodException: .*<init>"),
        ("native-mapped-enum", "native-mapped-enum", r"NullPointerException: .*getEnumConstants"),
        ("byreference-string", "byreference-string", r"AssertionError: ByReference Contract violated"),
        ("direct-names", "explicit-direct-mapping", r"UnsatisfiedLinkError: Error looking up function"),
        ("direct-stack", "direct-mapping", r"(?:Can't determine class with native methods|must be called from the static initializer)"),
    ],
    "logback": [
        ("logback-enum", "logback-enum", r"IllegalArgumentException: .* is not an enum class"),
        ("logback-level", "logback-level", r"AssertionError: Level conversion failed"),
        ("logback-duration", "logback-duration", r"AssertionError: Duration conversion failed"),
    ],
    "dbus": [
        ("dbus-property", "dbus-property", r"AnnotationFormatError: Invalid default"),
        ("dbus-annotation", "dbus-annotation", r"AssertionError: D-Bus annotation lost"),
    ],
}


def record(name, passed, **details):
    results.append(dict(name=name, passed=passed, **details))
    print(f"{'PASS' if passed else 'FAIL'} {name}", flush=True)
    result_file.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")


def prepare(family, version, module, omit):
    name = f"{family}-{module}-{version}-{omit or 'positive'}"
    command = [str(wrapper), "-p", str(sample), "shrinkRegression", "--console=plain",
               f"-Pscenario={family}", f"-PdependencyVersion={version}", f"-PjnaModule={module}", f"-Pomit={omit}"]
    log = output / f"{name}-build.log"
    with log.open("w", encoding="utf-8") as stream:
        process = subprocess.run(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT, timeout=300)
    if process.returncode:
        record(name + "-build", False, exitCode=process.returncode, log=str(log.relative_to(sample)))
        return None
    directory = sample / "build" / name
    metadata = json.loads((directory / "runtime.json").read_text(encoding="utf-8"))
    # No extra whole-package keeps or global optimizer switches may mask positives.
    mapping = (directory / "mapping.txt").read_text(encoding="utf-8")
    usage = (directory / "usage.txt").read_text(encoding="utf-8")
    prefix = {"jna": "com.sun.jna.", "logback": "ch.qos.logback.", "dbus": "org.freedesktop.dbus."}[family]
    renamed = any(a.startswith(prefix) and a != b for a, b in re.findall(r"^([^ #]+) -> ([^:]+):$", mapping, re.MULTILINE))
    if not renamed or "audit.UnusedRegressionMarker" not in usage:
        record(name + "-shrinking", False)
        return None
    return name, metadata


def run(prepared, case, expected=None, plain=False):
    name, metadata = prepared
    name += "-" + case + ("-plain" if plain else "")
    command = [metadata["java"], "-XX:-CreateCoredumpOnCrash", f"-XX:ErrorFile={output / (name + '-hs-error.log')}", "-Djava.awt.headless=true",
               "-cp", metadata["plainClasspath"] if plain else metadata["jar"], metadata["main"], case]
    process = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=45)
    text = process.stdout + process.stderr
    (output / f"{name}.log").write_text(text, encoding="utf-8")
    passed = (process.returncode != 0 and re.search(expected, text) is not None) if expected else (
        process.returncode == 0 and f"PASS {case}" in text)
    record(name, passed, exitCode=process.returncode, expected=expected)


try:
    for family in cases:
        if arguments.family not in ("all", family):
            continue
        versions = [("5.15.0", "jna"), ("5.16.0", "jna-jpms"), ("5.19.1", "jna")] if family == "jna" else (
            [("1.5.16", "jna"), ("1.6.5", "jna")] if family == "logback" else [("5.2.0", "jna")])
        for version, module in versions:
            positive = prepare(family, version, module, "")
            if positive:
                for case in cases[family]:
                    run(positive, case, plain=True)
                    run(positive, case)
            for omit, case, expected in negatives[family]:
                # The stack-depth regression was reproduced on the pre-StackWalker releases.
                if omit == "direct-stack" and version == "5.19.1":
                    continue
                negative = prepare(family, version, module, omit)
                if negative:
                    run(negative, case, expected)
except (subprocess.TimeoutExpired, OSError, ValueError, KeyError) as error:
    record("verification-infrastructure", False, error=str(error))
raise SystemExit(0 if results and all(result["passed"] for result in results) else 1)
