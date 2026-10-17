"""Run a generated matrix entry as arguments, never interpolate it into a shell."""
import json
import os
from pathlib import Path
import subprocess
import sys

from coverage import SUITES
from check_inputs import check_inputs

root = Path(__file__).resolve().parents[2]
entry = json.loads(os.environ["TEST_SELECTION"])
suite = entry["suite"]
if suite not in SUITES:
    raise ValueError("Unknown verification suite")
wrapper = str(root / ("gradlew.bat" if os.name == "nt" else "gradlew"))
if suite.startswith("coil-"):
    command = [wrapper, "-p", "samples/" + suite, "runRelease", "--console=plain"]
    subprocess.run(command, cwd=root, check=True)
    check_inputs(suite, "all", root)
    if suite == "coil-desktop":
        log = root / "samples/coil-desktop/build/verification/coil-negative.log"
        log.parent.mkdir(parents=True, exist_ok=True)
        with log.open("w", encoding="utf-8") as stream:
            result = subprocess.run(command + ["-PkeepKitNegative=true"], cwd=root,
                                    stdout=stream, stderr=subprocess.STDOUT)
        text = log.read_text(encoding="utf-8", errors="replace")
        if result.returncode == 0 or "> Task :runRelease FAILED" not in text or "Unable to create a fetcher" not in text:
            raise RuntimeError("Coil negative control did not fail at the expected runtime boundary")
else:
    command = [sys.executable, str(root / "samples" / suite / "verify.py")]
    if suite == "rule-regressions":
        command += ["--family", entry["family"]]
    elif entry["cases"]:
        command += ["--only", *entry["cases"]]
    subprocess.run(command, cwd=root, check=True)
