# Findings

- User explicitly authorized a local code commit; the prior message reports successful APK execution with different quantized models. This is user-reported verification, with no additional device identity, model list or test matrix supplied; it is not agent-run device acceptance.
- Existing full build: `bash scripts/verify-gradle.sh --offline --rerun-tasks`, terminal exit 0, 184 tasks, 42 JUnit tests, no failures/errors/skips. It ran against the dirty frozen source, not a new commit object.
- `.work/xyh-full-local-3/source-freeze.json` contains exactly 54 current SHA256-matching files, including the eight new product/test source files. Two additional tracked changes implement the user's APK version policy.
- Engineering APK: `android/app/build/outputs/apk/debug/app-debug.apk`, version 0.6-debug/code 6; SHA256 096a563efa0577f5b9b38ea7caa1064eedda28c1f179c1393cf037edd0035b5d.
- Native/UI independent closure remains absent. Existing local review and passing tests are not independent review. Do not retry/rephrase/provider-fallback to obtain a replacement.
- New-version APK build remains pending under the user's version-increment rule. No product/version edits are part of this checkpoint, and no release-ready APK is claimed.
- All other untracked planning directories, QwenASR, standalone history and unrelated design/evaluation documents remain excluded. Model weights, archives, audio, binaries, signing secrets, caches and local session data must never be staged.
