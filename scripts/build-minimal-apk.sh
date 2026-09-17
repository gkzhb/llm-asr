#!/usr/bin/env bash
# Run under scripts/nix-env.sh apk. No Gradle, Maven, network or model conversion.
set -euo pipefail
: "${ANDROID_HOME:?Use scripts/nix-env.sh apk}"
: "${ANDROID_NDK_ROOT:?Use scripts/nix-env.sh apk}"
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
build="$root/.work/build/minimal-apk"
source_dir="$root/.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36"
bt="$ANDROID_HOME/build-tools/35.0.0"
platform="$ANDROID_HOME/platforms/android-35/android.jar"
compiler="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++"
mkdir -p "$build" "$root/dist" "$root/reports/apk"
bash scripts/test-minimal-apk.sh | tee reports/apk/java-tests.txt
# Only clear this script's derived outputs; preserve ignored local signing key.
rm -rf "$build/classes" "$build/dex" "$build/package" "$build/assets"
mkdir -p "$build/classes" "$build/dex" "$build/package/lib/arm64-v8a" "$build/assets"
cp android/app/assets/* "$build/assets/"
cp reports/p0/mnn-model-manifest.json "$build/assets/model-manifest.json"
cp bench/audio/zh-original.wav "$build/assets/sample.wav"
python3 - <<'PY'
import hashlib,json
from pathlib import Path
m=json.loads(Path('reports/p0/mnn-model-manifest.json').read_text())
assert {e['file'] for e in m['files']}=={'config.json','llm_config.json','audio.mnn','audio.mnn.weight','llm.mnn','llm.mnn.weight','tokenizer.txt'}
# Pin small configs before embedding their hashes: imported paths must not redirect outside the model.
for name in ('config.json','llm_config.json'):
    e=next(e for e in m['files'] if e['file']==name)
    b=Path('models/mnn-16',name).read_bytes()
    assert len(b)==e['bytes'] and hashlib.sha256(b).hexdigest()==e['sha256']
c=json.loads(Path('models/mnn-16/config.json').read_text())
assert c['llm_model']=='llm.mnn' and c['llm_weight']=='llm.mnn.weight' and c['tokenizer_file']=='tokenizer.txt'
sample=Path('bench/audio/zh-original.wav').read_bytes()
assert hashlib.sha256(sample).hexdigest()=='46dbc998c9d1d48111267c40741dd3200f2e5bcf4075f8c4c97f4451160dce50'
PY
lib="$root/.work/build/mnn-android/libMNN.so"
test -f "$lib"
# Do not silently package an older library than the final device-verified P0 artifact.
expected=$(awk '$2 ~ /\/libMNN\.so$/ {print $1}' reports/p0/native-artifact-sha256.txt)
test -n "$expected"
printf '%s  %s\n' "$expected" "$lib" | sha256sum -c -
# Caller must build final patched MNN first; record exact library hash below.
# Compile the same frozen JNI source against a Java-generated header before
# linking; exported short names alone cannot prove C++ parameter signatures.
bash scripts/compile-jni-object.sh
python3 scripts/link-apk-native.py "$build/package/lib/arm64-v8a/libqwen_asr_jni.so"
# Keep only the single combined DSO in the APK, not intermediate .o/.rsp files.
rm -f "$build/package/lib/arm64-v8a/asr_jni.o" "$build/package/lib/arm64-v8a/native-link.rsp"
"$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -d "$build/package/lib/arm64-v8a/"*.so > reports/apk/native-dynamic.txt
"$bt/aapt2" compile --dir android/app/res -o "$build/res-compiled.zip"
"$bt/aapt2" link --auto-add-overlay -o "$build/base.apk" -I "$platform" --manifest android/app/AndroidManifest.xml -A "$build/assets" -R "$build/res-compiled.zip" --java "$build/generated"
# Android's boot stubs omit LambdaMetafactory.metafactory. Compile Java 8
# against JDK release APIs + Android classes; d8 below desugars the lambdas.
# R6: recursive discovery picks up every production subpackage.
mapfile -t prod_sources < <(find android/app/src/org/llmasr/minimal -name '*.java' -type f | LC_ALL=C sort)
javac -encoding UTF-8 --release 8 -classpath "$platform" -d "$build/classes" "${prod_sources[@]}"
python3 scripts/check-jni-symbols.py "$build/classes" "$build/package/lib/arm64-v8a/libqwen_asr_jni.so"
mapfile -t classes < <(find "$build/classes" -name '*.class' -type f | sort)
"$bt/d8" --min-api 29 --lib "$platform" --output "$build/dex" "${classes[@]}"
cp "$build/dex/classes.dex" "$build/package/classes.dex"
cp "$build/base.apk" "$build/unsigned.apk"
(cd "$build/package" && zip -q -r "$build/unsigned.apk" classes.dex lib)
"$bt/zipalign" -f -p 4 "$build/unsigned.apk" "$build/aligned.apk"
key="$root/.cache/android-signing/debug.p12"
mkdir -p "$(dirname "$key")"; chmod 700 "$(dirname "$key")"
if [ ! -f "$key" ]; then
  keytool -genkeypair -keystore "$key" -storetype PKCS12 -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Android Debug,O=llm-asr,C=CN'
  chmod 600 "$key"
fi
apk="$root/dist/qwen-asr-minimal-debug.apk"
"$bt/apksigner" sign --ks "$key" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$apk" "$build/aligned.apk"
"$bt/apksigner" verify --verbose --print-certs "$apk" > reports/apk/signature.txt
"$bt/aapt2" dump badging "$apk" > reports/apk/badging.txt
"$bt/aapt2" dump permissions "$apk" > reports/apk/permissions.txt
"$bt/aapt2" dump xmltree "$apk" --file AndroidManifest.xml > reports/apk/manifest-tree.txt
"$bt/aapt2" dump xmltree "$apk" --file res/xml/method.xml > reports/apk/ime-method-tree.txt
python3 scripts/apk_report_binding.py
"$bt/zipalign" -c -p 4 "$apk"
unzip -l "$apk" > reports/apk/contents.txt
sha256sum "$apk" "$lib" "$build/package/lib/arm64-v8a/libqwen_asr_jni.so" > reports/apk/artifact-sha256.txt
python3 - <<'PY'
import json,hashlib
from pathlib import Path
files=['flake.nix','flake.lock','scripts/build-minimal-apk.sh','scripts/check-minimal-apk.py','scripts/test-minimal-apk.sh','tests/MinimalApkTest.java','tests/RecordingRaceTest.java','tests/ResultFilesTest.java','tests/NativeResponseTest.java','tests/ModelRepositoryTest.java','tests/PartRecoveryTest.java','tests/RequestRunnerTest.java','tests/TaskCoordinatorTest.java','tests/AdmissionBoundaryTest.java','tests/ImeSessionTest.java','reports/p0/mnn-model-manifest.json']
files += [str(p) for p in sorted(Path('android/app').rglob('*')) if p.is_file()]
files += [str(p) for p in sorted(Path('tests').glob('*')) if p.is_file()]
files += [str(p) for p in sorted(Path('tests/fixtures').rglob('*')) if p.is_file()]
files += ['scripts/compile-android-java.sh','scripts/apk_report_binding.py','scripts/check-jni-symbols.py','scripts/compile-jni-object.sh']
files += ['native/apk/asr_jni.cpp','scripts/link-apk-native.py']+[str(p) for p in sorted(Path('patches').glob('*.patch'))]
Path('reports/apk/build-input-sha256.json').write_text(json.dumps({p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in files},indent=2)+'\n')
PY
python3 scripts/check-minimal-apk.py | tee reports/apk/package-checks.txt
printf '\nAPK_READY %s\n' "$apk"
ls -lh "$apk"
