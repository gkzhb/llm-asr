# Model management review disposition — round 1

Status: required fixes accepted, implementation pending. Original reports preserved verbatim:
- reports/review/model-management-android-independent.md (98e2832f)
- reports/review/model-management-core-independent.md (44a6d42a)

Both reviews were static, source61/61 matched. Parent build b6b11ff8b passed947 checks/full package, SHA f4ad0e57b4d3da661a310df94510affbb42bad95bd71b2533310974b7caf37ab. That package is review baseline, not final delivery.

## Required fix batch

| Item | Decision and acceptance |
|---|---|
| Android R1 | INSPECT updates inventory/readiness only, retains explicit IMPORT/VERIFY/DELETE recent terminal identity/outcome/errors/cleanup/delete stats until next explicit operation. Transient inspection busy/error separate if needed. Real controller cancel/failure/partial cleanup/delete→refresh regressions. |
| R2/Core F2 | All provider-authored exception text sanitized at query/cursor/open/read/close. Do not forward java CancellationException or app-named exception from provider. Genuine own gate cancellation stays typed fixed message; unsolicited provider cancellation safe failure. Test actual production boundary via safely extracted host adapter seam; no claim real ContentResolver runtime. |
| R3/F3 | Every terminal decision immutable to late cancel, including FAILED/CANCELLED, while owner retained until finalizer. Reentrant listener and bounded post-terminal/pre-finalize tests plus success-first. |
| Core F1 | Lazy verify validates all managed final/part paths before READY, consistent with inspect. Unsafe part dir/link fails INVALID, preserve target. Safe regular orphan part not auto-deleted. |
| R4/F4 | Publish reuse/copy/required/available plan before space refusal or final verification, retain in terminal/inspection history; all-reused exact totals with zero source opens, multi-file cumulative progress. |
| R5 | Terminal deletion must not render 8/7 or15/7 live-file counters; retain processed/planned semantics or omit terminal live row. |
| Core F5 | Listener test captures failure outside production Throwable guard, strong listener reference, bounded worker teardown. Compilable monitor-held notification mutation must fail behavioral assertion. |
| Core F6 | Bind derived aapt/signature/manifest reports to current APK SHA and report hashes/run provenance; standalone checker rejects stale/modified inputs. Full build regenerates binding serially. Test mismatch rejection, not only source string assertions. |

## Small completeness corrections inside same batch

- Live deletion success/failure counts, per-file reuse/failure progress presentation if implementable as bounded snapshot additions; no UI/database overhaul.
- Fix PartRecoveryTest stale explanatory comments to match actual executable fixture.
- ModelPageSession.cancellable single snapshot hardening allowed; **not** a previously proven production NPE.
- Preserve one owner, nonblocking cancellation, native/RecordingControl/IME isolation. No new permissions/background/model variants/automatic retry.

## Explicitly bounded/deferred

- Exact external-owner name currently generic honest App/IME/other-page hint: nonblocking UI enhancement, no second busy identity introduced solely for text.
- Small bundled manifest bootstrap synchronous asset read is inherited behavior, not large model hashing on UI; not expanded to asynchronous graph redesign here.
- Device SAF/Activity/IME/visual/accessibility behavior remains manual-device pending; host adapter seam is not real provider validation.
- No network exfiltration/corruption claim from provider-message finding; local privacy/error boundary only.

## Execution boundary

One writer for accepted production/tests/scripts fixes. Parent owns docs/reports/dist and preserves baseline. Worker runs host+javac only, parent complete APK after checkpoint. Source freezes again before independent focused follow-up. No commit/push/device/native/model changes.

## Round 2 complete / final narrow follow-up

- 8d949144 independently closes R1/R2/R3/CoreF1-F3 source safety. 3db342f7 closes R4/F4/R5/F5/F6 mechanisms, retains E1 inaccurate published-file observation and E2 zero-space test gap. Both static only,69 frozen inputs match.
- Parent accepts E1/E2: adds post-successful-rename historical observation before cancel checkpoint (not READY), true A-published/B-SHA-failed and publish-then-cancel controller tests; all-reuse/part-reclaim fixtures use available=0 with noCOPY/open assertions.
- Red E1 assertion captured .work/model-final-polish/publication-red.log; targeted publication/planning now pass. Changed only ModelRepository.java, ModelReviewFixTest.java and host suite inclusion. Final freeze .work/model-final-polish/frozen-input-sha256.json.
- Existing pending-before-completed-plan count, generic owner names, cached unknown inventory after failed inspect and empty-directory removal notice remain bounded display limitations, not further safety work. Final focused check/build before delivery.


## Final disposition

8c20d499 final narrow review closes E1/E2,69 final hashes match, no new blocker. Full report reports/review/model-management-final-publication-review.md. Parent final build b0453981b exit0/APK_READY;945 numeric assertions +8 review groups/provider boundary/3 mutants/checker fixtures passed. Parent checked78 build inputs include and match69 freeze, APK/report binding and unchanged native DSO. Final APK SHA6e4fbf7a4cf07262912019dc667258123a32f95931b13bb1c046cf6e77471073.

**All required accepted findings closed at source level; ready for manual device acceptance.** Reviewers did not execute tests/builds. Retain the documented device, provider, small bootstrap/UI-cache and storage durability limitations. No further optional polish or implementation loop; no commit/push/device access.
