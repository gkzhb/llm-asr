#!/usr/bin/env python3
"""Project-only complexity evidence collector/comparator (stdlib; no gates)."""
import argparse
import collections
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys

VERSIONS = {"cccc": "cccc 1.8.3", "knots": "knots 1.18.0"}
FLAGS = {"cccc": ["--no-config", "--no-cache", "--jobs", "2"],
         "knots": ["--format", "json", "--jobs", "2"]}
EXTENSIONS = {".java", ".kt", ".kts", ".cpp", ".py", ".sh", ".nix", ".cmake", ".gradle"}
SUPPORTED = {"cccc": {".java", ".cpp", ".py"},
             "knots": {".java", ".cpp", ".py", ".kt", ".kts"}}


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def dump(path, data):
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def inventory(root):
    paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")[:-1]
    rows = []
    for p in sorted(paths):
        path = Path(p)
        if not (p.startswith(("android/", "core/", "native/", "scripts/", "tests/", "model-tools/"))
                or "/" not in p):
            continue
        if path.suffix not in EXTENSIONS and path.name not in {"gradlew", "gradlew.bat", "CMakeLists.txt"}:
            continue
        if any(x in path.parts for x in ("vendor", "build", "generated", "__pycache__")):
            continue
        if p.startswith("tests/") or p.startswith("android/test-host/") or "/src/test/" in p or "/src/androidTest/" in p:
            group = "test"
        elif p.startswith("android/app/src/") or p == "native/apk/asr_jni.cpp":
            group = "production"
        else:
            group = "tooling"
        full = root / p
        if full.is_symlink() or not full.is_file():
            raise ValueError("non-regular input: " + p)
        rows.append({"path": p, "group": group, "sha256": digest(full),
                     "bytes": full.stat().st_size,
                     "tools": [t for t in VERSIONS if path.suffix in SUPPORTED[t]]})
    if not rows:
        raise ValueError("empty source inventory")
    return rows


def normalize(tool, raw, allowed):
    """Keep metrics separate; preserve collisions for manual source mapping."""
    records, errors = [], {}
    def add(p, name, line, metrics):
        if p not in allowed or not isinstance(name, str) or not name or type(line) is not int or line < 1:
            raise ValueError("unknown path/name/location")
        if any(type(v) not in (int, float) or not math.isfinite(v) or v < 0 for v in metrics.values()):
            raise ValueError("invalid metric")
        records.append(dict(path=p, name=name, line=line, **metrics))
    if tool == "cccc":
        if not isinstance(raw, dict) or not isinstance(raw.get("files"), list) or not isinstance(raw.get("summary"), dict):
            raise ValueError("cccc schema")
        seen = set()
        def walk(p, functions, parent=""):
            for f in functions:
                name = parent + f["name"]
                add(p, name, f["line"], {k: f[k] for k in ("cognitive", "cyclomatic")})
                walk(p, f.get("children", []), name + "/")
        for f in raw["files"]:
            p = f["path"]
            if p in seen or p not in allowed:
                raise ValueError("duplicate/unexpected cccc file")
            seen.add(p)
            errors[p] = f.get("parse_errors", [])
            walk(p, f["functions"])
        if seen != set(allowed):
            raise ValueError("cccc missing file (unsupported or silent skip)")
    else:
        if not isinstance(raw, list):
            raise ValueError("knots schema")
        for f in raw:
            add(f["file"], f["function"], f["start_line"],
                {k: f[k] for k in ("cognitive", "mccabe", "nesting", "sloc", "aird", "aicp")})
    records.sort(key=lambda r: (r["path"], r["name"], r["line"]))
    # Frontends can emit several same-line lambdas without column/signature.
    # Preserve all raw entries; occurrence is only a within-snapshot locator.
    occurrences = collections.Counter()
    for r in records:
        key = (r["path"], r["name"], r["line"])
        occurrences[key] += 1
        r["occurrence"] = occurrences[key]
    counts = collections.Counter(r["path"] for r in records)
    coverage = [{"path": p, "records": counts[p], "parse_errors": errors.get(p, []),
                 "status": "partial-parse" if errors.get(p) else ("records" if counts[p] else "zero-records-unknown")}
                for p in sorted(allowed)]
    return records, coverage


def collect(root, out):
    out.mkdir(parents=True, exist_ok=False)
    rows = inventory(root)
    manifest = {"schema": 1, "policy": "maintained-tracked-v1", "base": subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=root).decode().strip(), "files": rows}
    dump(out / "sources.json", manifest)
    executions, result = {}, {}
    for tool, expected in VERSIONS.items():
        binary = shutil.which(tool)
        if not binary:
            raise ValueError("missing tool: " + tool)
        version = subprocess.run([binary, "--version"], capture_output=True, text=True, check=True).stdout.strip()
        if version != expected:
            raise ValueError("version mismatch: " + version)
        allowed = [r["path"] for r in rows if tool in r["tools"]]
        cmd = [binary, *FLAGS[tool], *allowed]
        run = subprocess.run(cmd, cwd=root, capture_output=True, timeout=300)
        (out / (tool + ".json")).write_bytes(run.stdout)
        (out / (tool + ".stderr.txt")).write_bytes(run.stderr)
        executions[tool] = {"version": version, "binary": binary, "resolved_binary": str(Path(binary).resolve()),
                            "binary_sha256": digest(binary), "argv": cmd, "exit": run.returncode,
                            "stdout_sha256": digest(out / (tool + ".json")),
                            "stderr_sha256": digest(out / (tool + ".stderr.txt"))}
        dump(out / "execution.json", executions)
        if run.returncode or run.stderr:
            raise ValueError(tool + " failed or emitted diagnostics; inspect raw evidence")
        records, coverage = normalize(tool, json.loads(run.stdout), allowed)
        result[tool] = {"records": records, "coverage": coverage}
    if rows != inventory(root):
        raise ValueError("source drift during analysis")
    dump(out / "normalized.json", result)
    print(json.dumps({t: {"functions": len(x["records"]), "files": len(x["coverage"])} for t, x in result.items()}))


def compare(before, after):
    a, b = [json.loads((p / "sources.json").read_text()) for p in (before, after)]
    ae, be = [json.loads((p / "execution.json").read_text()) for p in (before, after)]
    if a["policy"] != b["policy"]:
        raise ValueError("scope policy changed")
    for t in VERSIONS:
        for manifest, execution in ((a, ae), (b, be)):
            expected_paths = [r["path"] for r in manifest["files"] if t in r["tools"]]
            if execution[t]["argv"][1:] != FLAGS[t] + expected_paths:
                raise ValueError("command/scope mismatch")
        if any(ae[t][k] != be[t][k] for k in ("version", "binary_sha256")):
            raise ValueError("tool identity changed")
        if ae[t]["exit"] or be[t]["exit"]:
            raise ValueError("failed tool")
    ap, bp = [{r["path"]: (r["group"], r["tools"]) for r in x["files"]} for x in (a, b)]
    if ap != bp:
        raise ValueError("scope changed: explicitly reconcile added/deleted/moved paths and groups before comparison")
    datasets = []
    for p, m, e in ((before, a, ae), (after, b, be)):
        data = {}
        for t in VERSIONS:
            if digest(p / (t + ".json")) != e[t]["stdout_sha256"] or digest(p / (t + ".stderr.txt")) != e[t]["stderr_sha256"]:
                raise ValueError("raw evidence drift")
            if (p / (t + ".stderr.txt")).stat().st_size:
                raise ValueError("tool diagnostics")
            data[t] = normalize(t, json.loads((p / (t + ".json")).read_text()),
                                [r["path"] for r in m["files"] if t in r["tools"]])[0]
        datasets.append(data)
    changes = []
    for t in VERSIONS:
        # Unique names only. Overloads/anonymous collisions always need manual source mapping.
        groups = []
        for d in datasets:
            g = collections.defaultdict(list)
            for r in d[t]:
                g[(r["path"], r["name"])].append(r)
            groups.append(g)
        old, new = groups
        for key in sorted(set(old) | set(new)):
            left, right = old.get(key, []), new.get(key, [])
            if len(left) > 1 or len(right) > 1:
                kind = "ambiguous-manual-map"
            elif not left:
                kind = "added-or-renamed"
            elif not right:
                kind = "removed-or-renamed"
            elif {k: v for k, v in left[0].items() if k != "line"} == {k: v for k, v in right[0].items() if k != "line"}:
                continue
            else:
                kind = "metric-change"
            changes.append({"tool": t, "path": key[0], "name": key[1], "kind": kind, "before": left, "after": right})
    return {"status": "review-required-not-a-gate", "changes": changes,
            "coverage_before_after": [{t: normalize(t, json.loads((p / (t + ".json")).read_text()),
                [r["path"] for r in m["files"] if t in r["tools"]])[1] for t in VERSIONS}
                for p, m in ((before, a), (after, b))]}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="action", required=True)
    c = sub.add_parser("collect")
    c.add_argument("--root", type=Path, default=Path.cwd())
    c.add_argument("--out", type=Path, required=True)
    c = sub.add_parser("compare")
    c.add_argument("before", type=Path)
    c.add_argument("after", type=Path)
    args = p.parse_args()
    try:
        if args.action == "collect":
            collect(args.root.resolve(), args.out)
        else:
            print(json.dumps(compare(args.before, args.after), ensure_ascii=False, indent=2))
    except (ValueError, KeyError, TypeError, AttributeError, OSError, subprocess.SubprocessError) as e:
        print("FAIL-UNKNOWN: " + str(e), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
