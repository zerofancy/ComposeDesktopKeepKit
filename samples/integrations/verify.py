#!/usr/bin/env python3
"""Run real optimized/obfuscated positive and per-adapter negative controls."""
import json
import pathlib
import subprocess
import argparse
import os

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "samples/integrations/build/verification"
OUTPUT.mkdir(parents=True, exist_ok=True)
parser = argparse.ArgumentParser()
parser.add_argument("--only", nargs="+", help="Rerun selected result names")
selected = parser.parse_args().only


def run(name, task, properties=(), negative=False):
    if selected and name not in selected:
        return None
    command = [str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")), "-p", "samples/integrations", task,
               "--console=plain", *properties]
    with (OUTPUT / (name + ".log")).open("w") as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log,
                                stderr=subprocess.STDOUT, timeout=240)
    output = (OUTPUT / (name + ".log")).read_text(encoding="utf-8", errors="replace")
    runtime_failure = (":runRelease FAILED" in output or ":verifySerializationSmoke FAILED" in output)
    passed = result.returncode != 0 and runtime_failure if negative else result.returncode == 0
    print(f"{'PASS' if passed else 'FAIL'} {name} (exit {result.returncode})", flush=True)
    return {"name": name, "passed": passed, "exitCode": result.returncode,
            "negative": negative, "runtimeFailure": runtime_failure}


results = [run("serialization-positive", "verifySerializationSmoke", ["-Pisolated=serialization"]),
           run("serialization-negative", "verifySerializationSmoke",
               ["-Pisolated=serialization", "-PserializationNegative=true"], True),
           run("serialization-core-positive", "verifySerializationSmoke", ["-Pisolated=serialization-core"]),
           run("serialization-core-negative", "verifySerializationSmoke",
               ["-Pisolated=serialization-core", "-PserializationNegative=true"], True)]
cases = [
    # The root OkHttp component and its JVM artifact share one fragment.
    ("retrofit", "retrofit", "com.squareup.retrofit2:retrofit"),
    ("okhttp", "okhttp", "com.squareup.okhttp3:okhttp-jvm,com.squareup.okhttp3:okhttp"),
    ("slf4j", "slf4j", "org.slf4j:slf4j-api"),
    ("logback-core", "logback-core", "ch.qos.logback:logback-core"),
    ("logback-classic", "logback-classic", "ch.qos.logback:logback-classic"),
    ("jna", "jna", "net.java.dev.jna:jna"),
    ("jna-platform", "jna-platform", "net.java.dev.jna:jna-platform"),
    ("jna-jpms", "jna-jpms", "net.java.dev.jna:jna-jpms"),
    ("jna-platform-jpms", "jna-platform-jpms", "net.java.dev.jna:jna-platform-jpms"),
    ("dbus-core", "dbus-core", "com.github.hypfvieh:dbus-java-core"),
    ("dbus-transport", "dbus-transport", "com.github.hypfvieh:dbus-java-transport-native-unixsocket"),
    ("sqlite", "sqlite", "org.xerial:sqlite-jdbc"),
    ("llamatik", "llamatik", "com.llamatik:library-jvm"),
]
for name, scenario, dependency in cases:
    # Only this dependency is direct; all other libraries must come transitively.
    results.append(run(name + "-positive", "runRelease", ["-Pisolated=" + scenario]))
    results.append(run(name + "-negative", "runRelease",
                       ["-Pisolated=" + scenario, "-PexcludeRules=" + dependency], True))
for wrapper in ("filekit", "sqldelight", "retrofit-converter"):
    results.append(run(wrapper + "-positive", "runRelease", ["-Pisolated=" + wrapper]))
for scenario in ("logback-core", "logback-classic"):
    props = ["-Pisolated=" + scenario, "-PlogbackVersion=1.5.16"]
    results.append(run(scenario + "-1516-positive", "runRelease", props))
    results.append(run(scenario + "-1516-negative", "runRelease",
                       props + ["-PexcludeRules=ch.qos.logback:" + scenario], True))
results.append(run("logback-classic-1516-no-obfuscation", "runRelease",
                   ["-Pisolated=logback-classic", "-PlogbackVersion=1.5.16", "-PsmokeObfuscate=false"]))
results.append(run("restored-positive", "runRelease"))
results = [result for result in results if result is not None]
if selected and (OUTPUT / "results.json").exists():
    previous = json.loads((OUTPUT / "results.json").read_text(encoding="utf-8"))
    by_name = {result["name"]: result for result in previous}
    by_name.update({result["name"]: result for result in results})
    results = list(by_name.values())
(OUTPUT / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
raise SystemExit(0 if all(result["passed"] for result in results) else 1)
