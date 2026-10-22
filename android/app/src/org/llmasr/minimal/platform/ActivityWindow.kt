package org.llmasr.minimal.platform

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Activity viewport/back adapter; never used by the IME service window. */
class ActivityWindow private constructor() {
    class BackRegistration(private val activity: Activity?, private val action: Runnable?) {
        private var callback: Any? = null
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun resumed() {
            if (Build.VERSION.SDK_INT >= 33 && callback == null) callback = Api33.register(activity, action)
        }
        @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
        open fun paused() {
            if (Build.VERSION.SDK_INT >= 33 && callback != null) {
                Api33.unregister(activity, callback)
                callback = null
            }
        }
    }
    @android.annotation.TargetApi(30)
    private class Api30 {
        companion object {
            @Suppress("DEPRECATION")
            @JvmStatic fun edgeToEdge(window: android.view.Window?) { window!!.setDecorFitsSystemWindows(false) }
            @JvmStatic fun safeInsets(insets: WindowInsets?): IntArray {
                val safe = insets!!.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                return intArrayOf(safe.left, safe.top, safe.right, safe.bottom)
            }
        }
    }
    @android.annotation.TargetApi(33)
    private class Api33 {
        companion object {
            // Preserve nullable Java checkcast/forwarding order at this SDK boundary only.
            @Suppress("UNCHECKED_CAST") private fun <T> unchanged(value: Any?): T = value as T
            @JvmStatic fun register(activity: Activity?, action: Runnable?): Any {
                val target = action!! // A Java bound method reference dereferences before registration.
                val callback = OnBackInvokedCallback { target.run() }
                activity!!.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                return callback
            }
            @JvmStatic fun unregister(activity: Activity?, callback: Any?) {
                activity!!.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(unchanged<android.window.OnBackInvokedCallback>(callback as android.window.OnBackInvokedCallback?))
            }
        }
    }
    companion object {
        @JvmStatic
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT", "DEPRECATION")
        open fun configure(activity: Activity?, viewport: View?) {
            val window = activity!!.getWindow()
            if (Build.VERSION.SDK_INT >= 30) Api30.edgeToEdge(window)
            else window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.setStatusBarColor(Color.TRANSPARENT)
            window.setNavigationBarColor(Color.TRANSPARENT)
            window.setNavigationBarContrastEnforced(true)
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attributes
            val left = viewport!!.paddingLeft; val top = viewport.paddingTop
            val right = viewport.paddingRight; val bottom = viewport.paddingBottom
            viewport.setOnApplyWindowInsetsListener { view, insets ->
                val safe = safeInsets(insets)
                view.setPadding(left + safe[0], top + safe[1], right + safe[2], bottom + safe[3])
                insets
            }
            viewport.requestApplyInsets()
        }
        @Suppress("DEPRECATION")
        private fun safeInsets(insets: WindowInsets?): IntArray {
            if (Build.VERSION.SDK_INT >= 30) return Api30.safeInsets(insets)
            val cutout = insets!!.displayCutout
            return intArrayOf(
                Math.max(insets.systemWindowInsetLeft, if (cutout == null) 0 else cutout.safeInsetLeft),
                Math.max(insets.systemWindowInsetTop, if (cutout == null) 0 else cutout.safeInsetTop),
                Math.max(insets.systemWindowInsetRight, if (cutout == null) 0 else cutout.safeInsetRight),
                Math.max(insets.systemWindowInsetBottom, if (cutout == null) 0 else cutout.safeInsetBottom))
        }
    }
}
