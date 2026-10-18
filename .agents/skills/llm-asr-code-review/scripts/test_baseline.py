#!/usr/bin/env python3
"""Synthetic schema/diff fixtures; never import or run product code."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import baseline as b


class BaselineTests(unittest.TestCase):
    def raw(self, tool, names=("run",)):
        if tool == "cccc":
            return {"files": [{"path": "tests/F.java", "functions": [
                {"name": n, "line": i + 1, "cognitive": 2, "cyclomatic": 3}
                for i, n in enumerate(names)]}], "summary": {}}
        return [{"file": "tests/F.java", "function": n, "start_line": i + 1,
                 "cognitive": 2, "mccabe": 3, "nesting": 1, "sloc": 4, "aird": 5, "aicp": 6}
                for i, n in enumerate(names)]

    def fixture(self, path, names=("run",)):
        path.mkdir()
        b.dump(path / "sources.json", {"policy": "maintained-tracked-v1", "files": [
            {"path": "tests/F.java", "group": "test", "tools": list(b.VERSIONS), "sha256": "fixture"}]})
        executions = {}
        for tool in b.VERSIONS:
            b.dump(path / (tool + ".json"), self.raw(tool, names))
            (path / (tool + ".stderr.txt")).write_text("")
            executions[tool] = {"version": b.VERSIONS[tool], "binary_sha256": "fixture", "exit": 0,
                "argv": [tool, *b.FLAGS[tool], "tests/F.java"],
                "stdout_sha256": b.digest(path / (tool + ".json")),
                "stderr_sha256": b.digest(path / (tool + ".stderr.txt"))}
        b.dump(path / "execution.json", executions)

    def change_raw(self, path, tool, update):
        raw = json.loads((path / (tool + ".json")).read_text())
        update(raw)
        b.dump(path / (tool + ".json"), raw)
        e = json.loads((path / "execution.json").read_text())
        e[tool]["stdout_sha256"] = b.digest(path / (tool + ".json"))
        b.dump(path / "execution.json", e)

    def pair(self, names=("run",)):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        a, c = Path(temp.name) / "before", Path(temp.name) / "after"
        self.fixture(a, names)
        self.fixture(c, names)
        return a, c

    def test_positive_and_line_shift(self):
        a, c = self.pair()
        self.change_raw(c, "knots", lambda raw: raw[0].update(start_line=20))
        self.assertEqual(b.compare(a, c)["changes"], [])

    def test_growth(self):
        a, c = self.pair()
        self.change_raw(c, "knots", lambda raw: raw[0].update(cognitive=12))
        x = b.compare(a, c)["changes"]
        self.assertEqual(len(x), 1)
        self.assertEqual(x[0]["kind"], "metric-change")
        self.assertEqual(x[0]["after"][0]["cognitive"], 12)

    def test_same_name_never_overwrites(self):
        a, c = self.pair(("run", "run"))
        x = b.compare(a, c)["changes"]
        self.assertEqual([r["kind"] for r in x], ["ambiguous-manual-map"] * 2)
        self.assertTrue(all(len(r["before"]) == 2 for r in x))

    def test_rename_split_and_delete(self):
        a, c = self.pair()
        for tool in b.VERSIONS:
            b.dump(c / (tool + ".json"), self.raw(tool, ("partA", "partB")))
        e = json.loads((c / "execution.json").read_text())
        for t in e:
            e[t]["stdout_sha256"] = b.digest(c / (t + ".json"))
        b.dump(c / "execution.json", e)
        kinds = [r["kind"] for r in b.compare(a, c)["changes"]]
        self.assertEqual(kinds.count("added-or-renamed"), 4)
        self.assertEqual(kinds.count("removed-or-renamed"), 2)

    def test_scope_and_tool_identity_failure(self):
        a, c = self.pair()
        m = json.loads((c / "sources.json").read_text())
        m["files"][0]["group"] = "production"
        b.dump(c / "sources.json", m)
        with self.assertRaisesRegex(ValueError, "scope changed"):
            b.compare(a, c)
        m["files"][0]["group"] = "test"
        b.dump(c / "sources.json", m)
        e = json.loads((c / "execution.json").read_text())
        e["knots"]["binary_sha256"] = "other"
        b.dump(c / "execution.json", e)
        with self.assertRaisesRegex(ValueError, "identity"):
            b.compare(a, c)

    def test_options_and_command_scope_failure(self):
        a, c = self.pair()
        e = json.loads((c / "execution.json").read_text())
        e["knots"]["argv"][-1] = "other.java"
        b.dump(c / "execution.json", e)
        with self.assertRaisesRegex(ValueError, "command/scope"):
            b.compare(a, c)

    def test_stderr_is_unknown(self):
        a, c = self.pair()
        (c / "knots.stderr.txt").write_text("parser diagnostic\n")
        e = json.loads((c / "execution.json").read_text())
        e["knots"]["stderr_sha256"] = b.digest(c / "knots.stderr.txt")
        b.dump(c / "execution.json", e)
        with self.assertRaisesRegex(ValueError, "diagnostics"):
            b.compare(a, c)

    def test_raw_drift_and_nonzero(self):
        a, c = self.pair()
        (c / "cccc.json").write_text("{}")
        with self.assertRaisesRegex(ValueError, "drift"):
            b.compare(a, c)
        e = json.loads((c / "execution.json").read_text())
        e["cccc"]["exit"] = 1
        b.dump(c / "execution.json", e)
        with self.assertRaisesRegex(ValueError, "failed"):
            b.compare(a, c)

    def test_zero_records_not_zero_complexity(self):
        records, coverage = b.normalize("knots", [], ["tests/F.java"])
        self.assertEqual(records, [])
        self.assertEqual(coverage[0]["status"], "zero-records-unknown")
        with self.assertRaisesRegex(ValueError, "missing file"):
            b.normalize("cccc", {"files": [], "summary": {}}, ["tests/F.java"])

    def test_parse_errors_and_nested_functions(self):
        raw = self.raw("cccc")
        raw["files"][0]["parse_errors"] = ["syntax error"]
        raw["files"][0]["functions"][0]["children"] = [
            {"name": "run", "line": 5, "cognitive": 1, "cyclomatic": 1}]
        records, cov = b.normalize("cccc", raw, ["tests/F.java"])
        self.assertEqual([r["name"] for r in records], ["run", "run/run"])
        self.assertEqual(cov[0]["status"], "partial-parse")

    def test_schema_path_metric_duplicate_failures(self):
        for t in b.VERSIONS:
            for bad in (None, "", {} if t == "knots" else []):
                with self.assertRaises(ValueError):
                    b.normalize(t, bad, ["tests/F.java"])
            with self.assertRaises(ValueError):
                b.normalize(t, self.raw(t), ["other.java"])
        raw = self.raw("knots")
        raw[0]["mccabe"] = -1
        with self.assertRaises(ValueError):
            b.normalize("knots", raw, ["tests/F.java"])
        for invalid in (float("nan"), float("inf"), True):
            raw[0]["mccabe"] = invalid
            with self.assertRaises(ValueError):
                b.normalize("knots", raw, ["tests/F.java"])
        raw = self.raw("knots")
        raw.append(copy.deepcopy(raw[0]))
        records, _ = b.normalize("knots", raw, ["tests/F.java"])
        self.assertEqual([r["occurrence"] for r in records], [1, 2])
        self.assertEqual(len(records), 2)

    def test_inventory_tracked_only_groups_and_exclusions(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            paths = ["android/app/src/F.java", "android/test-host/src/main/H.java",
                     "core/src/test/T.java", "native/apk/asr_jni.cpp", "native/p0/H.cpp",
                     "scripts/tool.py", "model-tools/tool.py", "tests/T.py", "core/build.gradle.kts",
                     "flake.nix", "gradlew", "reports/old.py", "native/vendor/V.cpp",
                     "android/app/build/G.java", "docs/example.py"]
            for p in paths + ["scripts/untracked.py"]:
                full = root / p
                full.parent.mkdir(parents=True, exist_ok=True)
                full.write_text("// fixture\n")
            subprocess.run(["git", "add", "--", *paths], cwd=root, check=True)
            got = {r["path"]: r for r in b.inventory(root)}
            self.assertEqual(len(got), 11)
            self.assertEqual(got[paths[0]]["group"], "production")
            self.assertEqual(got[paths[1]]["group"], "test")
            self.assertEqual(got["core/build.gradle.kts"]["tools"], ["knots"])
            self.assertEqual(got["flake.nix"]["tools"], [])

    def test_cli_empty_output_and_malformed_json_fail_unknown(self):
        a, c = self.pair()
        for content in ("", "not-json"):
            (c / "cccc.json").write_text(content)
            e = json.loads((c / "execution.json").read_text())
            e["cccc"]["stdout_sha256"] = b.digest(c / "cccc.json")
            b.dump(c / "execution.json", e)
            r = subprocess.run([sys.executable, str(Path(b.__file__)), "compare", str(a), str(c)], capture_output=True, text=True)
            self.assertEqual(r.returncode, 2)
            self.assertIn("FAIL-UNKNOWN", r.stderr)
            self.assertEqual(r.stdout, "")


if __name__ == "__main__":
    unittest.main(verbosity=2)
