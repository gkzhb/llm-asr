# Findings / lsk-parent

Current AsrImeService routes return button through generic leaveForExternalUi which invalidates then requests hide before switchToPreviousInputMethod. This is an inspection fact, not yet confirmed device root cause. Generic picker/settings historically intentionally hide. Need production-path red-capable host feedback that models system visibility semantics without claiming real Binder/system execution.
Current user clarifies keyboard hides but previous IME never displays. Device/Android/installed APK identity remain unknown. No device access authorized.
Prior23p accepted true as request acceptance only, false/RuntimeException picker fallback; old test ordering deliberately asserts hide before external, so regression must preserve generic external behavior but challenge return-specific hiding as appropriate after evidence.
Runtime admission: herdr agent list only current parent wY:p1 in this repo; historical residentb-build-2/review-9 crash notices retained, no existing source writer. d5b38ac current HEAD, clean tracked/index, pre-existing untracked excluded.
