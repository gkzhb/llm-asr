package org.llmasr.minimal.platform;

import android.content.ContentResolver;
import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Application-level context. The interface keeps the operation layer
 * testable on the host: AsrOperation depends only on this interface, and
 * the Android-side wiring supplies AndroidOperationContext; host tests
 * supply a fake implementation.
 */
public interface OperationContext {
    File filesDir();
    File cacheDir();
    InputStream openAsset(String name) throws IOException;
    ContentResolver contentResolver();

    /** Android-side implementation that wraps the application Context. */
    final class Android implements OperationContext {
        private final Context appContext;
        public Android(Context appContext) {
            if (appContext == null) throw new IllegalArgumentException("appContext required");
            Context ac = appContext.getApplicationContext();
            if (ac == null) throw new IllegalArgumentException("applicationContext required");
            this.appContext = ac;
        }
        @Override public File filesDir() { return appContext.getFilesDir(); }
        @Override public File cacheDir() { return appContext.getCacheDir(); }
        @Override public InputStream openAsset(String name) throws IOException { return appContext.getAssets().open(name); }
        @Override public ContentResolver contentResolver() { return appContext.getContentResolver(); }
    }
}
