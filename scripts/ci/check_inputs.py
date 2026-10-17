"""Reject a selector inventory that omits an actually resolved transitive rule."""
import json
from pathlib import Path

from coverage import COIL, FLOATCLOCK, INTEGRATIONS, KZAGENT, REGRESSIONS


def check_inputs(suite, scenario, root):
    report = Path(root) / "samples" / suite / "build/verification/ci-inputs/rule-inputs.json"
    data = json.loads(report.read_text(encoding="utf-8"))
    if data["schemaVersion"] != 1 or not data["applications"]:
        raise ValueError("Missing or unsupported CI rule-input report")
    actual = {rule for entry in data["applications"] for rule in entry["rules"]}
    if suite == "integrations":
        expected = set.union(*INTEGRATIONS.values()) if scenario == "all" else INTEGRATIONS[scenario]
    elif suite == "floatclock-dependencies":
        expected = FLOATCLOCK[scenario]
    elif suite == "kzagent-dependencies":
        expected = KZAGENT[scenario]
    elif suite == "rule-regressions":
        expected = REGRESSIONS[scenario]
    else:
        expected = COIL
    missing = actual - expected
    if missing:
        raise ValueError(f"CI rule-input inventory omits {suite}/{scenario}: {sorted(missing)}")
    return actual
