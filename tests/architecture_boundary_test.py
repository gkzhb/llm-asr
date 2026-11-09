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
    'ModelAccess': 'model', 'ModelResidency': 'model', 'ModelVerification': 'model', 'AndroidModelVerificationIo': 'model',
    'ModelProviderBoundary': 'model', 'SafModelSource': 'model',
    'FixedModelPackage': 'model', 'ModelZipArchive': 'model', 'ModelZipSource': 'model', 'SafModelZipSource': 'model',
    'ModelPackagePolicy': 'model', 'ModelDeployment': 'model', 'ModelCatalog': 'model',
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
    'OperationContext': 'platform', 'ActivityWindow': 'platform',
}
# Reverse map for "package for class X" queries.
def pkg_of(name):
    if name not in PACKAGE_MAP:
        raise KeyError(name)
    return PACKAGE_MAP[name] or 'root'

def path_of(name):
    sub = PACKAGE_MAP[name]
    base = ROOT_BASE / sub / name
    matches = [p for p in (base.with_suffix('.java'), base.with_suffix('.kt')) if p.is_file()]
    assert len(matches) == 1, f'missing/duplicate JVM source for {name}'
    return matches[0]

def references(source):
    return set(re.findall(r'[A-Za-z_$][A-Za-z0-9_$]*', IGNORED.sub(' ', source)))

def check_model(source):
    forbidden = references(source) & {'ModelManagementState', 'ModelManagementActivity', 'ModelManagementController', 'AppGraph'}
    assert not forbidden, 'model core references presentation/wiring: ' + str(forbidden)
for name in ['ModelReports', 'ModelRepository', 'ModelReadiness', 'ModelAccess', 'ModelResidency', 'ModelVerification', 'ModelManifest', 'ModelEntry', 'ModelSource', 'FixedModelPackage', 'ModelPackagePolicy', 'ModelDeployment', 'ModelCatalog', 'ModelZipArchive', 'ModelZipSource']:
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
    listener_receiver = 'coordinator()!!.' if path_of(name).suffix == '.kt' else 'coordinator().'
    assert listener_receiver + 'addListener(' in source and listener_receiver + 'removeListener(' in source
    assert ('WeakReference<' in source or 'WeakReference(' in source) and re.search(r'main\.post(?:Delayed)?\(this', source)
assert 'TaskKind.INFERENCE' in path_of('ImeController').read_text()
assert 'TaskKind.MAINTENANCE' not in path_of('ImeController').read_text()
assert not (references(path_of('AppState').read_text()) & {'ModelReadiness', 'verified', 'setVerified'})
print('PASS R2 mechanism/policy/private-feature boundaries, direct lifecycle wiring and negative fixtures (lexical, not Android runtime)')

# R3: feature orchestration must not own JNI, JSON or graph assembly.
def require_adapter_contract(condition, diagnostic):
    if not condition:
        raise AssertionError(diagnostic)

def check_inference_caller(source):
    forbidden = references(source) & {'MainActivity', 'JniNativeTranscription', 'System', 'loadLibrary',
        'JSONObject', 'JSONArray', 'MessageDigest', 'ReportWriterImpl', 'AppGraph'}
    assert not forbidden, 'inference caller owns infrastructure: ' + str(forbidden)
    require_adapter_contract(adapter_constructions(source) == 0, 'inference caller must not construct adapter')

def adapter_constructions(source):
    # Narrow Java/Kotlin construction spelling; literals/comments cannot add or hide a call.
    code = IGNORED.sub(' ', source)
    return len(re.findall(r'\b(?:new\s+)?InferenceAdapter\s*\(', code))
for name in ['AsrOperation', 'ImeBackend']:
    check_inference_caller(path_of(name).read_text())
for fixture in ['MainActivity.invokeTranscribe();', 'AppGraph graph;', 'JSONObject r;',
                'MessageDigest digest;', 'System.loadLibrary("x");']:
    try: check_inference_caller(fixture)
    except AssertionError: pass
    else: raise AssertionError('R3 guard accepted forbidden fixture: ' + fixture)
for fixture in ['new InferenceAdapter(a,b,c,d);', 'val copy = InferenceAdapter(null, a, b, c)']:
    try: check_inference_caller(fixture)
    except AssertionError as failure:
        require_adapter_contract(str(failure) == 'inference caller must not construct adapter', 'wrong caller rejection')
    else: raise AssertionError('caller adapter construction accepted')
check_inference_caller('val injected: InferenceAdapter? = null // InferenceAdapter(a,b,c,d)\n')
graph_kotlin = path_of('AppGraph').suffix == '.kt'
ASSEMBLY = [
    ('new InferenceAdapter(logStore, access::requireResidentReady,', 'InferenceAdapter(logStore, access::requireResidentReady,'),
    ('null, bridge, new InferenceAdapter.Session()', 'null, bridge, object : InferenceAdapter.Session'),
    ('inference, new AppReportWriter(opCtx)', 'inference, AppReportWriter(opCtx)'),
    ('logStore, logWorker, inference)', 'logStore, logWorker, inference)'),
    ('new ImeBackend(context, modelRepository, inference, modelAccess)', 'ImeBackend(context, modelRepository, inference, modelAccess!!)'),
]
def check_assembly(source, kotlin):
    require_adapter_contract(adapter_constructions(source) == 1, 'graph must construct exactly one inference adapter')
    # Strip comments but preserve string arguments needed for the library wiring contract.
    code = IGNORED.sub(lambda match: ' ' if match.group().startswith(('//', '/*')) else match.group(), source)
    for java_form, kotlin_form in ASSEMBLY:
        required = kotlin_form if kotlin else java_form
        require_adapter_contract(code.count(required) == 1, 'graph assembly contract: ' + required)
check_assembly(graph, graph_kotlin)
for required in [pair[1 if graph_kotlin else 0] for pair in ASSEMBLY]:
    try: check_assembly(graph.replace(required, 'removedAssembly'), graph_kotlin)
    except AssertionError as failure:
        require_adapter_contract(str(failure).startswith(('graph assembly contract:', 'graph must construct exactly one')), 'wrong missing assembly rejection')
    else: raise AssertionError('assembly missing fixture accepted by actual checker')
for kotlin in [False, True]:
    valid = '\n'.join(pair[1 if kotlin else 0] for pair in ASSEMBLY)
    check_assembly(valid, kotlin)
    for bad in [valid.replace('InferenceAdapter(', 'RemovedAdapter(', 1),
                valid + ('\nval copy = InferenceAdapter(null, a, b, c)' if kotlin else '\nnew InferenceAdapter(null,a,b,c);')]:
        try: check_assembly(bad, kotlin)
        except AssertionError as failure:
            require_adapter_contract(str(failure) == 'graph must construct exactly one inference adapter', 'wrong duplicate/missing constructor rejection')
        else: raise AssertionError('assembly constructor missing/duplicate accepted')
    check_assembly(valid + '\n// InferenceAdapter(null,a,b,c)\nString note = "InferenceAdapter(a,b,c,d)";', kotlin)
assert ('graph!!.imeBackend()' if path_of('AsrImeService').suffix == '.kt' else 'graph.imeBackend()') in path_of('AsrImeService').read_text()
main = references(path_of('MainActivity').read_text())
assert not main & {'native', 'transcribe', 'transcribeWithListener', 'invokeTranscribe', 'invokeTranscribeWithListener'}
jni = path_of('JniNativeTranscription').read_text()
assert 'implements NativeTranscription' in jni and 'loadLibrary' not in references(jni)
assert 'if (listener == null) return transcribe(config, wav, language, cache);' in jni
assert 'return transcribeWithListener(config, wav, language, cache, listener);' in jni
operation = path_of('AsrOperation').read_text()
operation_kotlin = path_of('AsrOperation').suffix == '.kt'
assert operation.count('class AsrOperation(' if operation_kotlin else 'public AsrOperation(') == 1
assert ('AppRequestPolicy(reports, StateImpl())' if operation_kotlin else 'new AppRequestPolicy(reports, new StateImpl())') in operation
ordered_parts = (['reports!!.writeSuccess(ctx, r, wav, seconds, language, source)', 'ctx.inferenceReported = true', 'appState.setLastText(r.display)']
                 if operation_kotlin else ['reports.writeSuccess(ctx, r, wav, seconds, language, source);', 'ctx.inferenceReported = true;', 'appState.setLastText(r.display);'])
def check_report_order(text):
    positions = [text.index(part) for part in ordered_parts]
    assert positions == sorted(positions), 'report before inferenceReported before text'
check_report_order(operation)
try:
    check_report_order(operation.replace(ordered_parts[0], ordered_parts[2], 1))
except (AssertionError, ValueError):
    pass
else:
    raise AssertionError('report order guard accepted missing writeSuccess')
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

actual = list(ROOT_BASE.rglob('*.java')) + list(ROOT_BASE.rglob('*.kt'))
assert len(actual) == len(PACKAGE_MAP)
assert {p.resolve() for p in actual} == {path_of(n).resolve() for n in PACKAGE_MAP}
for name in PACKAGE_MAP:
    text = path_of(name).read_text()
    expected = 'org.llmasr.minimal' + ('.' + PACKAGE_MAP[name] if PACKAGE_MAP[name] else '')
    assert re.search(r'^package\s+' + re.escape(expected) + r'(?:;|\s*$)', text, re.M)
    check_package(pkg_of(name), text)

negatives = [('model', 'ModelManagementState'), ('ime', 'AppState'),
             ('task', 'ModelRepository'), ('task', 'InferenceAdapter'),
             ('platform', 'AppRequestPolicy'), ('diagnostics', 'ImeController')]
negatives += [(package, 'AppGraph') for package in ALLOWED if package != 'root']
# Newly registered model owners are subject to the same existing package edge policy.
negatives += [(package, name) for package in ('task', 'audio', 'platform', 'diagnostics', 'asr')
              for name in ('ModelPackagePolicy', 'ModelDeployment', 'ModelCatalog')]
for package, name in negatives:
    try:
        check_package(package, 'class Fixture { ' + name + ' forbidden; }')
    except AssertionError:
        pass
    else:
        raise AssertionError(f'actual package checker accepted {package} -> {name}')
check_package('model', '/* AppGraph */ String x="AppState"; ModelReports value;')
print(f'PASS R6 complete recursive package map, allowed edges and {len(negatives)} actual-checker negative fixtures (lexical)')
