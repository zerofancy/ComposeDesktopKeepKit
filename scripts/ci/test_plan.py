import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

from coverage import FLOATCLOCK_CASES, INTEGRATION_CASES, KNOWN_RULES, KZAGENT_CASES, SUITES
from plan import (ROOT, RULE_DIR, catalog_rules, changed_paths, make_plan, select,
                  successful_baseline)
from check_inputs import check_inputs


def tests_by_suite(plan):
    return {test["suite"]: test for test in plan["tests"]}


class SelectionTests(unittest.TestCase):
    def test_documentation_only_does_not_schedule_builds(self):
        result = select(["README.md", "docs/report.html", "samples/integrations/README.md"])
        self.assertFalse(result["core"])
        self.assertEqual([], result["tests"])

    def test_jna_keeps_platform_transitive_and_oshi_coverage(self):
        result = select([RULE_DIR + "jna.pro"])
        tests = tests_by_suite(result)
        self.assertTrue(result["core"])
        expected = {"filekit-positive", "restored-positive"}
        for scenario in ("jna", "jna-platform", "jna-jpms", "jna-platform-jpms"):
            expected.update(INTEGRATION_CASES[scenario])
        self.assertEqual(expected, set(tests["integrations"]["cases"]))
        self.assertEqual({"oshi-positive", "oshi-negative"}, set(tests["floatclock-dependencies"]["cases"]))
        self.assertEqual("jna", tests["rule-regressions"]["family"])
        self.assertNotIn("coil-desktop", tests)
        self.assertNotIn("kzagent-dependencies", tests)

    def test_coil_rule_does_not_run_other_libraries(self):
        result = select([RULE_DIR + "coil3.pro"])
        self.assertEqual({"coil-desktop", "coil-kmp"}, set(tests_by_suite(result)))

    def test_pinned_okhttp5_does_not_retest_okhttp4_consumers(self):
        tests = tests_by_suite(select([RULE_DIR + "okhttp5.pro"]))
        self.assertEqual({"integrations"}, set(tests))
        self.assertEqual({"okhttp-positive", "okhttp-negative", "restored-positive"}, set(tests["integrations"]["cases"]))

    def test_swing_rule_does_not_retest_fixtures_using_only_core_coroutines(self):
        tests = tests_by_suite(select([RULE_DIR + "coroutines-swing.pro"]))
        self.assertEqual({"kzagent-dependencies"}, set(tests))
        self.assertEqual(set(KZAGENT_CASES["coroutines-swing"]), set(tests["kzagent-dependencies"]["cases"]))

    def test_jsoup_is_isolated(self):
        tests = tests_by_suite(select([RULE_DIR + "jsoup.pro"]))
        self.assertEqual({"kzagent-dependencies"}, set(tests))
        self.assertEqual(set(KZAGENT_CASES["jsoup"]), set(tests["kzagent-dependencies"]["cases"]))

    def test_datastore_rule_does_not_run_oshi_or_custom_serializer(self):
        tests = tests_by_suite(select([RULE_DIR + "datastore-preferences.pro"]))
        self.assertEqual({"floatclock-dependencies"}, set(tests))
        self.assertEqual({"datastore-positive", "datastore-negative"}, set(tests["floatclock-dependencies"]["cases"]))

    def test_shared_transitive_rules_select_all_consumers(self):
        tests = tests_by_suite(select([RULE_DIR + "coroutines.pro"]))
        self.assertTrue({"coil-desktop", "coil-kmp", "integrations", "floatclock-dependencies", "kzagent-dependencies"} <= tests.keys())
        self.assertNotIn("jsoup-positive", tests["kzagent-dependencies"]["cases"])
        slf4j = select([RULE_DIR + "slf4j2.pro"])
        self.assertEqual({"dbus", "logback"}, {t["family"] for t in slf4j["tests"] if t["suite"] == "rule-regressions"})

    def test_multiple_rules_union_without_duplicate_cases(self):
        result = select([RULE_DIR + "jna.pro", RULE_DIR + "jna-platform.pro", RULE_DIR + "dbus.pro"])
        integrations = tests_by_suite(result)["integrations"]["cases"]
        self.assertEqual(len(integrations), len(set(integrations)))
        self.assertEqual({"jna", "dbus"}, {t["family"] for t in result["tests"] if t["suite"] == "rule-regressions"})

    def test_fixture_configuration_change_rechecks_entire_fixture(self):
        result = select(["samples/integrations/build.gradle.kts"])
        self.assertFalse(result["core"])
        self.assertEqual([{"suite": "integrations", "cases": [], "family": "all"}], result["tests"])

    def test_named_fixture_source_selects_only_related_cases(self):
        tests = tests_by_suite(select(["samples/floatclock-dependencies/src/oshi/Main.kt"]))
        self.assertEqual(set(FLOATCLOCK_CASES["oshi"]), set(tests["floatclock-dependencies"]["cases"]))
        tests = tests_by_suite(select(["samples/rule-regressions/src/dbus/audit/DbusRegression.java"]))
        self.assertEqual("dbus", tests["rule-regressions"]["family"])

    def test_plugin_or_shared_configuration_change_selects_everything(self):
        for path in ("src/main/kotlin/Plugin.kt", "build.gradle.kts", "gradle/libs.versions.toml",
                     "gradle/wrapper/gradle-wrapper.properties", "gradle.properties", ".github/workflows/ci.yml",
                     "scripts/ci/plan.py", "samples/shared.gradle.kts", "unknown.config"):
            with self.subTest(path=path):
                result = select([path])
                self.assertTrue(result["core"])
                self.assertEqual(set(SUITES), set(tests_by_suite(result)))

    def test_unknown_rule_falls_back_instead_of_skipping(self):
        result = select([RULE_DIR + "new-library.pro"])
        self.assertEqual(set(SUITES), set(tests_by_suite(result)))

    def test_unit_test_change_only_needs_core(self):
        result = select(["src/test/kotlin/RuleCatalogTest.kt"])
        self.assertTrue(result["core"])
        self.assertEqual([], result["tests"])

    def test_catalog_inventory_and_case_names_match_real_verifiers(self):
        catalog = json.loads((ROOT / RULE_DIR / "catalog.json").read_text())
        rules = {rule for entry in catalog["dependencies"] for v in entry["versions"] for rule in v["rules"]}
        self.assertLessEqual(rules, KNOWN_RULES)
        for suite, groups, extra in (("integrations", INTEGRATION_CASES, {"restored-positive"}),
                                      ("floatclock-dependencies", FLOATCLOCK_CASES, set()),
                                      ("kzagent-dependencies", KZAGENT_CASES, set())):
            actual = json.loads(subprocess.check_output([sys.executable, str(ROOT / "samples" / suite / "verify.py"), "--list-cases"]))
            expected = set.union(extra, *(set(cases) for cases in groups.values()))
            self.assertEqual(expected, set(actual), suite)

    def test_catalog_version_mapping_and_deleted_rules_are_inputs(self):
        def catalog(version, rule):
            return {"schemaVersion": 1, "dependencies": [{"dependency": "group:module", "versions": [{"version": version, "rules": [rule]}]}]}
        self.assertEqual({"jna.pro"}, catalog_rules(catalog("1", "jna.pro"), catalog("2", "jna.pro")))
        self.assertEqual({"jna.pro", "dbus.pro"}, catalog_rules(catalog("1", "jna.pro"), catalog("1", "dbus.pro")))
        self.assertEqual({"jna.pro"}, catalog_rules(catalog("1", "jna.pro"), {"schemaVersion": 1, "dependencies": []}))
        with self.assertRaises(ValueError):
            catalog_rules({"schemaVersion": 1}, {"schemaVersion": 2})
        with self.assertRaisesRegex(ValueError, "New catalog coordinates"):
            catalog_rules({"schemaVersion": 1, "dependencies": []}, catalog("1", "jna.pro"))

    def test_actual_report_rejects_missing_transitive_rule_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "samples/integrations/build/verification/ci-inputs/rule-inputs.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"schemaVersion": 1, "applications": [{"rules": ["jna.pro", "serialization.pro"]}]}))
            self.assertEqual({"jna.pro", "serialization.pro"}, check_inputs("integrations", "jna", root))
            path.write_text(json.dumps({"schemaVersion": 1, "applications": [{"rules": ["unclassified.pro"]}]}))
            with self.assertRaisesRegex(ValueError, "inventory omits"):
                check_inputs("integrations", "jna", root)
            path.write_text('{"schemaVersion":1,"applications":[]}')
            with self.assertRaises(ValueError):
                check_inputs("integrations", "jna", root)

    def test_invalid_case_selection_fails_before_overwriting_results(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copytree(ROOT / "scripts/ci", root / "scripts/ci", ignore=shutil.ignore_patterns("__pycache__"))
            for suite in ("integrations", "floatclock-dependencies", "kzagent-dependencies"):
                sample = root / "samples" / suite
                path = sample / "build/verification/results.json"
                path.parent.mkdir(parents=True)
                path.write_bytes(b"previous successful results\n")
                shutil.copyfile(ROOT / "samples" / suite / "verify.py", sample / "verify.py")
                result = subprocess.run([sys.executable, str(sample / "verify.py"), "--only", "not-a-case"], capture_output=True)
                self.assertEqual(2, result.returncode)
                self.assertIn(b"Unknown --only", result.stderr)
                self.assertEqual(b"previous successful results\n", path.read_bytes())


class BaselineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-b", "main")
        self.git("config", "user.name", "CI test")
        self.git("config", "user.email", "ci@example.invalid")
        (self.root / RULE_DIR).mkdir(parents=True)
        (self.root / RULE_DIR / "catalog.json").write_text('{"schemaVersion":1,"dependencies":[]}')
        self.first = self.commit("baseline")
        (self.root / RULE_DIR / "jna.pro").write_text("changed rule")
        self.failed = self.commit("failed rule run")
        (self.root / "README.md").write_text("documentation update")
        self.head = self.commit("docs after failure")
        self.env = {"GITHUB_REPOSITORY": "owner/repo", "GITHUB_REF_NAME": "main", "GITHUB_RUN_ID": "99"}

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, stderr=subprocess.DEVNULL).decode().strip()

    def commit(self, message):
        self.git("add", ".")
        self.git("commit", "-m", message)
        return self.git("rev-parse", "HEAD")

    def run_record(self, sha, **kwargs):
        return dict(id=1, head_sha=sha, conclusion="success", event="push", head_branch="main", **kwargs)

    def test_failed_previous_run_remains_in_scope_on_doc_push(self):
        good = self.run_record(self.first)
        failed = dict(good, id=2, head_sha=self.failed, conclusion="failure")
        plan, base = make_plan({"before": self.failed}, "push", self.head, env=self.env,
                               fetch=lambda _: {"workflow_runs": [failed, good]}, root=self.root)
        self.assertEqual(self.first, base)
        self.assertIn("integrations", tests_by_suite(plan))
        self.assertIn(RULE_DIR + "jna.pro", plan["reasons"])

    def test_api_error_missing_baseline_and_untrusted_base_run_full(self):
        for fetch in (lambda _: {"workflow_runs": []}, lambda _: (_ for _ in ()).throw(OSError("API unavailable"))):
            plan, base = make_plan({}, "push", self.head, env=self.env, fetch=fetch, root=self.root)
            self.assertIsNone(base)
            self.assertEqual(set(SUITES), set(tests_by_suite(plan)))
        plan, base = make_plan({}, "push", self.head, base="not-a-sha", root=self.root)
        self.assertIsNone(base)
        self.assertTrue(plan["core"])

    def test_rewritten_branch_and_current_run_cannot_be_baselines(self):
        self.git("checkout", "-b", "other", self.first)
        (self.root / "other.txt").write_text("other history")
        other = self.commit("not an ancestor")
        self.git("checkout", "main")
        current = dict(self.run_record(self.head), id=99)
        data = {"workflow_runs": [self.run_record(other), current, self.run_record(self.first)]}
        self.assertEqual(self.first, successful_baseline({}, self.head, self.env, lambda _: data, self.root))

    def test_pull_request_uses_successful_base_branch_history(self):
        event = {"pull_request": {"base": {"ref": "main"}}}
        env = dict(self.env, GITHUB_REF_NAME="17/merge")
        def fetch(url):
            self.assertIn("branch=main", url)
            return {"workflow_runs": [self.run_record(self.first)]}
        self.assertEqual(self.first, successful_baseline(event, self.head, env, fetch, self.root))

    def test_pagination(self):
        seen = []
        def fetch(url):
            seen.append(url)
            records = [dict(self.run_record(self.first), id=99)] * 100 if len(seen) == 1 else [self.run_record(self.first)]
            return {"workflow_runs": records}
        self.assertEqual(self.first, successful_baseline({}, self.head, self.env, fetch, self.root))
        self.assertIn("page=2", seen[-1])

    def test_snapshot_or_manual_full_run_never_reuses_baseline(self):
        for event in ("workflow_dispatch", "push"):
            (self.root / "build.gradle.kts").write_text('implementation("group:module:1.0-SNAPSHOT")')
            result, base = make_plan({}, event, self.head, base=self.first, root=self.root)
            self.assertIsNone(base)
            self.assertEqual(set(SUITES), set(tests_by_suite(result)))

    def test_dynamic_versions_in_catalog_or_settings_force_full_run(self):
        for filename, content in (("settings.gradle.kts", 'id("plugin") version "1.0-snapshot"'),
                                  ("gradle/libs.versions.toml", 'library = "1.+"'),
                                  ("build.gradle.kts", 'implementation("g:m:[1,2)")'),
                                  ("build.gradle.kts", 'isChanging = true')):
            with self.subTest(filename=filename, content=content):
                path = self.root / filename
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                result, base = make_plan({}, "push", self.head, base=self.first, root=self.root)
                self.assertIsNone(base)
                self.assertEqual(set(SUITES), set(tests_by_suite(result)))
                path.unlink()

    def test_rename_includes_old_and_new_paths(self):
        self.git("mv", RULE_DIR + "jna.pro", RULE_DIR + "renamed.pro")
        head = self.commit("rename fragment")
        self.assertEqual({RULE_DIR + "jna.pro", RULE_DIR + "renamed.pro"}, set(changed_paths(self.head, head, self.root)))


if __name__ == "__main__":
    unittest.main()
