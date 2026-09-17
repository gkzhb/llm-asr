package org.llmasr.minimal.transcription;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Process-wide UI state. Survives Activity recreation; both the operation
 * layer (writer) and the Activity (reader + listener) touch it.
 *
 * No busy flag: busy is derived from the coordinator by the UI at refresh
 * time. Listeners are weakly held so a destroyed Activity cannot leak; the
 * Activity is still expected to remove its listener in onPause so that a
 * recreated Activity can install a fresh one without being filtered by a
 * request-id field (no per-request id is exposed here).
 *
 * The TEXT+REVISION single-owner state lives in {@link ResultState}, accessed
 * via {@link #resultState()}. AppState's lastText/lastText set is a thin
 * facade that delegates to ResultState; this preserves App policy/report
 * semantics while ensuring that export and edit both consult the same
 * monotonic revision.
 */
public final class AppState {
    private static final String DEFAULT_STATUS =
        "模型尚未校验，请打开模型管理检查或导入。";

    public interface Listener { void onChange(); }

    private final ResultState resultState = new ResultState();
    private final AtomicReference<String> lastStatus = new AtomicReference<>(DEFAULT_STATUS);
    private final List<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();

    public ResultState resultState() { return resultState; }

    public String lastStatus() { return lastStatus.get(); }
    public void setLastStatus(String value) {
        if (value == null) value = "";
        lastStatus.set(value);
        notifyChange();
    }

    public String lastText() { return resultState.currentText(); }
    public void setLastText(String value) {
        if (value == null) value = "";
        resultState.setText(value);
        notifyChange();
    }

    /** Edit by ABA-safe revision match. Returns the new revision on success,
     * or -1 if the expected revision no longer matches. */
    public long applyEdit(long expectedRevision, String edited) {
        long r = resultState.applyEdit(expectedRevision, edited);
        if (r > 0) notifyChange();
        return r;
    }

    public void clearText() {
        resultState.clear();
        notifyChange();
    }

    public void addListener(Listener l) {
        if (l == null) return;
        listeners.removeIf(ref -> ref.get() == null);
        listeners.add(new WeakReference<>(l));
    }

    public void removeListener(Listener l) {
        listeners.removeIf(ref -> {
            Listener current = ref.get();
            return current == null || current == l;
        });
    }

    public void notifyChange() {
        listeners.removeIf(ref -> ref.get() == null);
        for (WeakReference<Listener> ref : listeners) {
            Listener l = ref.get();
            if (l != null) {
                try { l.onChange(); } catch (Throwable ignored) { /* listener faults must not poison the writer */ }
            }
        }
    }
}
