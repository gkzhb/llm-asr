package org.llmasr.lintfixture;

public final class LintProbe {
    public static void probe(android.app.Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            activity.getOnBackInvokedDispatcher();
        }
    }
}
