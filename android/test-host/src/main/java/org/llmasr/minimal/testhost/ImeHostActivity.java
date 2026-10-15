package org.llmasr.minimal.testhost;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

/** No AppGraph, Service, audio or JNI initialization. Instrumentation-only UI host. */
public final class ImeHostActivity extends Activity {
    private final EditText[] fields = new EditText[3];

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new EditText(this);
            fields[i].setInputType(InputType.TYPE_CLASS_TEXT | (i == 2
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_NORMAL));
            layout.addView(fields[i]);
        }
        setContentView(layout);
    }

    /** Call only on the main thread; no framework lifecycle emulation. */
    public EditText field(int index) { return fields[index]; }
}
