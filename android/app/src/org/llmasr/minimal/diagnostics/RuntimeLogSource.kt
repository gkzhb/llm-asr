package org.llmasr.minimal.diagnostics

/** Source of a runtime log event. Distinguishes the App-side, IME-side and
 * background work without leaking request text or audio. */
enum class RuntimeLogSource {
    APP,
    IME,
    SHARED
}
