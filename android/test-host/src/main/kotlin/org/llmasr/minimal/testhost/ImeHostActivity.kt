package org.llmasr.minimal.testhost

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

/** No AppGraph, Service, audio or JNI initialization. Instrumentation-only UI host. */
class ImeHostActivity : Activity() {
    private val fields = arrayOfNulls<EditText>(3)
    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        for (i in fields.indices) {
            fields[i] = EditText(this)
            fields[i]!!.inputType = InputType.TYPE_CLASS_TEXT or (if (i == 2)
                InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_NORMAL)
            layout.addView(fields[i])
        }
        setContentView(layout)
    }
    /** Call only on the main thread; no framework lifecycle emulation. */
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    open fun field(index: Int): EditText? = fields[index]
}
