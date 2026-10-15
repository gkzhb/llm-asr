package org.llmasr.minimal.platform;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/** Platform-only Activity window adapter; never used for the IME service window. */
public final class ActivityWindow {
    private ActivityWindow() {}

    /** Insets belong to the outer viewport; inner content retains its design spacing.
     * Capture initial padding once: repeated dispatch must never accumulate insets.
     * Leave insets unconsumed so child controls/dialogs keep their normal behavior. */
    public static void configure(Activity activity, View viewport) {
        android.view.Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= 30) Api30.edgeToEdge(window);
        else window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility()
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility()
            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        // Keep the platform contrast scrim for three-button navigation.
        window.setNavigationBarContrastEnforced(true);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attributes);
        final int left = viewport.getPaddingLeft(), top = viewport.getPaddingTop();
        final int right = viewport.getPaddingRight(), bottom = viewport.getPaddingBottom();
        viewport.setOnApplyWindowInsetsListener((view, insets) -> {
            int[] safe = safeInsets(insets);
            view.setPadding(left + safe[0], top + safe[1], right + safe[2], bottom + safe[3]);
            return insets;
        });
        viewport.requestApplyInsets();
    }

    private static int[] safeInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) return Api30.safeInsets(insets);
        DisplayCutout cutout = insets.getDisplayCutout();
        return new int[] {
            Math.max(insets.getSystemWindowInsetLeft(), cutout == null ? 0 : cutout.getSafeInsetLeft()),
            Math.max(insets.getSystemWindowInsetTop(), cutout == null ? 0 : cutout.getSafeInsetTop()),
            Math.max(insets.getSystemWindowInsetRight(), cutout == null ? 0 : cutout.getSafeInsetRight()),
            Math.max(insets.getSystemWindowInsetBottom(), cutout == null ? 0 : cutout.getSafeInsetBottom())
        };
    }

    /** API33+ system gestures invoke exactly the existing Activity back action.
     * No predictive animation is advertised. Dialog/IME dispatch remains platform-owned.
     * Below API33 the Activity's onBackPressed override continues to handle the key. */
    public static final class BackRegistration {
        private final Activity activity;
        private final Runnable action;
        private Object callback;
        public BackRegistration(Activity activity, Runnable action) {
            this.activity = activity;
            this.action = action;
        }
        public void resumed() {
            if (Build.VERSION.SDK_INT >= 33 && callback == null)
                callback = Api33.register(activity, action);
        }
        public void paused() {
            if (Build.VERSION.SDK_INT >= 33 && callback != null) {
                Api33.unregister(activity, callback);
                callback = null;
            }
        }
    }

    @android.annotation.TargetApi(30)
    private static final class Api30 {
        static void edgeToEdge(android.view.Window window) { window.setDecorFitsSystemWindows(false); }
        static int[] safeInsets(WindowInsets insets) {
            // getInsets returns the per-edge union/max, not bars + keyboard twice.
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            return new int[] {safe.left, safe.top, safe.right, safe.bottom};
        }
    }

    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static Object register(Activity activity, Runnable action) {
            android.window.OnBackInvokedCallback callback = action::run;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }
        static void unregister(Activity activity, Object callback) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                (android.window.OnBackInvokedCallback) callback);
        }
    }
}
