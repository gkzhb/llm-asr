"""Frozen pre-R3 report-body contracts; source preservation, NOT JSON runtime tests.
Production AppRequestPolicy is exercised on host with injected Reports. Android
JSONObject and real file writes still need Android/device runtime validation.
"""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[1]
# R6: AppReportWriter moved to the transcription subpackage.
JAVA = ROOT / 'android/app/src/org/llmasr/minimal/transcription/AppReportWriter.java'
IGNORED = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/', re.S)

def body(source, marker):
    start = source.index('{', source.index(marker))
    masked = IGNORED.sub(lambda m: ' ' * len(m.group()), source)
    depth = 0
    for at in range(start, len(source)):
        if masked[at] == '{': depth += 1
        elif masked[at] == '}':
            depth -= 1
            if depth == 0: return source[start:at + 1]
    raise AssertionError('unclosed method: ' + marker)

def digest(text):
    return hashlib.sha256(text.encode()).hexdigest()

def check(source):
    expected = json.loads((ROOT / 'tests/fixtures/app-report-pre-r3.json').read_text())
    for marker, sha in expected.items():
        assert digest(body(source, marker)) == sha, 'pre-R3 report body changed: ' + marker

if __name__ == '__main__':
    report = JAVA.read_text()
    check(report)
    assert 'implements AppRequestPolicy.Reports' in report
    for method, call in [('writePending', 'pendingReport()'), ('writeTerminal', 'modelOperationReport()'),
                         ('writeFailure', 'failureReport(cause)'), ('writeCancel', 'cancelReport()')]:
        assert body(report, 'public void ' + method + '(') == '{ saveReport(ctx, ' + call + '); }'
    for old, new in [('1024 * 1024', '2 * 1024 * 1024'), ('report.toString(2)', 'report.toString()'),
                     ('System.currentTimeMillis()', '0'), ('String.valueOf(cause)', 'cause.getMessage()'),
                     ('"truncated", false', '"truncated", true'), ('"Cannot save result"', '"different error"'),
                     ('"last-result-"', '"different-"'), ('r.inferenceSeconds / seconds', '0')]:
        assert old in report
        try: check(report.replace(old, new))
        except AssertionError: pass
        else: raise AssertionError('report source guard accepted mutation: ' + old)
    cpp = (ROOT / 'native/apk/asr_jni.cpp').read_text()
    baseline = (ROOT / 'tests/fixtures/jni-pre-r3.sha256').read_text().strip()
    # R6: JniNativeTranscription moved to org.llmasr.minimal.asr. The C++ symbol
    # prefix changed with it; allow only that mechanical rename when comparing
    # the C++ body to the pre-R3 baseline. Both directions of the rename
    # (legacy and R6) are mapped to the same canonical prefix, so the diff
    # is a single-line signature check against the JNI class binding.
    canonical = cpp
    for src, dst in (('Java_org_llmasr_minimal_asr_JniNativeTranscription_',
                      'Java_org_llmasr_minimal_JniNativeTranscription_'),
                     ('Java_org_llmasr_minimal_JniNativeTranscription_',
                      'Java_org_llmasr_minimal_MainActivity_')):
        canonical = canonical.replace(src, dst)
    assert digest(canonical) == baseline, 'native JNI body changed beyond package prefix'
    print('PASS frozen pre-R3 report bodies + 8 negative fixtures; native source identical except JNI class symbol prefix (not runtime)')
