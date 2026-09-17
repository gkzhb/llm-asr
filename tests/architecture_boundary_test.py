"""Lexical dependency guards (not a Java AST or Android runtime test)."""
from pathlib import Path
import re

# R6: every production class lives under a subpackage of org.llmasr.minimal.
# Resolve ROOT dynamically from the package map below.
ROOT_BASE = Path(__file__).resolve().parents[1] / 'android/app/src/org/llmasr/minimal'
# Consume literals and comments together, so // or /* inside a literal cannot
# hide a forbidden reference later in the file.
IGNORED = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/', re.S)

PACKAGE_MAP = {
    'AppGraph': '', 'MainActivity': '', 'AsrImeService': '',
    'ModelManagementActivity': '', 'LogsActivity': '',
    'TaskKind': 'task', 'RequestContext': 'task', 'TaskCoordinator': 'task', 'RequestRunner': 'task',
    'WaveInput': 'audio', 'PcmWave': 'audio', 'AsrText': 'audio',
    'RecordingControl': 'audio', 'ForegroundRecorder': 'audio',
    'ModelEntry': 'model', 'ModelManifest': 'model', 'ModelReports': 'model',
    'ModelSource': 'model', 'ModelRepository': 'model', 'ModelReadiness': 'model',
    'ModelAccess': 'model', 'ModelProviderBoundary': 'model', 'SafModelSource': 'model',
    'FileSafety': 'model',
    'NativeResponse': 'asr', 'NativeTranscription': 'asr', 'JniNativeTranscription': 'asr',
    'InferenceAdapter': 'asr', 'InferencePhase': 'asr', 'InferencePhaseListener': 'asr',
    'LoggingInferencePhaseListener': 'asr',
    'AppState': 'transcription', 'ResultState': 'transcription',
    'TextExportController': 'transcription', 'TextExportPage': 'transcription',
    'AsrOperation': 'transcription', 'AppRequestPolicy': 'transcription',
    'AppReportWriter': 'transcription', 'ResultFiles': 'transcription',
    'ImeSession': 'ime', 'ImeFieldPolicy': 'ime', 'ImeController': 'ime', 'ImeBackend': 'ime',
    'ModelOperationControl': 'modelmanagement', 'ModelManagementState': 'modelmanagement',
    'ModelManagementController': 'modelmanagement', 'ModelPageSession': 'modelmanagement',
    'ModelPickerTickets': 'modelmanagement', 'ModelUiText': 'modelmanagement',
    'RuntimeLogEventKind': 'diagnostics', 'RuntimeLogSource': 'diagnostics',
    'RuntimeLogEvent': 'diagnostics', 'RuntimeLogClock': 'diagnostics',
    'RuntimeLogSink': 'diagnostics', 'RuntimeLogStore': 'diagnostics',
    'RuntimeLogPersistence': 'diagnostics', 'RuntimeLogCodec': 'diagnostics',
    'RuntimeLogWorker': 'diagnostics', 'LogExportController': 'diagnostics',
    'LogExportPage': 'diagnostics', 'RuntimeLogText': 'diagnostics',
    'OperationContext': 'platform',
}
# Reverse map for "package for class X" queries.
def pkg_of(name):
    if name not in PACKAGE_MAP:
        raise KeyError(name)
    return PACKAGE_MAP[name] or 'root'

def path_of(name):
    sub = PACKAGE_MAP[name]
    if sub:
        return ROOT_BASE / sub / f'{name}.java'
    return ROOT_BASE / f'{name}.java'

def references(source):
    return set(re.findall(r'[A-Za-z_$][A-Za-z0-9_$]*', IGNORED.sub(' ', source)))

def check_model(source):
    forbidden = references(source) & {'ModelManagementState', 'ModelManagementActivity', 'ModelManagementController', 'AppGraph'}
    assert not forbidden, 'model core references presentation/wiring: ' + str(forbidden)
for name in ['ModelReports', 'ModelRepository', 'ModelReadiness', 'ModelAccess', 'ModelManifest', 'ModelEntry', 'ModelSource']:
    check_model(path_of(name).read_text())
check_model('/* ModelManagementState */ String text="https://x/ModelManagementState";')
for bad in ['ModelManagementState state;', 'org.llmasr.minimal.ModelManagementState.Snapshot s;',
            'String x="https://a"; ModelManagementState bad;', 'ModelManagementController controller;']:
    try: check_model(bad)
    except AssertionError: pass
    else: raise AssertionError('dependency guard accepted forbidden fixture')
print('PASS model dependency boundaries and forbidden-reference negative fixtures (lexical, not AST)')

# R2: runner is mechanism only; feature pages may not observe App text for busy.
def check_task(source):
    forbidden = references(source) & {'AppState', 'AppRequestPolicy', 'Reports', 'State',
        'setLastText', 'setLastStatus', 'lastText', 'lastStatus', 'verified', 'setVerified',
        'MAINTENANCE', 'INFERENCE', 'MODEL_OPERATION', 'inferenceReported', 'writePending', 'writeFailure'}
    assert not forbidden, 'generic task mechanism references App policy: ' + str(forbidden)
def check_private_feature(source):
    forbidden = references(source) & {'AppState', 'AppRequestPolicy', 'appState', 'NO_REPORTS', 'PrivateState'}
    assert not forbidden, 'private feature references App state/report policy: ' + str(forbidden)
for name in ['RequestRunner', 'TaskCoordinator']:
    check_task(path_of(name).read_text())
for name in ['ImeController', 'ModelManagementController', 'AsrImeService', 'ModelManagementActivity']:
    check_private_feature(path_of(name).read_text())
for guard, fixture in [(check_task, 'state.setLastText("");'),
                       (check_task, 'ctx.kind != TaskKind.MAINTENANCE;'),
                       (check_task, 'AppRequestPolicy policy;'),
                       (check_private_feature, 'graph.appState().addListener(refresh);'),
                       (check_private_feature, 'AppRequestPolicy.Reports reports;')]:
    try: guard(fixture)
    except AssertionError: pass
    else: raise AssertionError('R2 dependency guard accepted forbidden fixture')
graph = path_of('AppGraph').read_text()
assert 'state::notifyChange' not in graph
for name in ['MainActivity', 'AsrImeService', 'ModelManagementActivity']:
    source = path_of(name).read_text()
    assert 'coordinator().addListener(' in source and 'coordinator().removeListener(' in source
    assert 'WeakReference<' in source and re.search(r'main\.post(?:Delayed)?\(this', source)
assert 'TaskKind.INFERENCE' in path_of('ImeController').read_text()
assert 'TaskKind.MAINTENANCE' not in path_of('ImeController').read_text()
assert not (references(path_of('AppState').read_text()) & {'ModelReadiness', 'verified', 'setVerified'})
print('PASS R2 mechanism/policy/private-feature boundaries, direct lifecycle wiring and negative fixtures (lexical, not Android runtime)')

# R3: feature orchestration must not own JNI, JSON or graph assembly.
def check_inference_caller(source):
    forbidden = references(source) & {'MainActivity', 'JniNativeTranscription', 'System', 'loadLibrary',
        'JSONObject', 'JSONArray', 'MessageDigest', 'ReportWriterImpl', 'AppGraph'}
    assert not forbidden, 'inference caller owns infrastructure: ' + str(forbidden)
    assert not re.search(r'\bnew\s+InferenceAdapter\s*\(', IGNORED.sub(' ', source))
for name in ['AsrOperation', 'ImeBackend']:
    check_inference_caller(path_of(name).read_text())
for fixture in ['MainActivity.invokeTranscribe();', 'AppGraph graph;', 'JSONObject r;',
                'MessageDigest digest;', 'new InferenceAdapter(a,b,c,d);', 'System.loadLibrary("x");']:
    try: check_inference_caller(fixture)
    except AssertionError: pass
    else: raise AssertionError('R3 guard accepted forbidden fixture: ' + fixture)
assert graph.count('new InferenceAdapter(') == 1
assert 'new InferenceAdapter(logStore, access::requireReady,' in graph
assert '() -> System.loadLibrary("qwen_asr_jni"), new JniNativeTranscription()' in graph
assert 'inference, new AppReportWriter(opCtx)' in graph
assert 'logStore, logWorker, inference)' in graph
assert 'new ImeBackend(context, modelRepository, inference)' in graph
assert 'graph.imeBackend()' in path_of('AsrImeService').read_text()
main = references(path_of('MainActivity').read_text())
assert not main & {'native', 'transcribe', 'transcribeWithListener', 'invokeTranscribe', 'invokeTranscribeWithListener'}
jni = path_of('JniNativeTranscription').read_text()
assert 'implements NativeTranscription' in jni and 'loadLibrary' not in references(jni)
assert 'if (listener == null) return transcribe(config, wav, language, cache);' in jni
assert 'return transcribeWithListener(config, wav, language, cache, listener);' in jni
operation = path_of('AsrOperation').read_text()
assert operation.count('public AsrOperation(') == 1
assert 'new AppRequestPolicy(reports, new StateImpl())' in operation
assert operation.index('reports.writeSuccess(ctx, r, wav, seconds, language, source);') < operation.index('ctx.inferenceReported = true;') < operation.index('appState.setLastText(r.display);')
print('PASS R3 graph/shared-adapter/JNI/report boundaries and negative fixtures (lexical, not runtime)')

# R6 allowed dependencies. Same checker is exercised against real source and
# negative fixtures; destination sets never compare a string against a tuple.
ALLOWED = {
    'task': set(), 'audio': set(), 'model': set(), 'platform': set(),
    'diagnostics': set(),
    'asr': {'audio', 'diagnostics'},
    'ime': {'asr', 'audio', 'diagnostics', 'model', 'platform', 'task'},
    'transcription': {'asr', 'audio', 'diagnostics', 'model', 'platform', 'task'},
    'modelmanagement': {'diagnostics', 'model', 'task'},
    'root': {'asr', 'audio', 'diagnostics', 'ime', 'model', 'modelmanagement',
             'platform', 'task', 'transcription'},
}

def check_package(package, text):
    refs = references(text)
    destinations = {pkg_of(name) for name in refs & PACKAGE_MAP.keys()}
    forbidden = destinations - {package} - ALLOWED[package]
    assert not forbidden, f'{package} forbidden dependencies: {sorted(forbidden)}'
    # Wildcard local imports hide class names from lexical edge analysis.
    assert not re.search(r'import\s+org\.llmasr\.minimal[\w.]*\.\*', text)

actual = list(ROOT_BASE.rglob('*.java'))
assert len(actual) == len(PACKAGE_MAP)
assert {p.resolve() for p in actual} == {path_of(n).resolve() for n in PACKAGE_MAP}
for name in PACKAGE_MAP:
    text = path_of(name).read_text()
    expected = 'org.llmasr.minimal' + ('.' + PACKAGE_MAP[name] if PACKAGE_MAP[name] else '')
    assert re.search(r'^package\s+' + re.escape(expected) + r';', text, re.M)
    check_package(pkg_of(name), text)

negatives = [('model', 'ModelManagementState'), ('ime', 'AppState'),
             ('task', 'ModelRepository'), ('task', 'InferenceAdapter'),
             ('platform', 'AppRequestPolicy'), ('diagnostics', 'ImeController')]
negatives += [(package, 'AppGraph') for package in ALLOWED if package != 'root']
for package, name in negatives:
    try:
        check_package(package, 'class Fixture { ' + name + ' forbidden; }')
    except AssertionError:
        pass
    else:
        raise AssertionError(f'actual package checker accepted {package} -> {name}')
check_package('model', '/* AppGraph */ String x="AppState"; ModelReports value;')
print(f'PASS R6 complete recursive package map, allowed edges and {len(negatives)} actual-checker negative fixtures (lexical)')
