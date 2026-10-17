#!/usr/bin/env python3
"""Choose runtime tests from changes since a successful ancestor CI run.

No successful baseline, API errors, unknown inputs or manual dispatch => full run.
Only immutable/pinned dependency inputs may reuse earlier verification.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.parse
import urllib.request

from coverage import (COIL, FLOATCLOCK, FLOATCLOCK_CASES, INTEGRATIONS, INTEGRATION_CASES,
                      KNOWN_RULES, KZAGENT, KZAGENT_CASES, OS, REGRESSIONS, SUITES)

ROOT = Path(__file__).resolve().parents[2]
RULE_DIR = "src/main/resources/keepkit/"


def git(*args, root=ROOT):
    return subprocess.check_output(["git", *args], cwd=root)


def ancestor(base, head, root=ROOT):
    if not re.fullmatch(r"[0-9a-f]{40,64}", base):
        return False
    return subprocess.run(["git", "merge-base", "--is-ancestor", base, head], cwd=root,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def successful_baseline(event, head, env=os.environ, fetch=None, root=ROOT):
    """Never trust event.before: a failed/interrupted prior run is not evidence."""
    branch = event.get("pull_request", {}).get("base", {}).get("ref") or env.get("GITHUB_REF_NAME")
    if not branch:
        return None
    repository = env["GITHUB_REPOSITORY"]
    api = env.get("GITHUB_API_URL", "https://api.github.com")

    def request(url):
        headers = {"Accept": "application/vnd.github+json", "User-Agent": "KeepKit-CI"}
        if env.get("GITHUB_TOKEN"):
            headers["Authorization"] = "Bearer " + env["GITHUB_TOKEN"]
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as response:
            return json.load(response)

    fetch = fetch or request
    for page in range(1, 6):
        query = urllib.parse.urlencode({"branch": branch, "status": "success", "per_page": 100, "page": page})
        data = fetch(f"{api}/repos/{repository}/actions/workflows/ci.yml/runs?{query}")
        runs = data["workflow_runs"]
        for run in runs:
            if (str(run["id"]) != env.get("GITHUB_RUN_ID") and run.get("conclusion") == "success"
                    and run.get("event") in ("push", "workflow_dispatch")
                    and run.get("head_branch") == branch
                    and ancestor(run["head_sha"], head, root)):
                return run["head_sha"]
        if len(runs) < 100:
            break
    return None


def all_tests(reason):
    return {"core": True, "tests": [{"suite": suite, "cases": [], "family": "all"} for suite in SUITES],
            "reasons": [reason]}


def catalog_rules(before, after):
    """Compare coordinates/versions too, including removed and renamed fragments."""
    if {k: v for k, v in before.items() if k != "dependencies"} != {
            k: v for k, v in after.items() if k != "dependencies"}:
        raise ValueError("Catalog schema changed")
    old = {entry["dependency"]: entry for entry in before["dependencies"]}
    new = {entry["dependency"]: entry for entry in after["dependencies"]}
    if new.keys() - old.keys():
        # A previously unsupported transitive coordinate can appear in fixtures
        # whose old reports had no adapter for it. Bootstrap its inventory fully.
        raise ValueError("New catalog coordinates require a full coverage baseline")
    rules = set()
    for dependency in old.keys() | new.keys():
        if old.get(dependency) != new.get(dependency):
            for entry in (old.get(dependency), new.get(dependency)):
                if entry:
                    for version in entry["versions"]:
                        rules.update(version["rules"])
    return rules


def select(paths, changed_catalog_rules=(), force=False):
    if force:
        return all_tests("Manual full run or no trusted successful baseline")
    core = False
    rules = set(changed_catalog_rules)
    whole = set()
    families = set()
    selected = {suite: set() for suite in SUITES}
    reasons = []
    for path in paths:
        # Only explicitly harmless documents/assets bypass runtime verification.
        if path.startswith("docs/") or path.endswith(".md") or path in ("LICENSE", ".gitignore"):
            continue
        reasons.append(path)
        if path == RULE_DIR + "catalog.json":
            core = True
        elif path.startswith(RULE_DIR) and path.endswith(".pro"):
            rules.add(path.removeprefix(RULE_DIR))
            core = True
        elif path.startswith("src/test/"):
            core = True
        elif path.startswith("samples/"):
            parts = path.split("/")
            suite = parts[1]
            if suite not in SUITES:
                # Remote-consumer/release fixtures are not silently ignored.
                return all_tests("Unclassified sample input: " + path)
            if suite == "rule-regressions" and len(parts) > 3 and parts[2] == "src" and parts[3] in REGRESSIONS:
                families.add(parts[3])
            elif suite in ("floatclock-dependencies", "kzagent-dependencies") and len(parts) > 3 and parts[2] == "src":
                groups = FLOATCLOCK_CASES if suite == "floatclock-dependencies" else KZAGENT_CASES
                if parts[3] in groups:
                    selected[suite].update(groups[parts[3]])
                else:
                    whole.add(suite)
            else:
                whole.add(suite)
        else:
            return all_tests("Shared or unclassified input changed: " + path)

    if not rules <= KNOWN_RULES:
        return all_tests("Unclassified rule inputs: " + ", ".join(sorted(rules - KNOWN_RULES)))
    for groups, cases, suite in ((INTEGRATIONS, INTEGRATION_CASES, "integrations"),
                                  (FLOATCLOCK, FLOATCLOCK_CASES, "floatclock-dependencies"),
                                  (KZAGENT, KZAGENT_CASES, "kzagent-dependencies")):
        for name, inputs in groups.items():
            if rules & inputs:
                selected[suite].update(cases[name])
    if selected["integrations"]:
        selected["integrations"].add("restored-positive")
    families.update(family for family, inputs in REGRESSIONS.items() if rules & inputs)
    if rules & COIL:
        whole.update(("coil-desktop", "coil-kmp"))

    tests = []
    for suite in SUITES:
        if suite in whole:
            tests.append({"suite": suite, "cases": [], "family": "all"})
        elif suite == "rule-regressions":
            # Separate families have independent build directories and diagnostics.
            tests.extend({"suite": suite, "cases": [], "family": family} for family in sorted(families))
        elif selected[suite]:
            tests.append({"suite": suite, "cases": sorted(selected[suite]), "family": "all"})
    return {"core": core, "tests": tests, "reasons": reasons or ["Only documentation or no changed inputs"]}


def changed_paths(base, head, root=ROOT):
    # --no-renames includes both sides, so moving a file cannot hide a removed input.
    return [p for p in git("diff", "--name-only", "--no-renames", "-z", base, head, "--", root=root)
            .decode().split("\0") if p]


def mutable_dependencies(root=ROOT):
    paths = [*root.glob("**/*.gradle.kts"), *root.glob("**/*.gradle"),
             *root.glob("**/gradle.properties"), *root.glob("gradle/*.toml"),
             root / RULE_DIR / "catalog.json"]
    return any(re.search(r"-SNAPSHOT\b|latest\.(?:release|integration)|:[^\s\"']*\+|"
                         r"[\"'][^\"'\n]*\+[\"']|[:\"'][\[(]\d[^\s\"']+|isChanging\s*=\s*true", p.read_text(), re.I)
               for p in paths if p.is_file() and "build" not in p.relative_to(root).parts
               and ".gradle" not in p.relative_to(root).parts)


def make_plan(event, event_name, head, base=None, env=os.environ, fetch=None, root=ROOT):
    if event_name == "workflow_dispatch" or mutable_dependencies(root):
        return all_tests("Manual full run or mutable dependency versions"), None
    try:
        base = base or successful_baseline(event, head, env, fetch, root)
        if not base or not ancestor(base, head, root):
            return all_tests("No trusted successful ancestor CI run"), None
        paths = changed_paths(base, head, root)
        rules = set()
        if RULE_DIR + "catalog.json" in paths:
            rules = catalog_rules(json.loads(git("show", f"{base}:{RULE_DIR}catalog.json", root=root)),
                                  json.loads(git("show", f"{head}:{RULE_DIR}catalog.json", root=root)))
        return select(paths, rules), base
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        # API/checkout/catalog trouble must increase coverage, never silently skip it.
        return all_tests("Cannot establish safe change scope: " + type(error).__name__), None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Explicit successful baseline for local inspection")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--full", action="store_true")
    args = parser.parse_args()
    head = git("rev-parse", args.head).decode().strip()
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text()) if os.getenv("GITHUB_EVENT_PATH") else {}
    plan, base = make_plan(event, "workflow_dispatch" if args.full else os.getenv("GITHUB_EVENT_NAME", "push"),
                           head, args.base)
    matrix = {"include": [dict(test, os=platform,
                               label=test["suite"] + ("-" + test["family"] if test["family"] != "all" else ""))
                           for test in plan["tests"] for platform in OS]}
    plan.update(base=base, head=head, matrix=matrix)
    output = ROOT / "build/ci-plan.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(plan, indent=2) + "\n")
    print(json.dumps(plan, indent=2))
    if os.getenv("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            stream.write(f"core={str(plan['core']).lower()}\nhas-tests={str(bool(plan['tests'])).lower()}\n")
            stream.write("matrix=" + json.dumps(matrix, separators=(",", ":")) + "\n")
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
            stream.write(f"### Selected CI verification\n\nBaseline: `{base or 'full run'}`\n\n")
            stream.write(f"Core tests: {plan['core']}\n\n")
            for test in plan["tests"]:
                stream.write(f"- {test['suite']} / {test['family']}: " + (", ".join(test["cases"]) or "all cases") + "\n")
            stream.write("\nReasons:\n\n" + "\n".join("- " + path for path in plan["reasons"]) + "\n")


if __name__ == "__main__":
    main()
