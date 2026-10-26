# Findings

Admission: explicit bd show llm-asr-zcm is in_progress, owner p1-steward; embedded llm_asr. HEAD correct, index empty. Parent authority records resident-build done/stopped/noBG and other reviewer read-only. All44 candidate mode/bytes/SHA matched start/end. No engineering commands launched. Full44 diff including5 new files read.

PLATFORM-R1 P1 production blocker: AndroidModelVerificationIo read/write uses borrowed-descriptor Android FileInputStream/FileOutputStream constructors but never closes raw Os.open FD. Actual close pre-rename/error gate absent; leaks plus close-failure contract violation. Desktop OpenJDK wrappers/TestIo hide Android semantics. Exact method-level owner/close regressions requested; no platform probe/source download run.

PLATFORM-R2 P2 test blocker: ModelAndroidHelpersTest.reservation bounded join followed by assertion failure enters outer fixture.use cleanup, deleting directory even if worker still alive or join interrupted. Register worker/retain fixture on unknown exit; limited failure regression required, no budget extension.

Evidence verified read-only: seal20, domains65/7/30,203 owned records+406 streams, four proof mutants ordinary/-O compile0/red1/restored0,169 current/frozen module deps,8 App sources/24 classes bijection,physical finite checker ROOT/ordinary-O15 guards,JUnit34+1. Historical next/compat production bytes current but five test-only deltas explicitly reconciled. Complexity same tools223 current source hashes; added scope FAIL-UNKNOWN retained.28 old artifacts unchanged/not current APK. Herdr once read-only actual resident-build wY:p5A done, reviewers working; no control/mutation/polling.
