# Phase13 IME review disposition

## Decision

Deliver current `0.4-ime-debug` as a **manual device test build**, not a store release or device-verified IME. No further source/test edits after the final reviewed build. The initial rejection and all test-evidence limits remain in the original reports.

- Initial independent review: `ime-independent.md`, B1 source-level picker recovery blocker.
- Follow-up: `ime-fix-independent.md`, B1 closed, no remaining blockers within scope; independently reran 405 host checks.
- Parent build `b4ea5c08d` exit0 and APK_READY, 405 host checks, resource/Java/DEX/native/signature/package checks passed.
- Parent merged initial/follow-up fingerprints and verified all 31 reviewed files, all 55 build-input files and current APK SHA. Follow-up replaces only the five explicitly re-reviewed file fingerprints. No claim that either reviewer built or ran the APK.
- APK SHA256: `c433d1b82f30b5b1ef3ab37fbd4b349199196bd8507386d97e0463ca5c451a2c`.

## Findings

| Finding | Disposition |
|---|---|
| B1 picker dismiss/current selection leaves only logical hidden state | Source fix accepted: invalidate, requestHideSelf(0), then external launch. Actual window hide is asynchronous and device behavior is untested. User may need to tap editor to re-show; never restore old preview or auto-record. |
| Initial N1 App busy prompt | Fixed for other controllers/App. Follow-up P3: old task in same IME controller can still leave new session's record instruction visible while button is disabled. Safe owner exclusion remains; defer wording refinement. |
| Initial N2 disconnected evidence claims | Removed unrelated AppState assertion, narrowed fake-audio evidence, improved cleanup-failure and lifecycle command tests. Follow-up P3: post-handoff stopped assertion alone cannot detect no-op stop; actual production delegation was source-inspected. Defer test strengthening without treating current check as Android stop proof. |
| N3 cancellation cutoff | Documented accurately: post-capture handoff processing (including model verification before JNI) is noncancellable, old results discarded and owner retained until completion. |
| Failed build bff361037 | Historical exit1 retained. AAPT2 tree-depth and true-value parser corrected; final build regenerated all package dumps before passing. |

## Remaining device acceptance

Manually enable/select IME and authorize microphone through launcher. Test ordinary text, password denial, permission denial/regrant, record/stop/preview/confirm once, field/app switches during capture and processing, picker dismissal/reselection, settings return, same-field hide/re-show, repeated requests and App/IME contention. Use nonprivate test text. No device/microphone was accessed by the agent.

Native mathematics/model data unchanged. Debuggable build; no hardened privacy certification, secure erase guarantee, multi-device performance claim, or new inference quality evidence. Failed delete/process kill may retain bounded-cleanup temporary WAVs. No commit/push.
