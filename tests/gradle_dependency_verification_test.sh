#!/usr/bin/env bash
# Isolated verification metadata mutation: never alter the live project/cache.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$root/.cache/gradle-user-home}"
mkdir -p "$root/.work/gradle-m1"
fixture=$(mktemp -d "$root/.work/gradle-m1/dependency-negative-XXXXXX")
cp settings.gradle.kts build.gradle.kts gradle.properties buildscript-gradle.lockfile "$fixture/"
mkdir -p "$fixture/android/app" "$fixture/android/asr-runtime" "$fixture/core" "$fixture/gradle"
cp android/app/build.gradle.kts android/app/gradle.lockfile "$fixture/android/app/"
cp android/asr-runtime/build.gradle.kts android/asr-runtime/gradle.lockfile "$fixture/android/asr-runtime/"
cp core/build.gradle.kts core/gradle.lockfile "$fixture/core/"
cp gradle/verification-metadata.xml "$fixture/gradle/"
python3 - "$fixture/gradle/verification-metadata.xml" <<'PY'
import sys,xml.etree.ElementTree as E
from pathlib import Path
p=Path(sys.argv[1]);r=E.fromstring(p.read_text());n={'g':'https://schema.gradle.org/dependency-verification'}
for c in r.findall('g:components/g:component',n):
 if c.attrib == {'group':'com.android.tools.build','name':'gradle','version':'8.6.1'}:
  for a in c.findall('g:artifact',n):
   if a.attrib['name']=='gradle-8.6.1.jar':
    for s in a.findall('g:sha256',n):s.attrib['value']='0'*64
E.register_namespace('',n['g']);p.write_bytes(E.tostring(r,encoding='utf-8',xml_declaration=True))
PY
set +e
"$root/gradlew" -p "$fixture" help --offline --no-daemon --console=plain --dependency-verification strict > "$fixture/rejection.log" 2>&1
code=$?
set -e
test "$code" -ne 0
# Require checksum rejection, not missing source/tool/plugin errors.
grep -Eiq 'Dependency verification failed' "$fixture/rejection.log"
grep -q 'gradle-8.6.1.jar' "$fixture/rejection.log"
printf 'PASS strict checksum mutation rejected exit=%s log=%s\n' "$code" "$fixture/rejection.log"
