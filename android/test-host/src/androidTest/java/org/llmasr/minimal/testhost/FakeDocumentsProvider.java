package org.llmasr.minimal.testhost;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** In-process resolver fixture, NOT Binder, a registered provider or a SAF grant. */
public final class FakeDocumentsProvider extends ContentProvider {
    public static final String AUTHORITY = "org.llmasr.minimal.testhost.synthetic";
    public static final String CANARY = "synthetic-private-canary content://synthetic/not-a-private-file";
    public boolean failQuery, failClose, blockQuery;
    public final CountDownLatch entered = new CountDownLatch(1);
    public final CountDownLatch release = new CountDownLatch(1);
    public final AtomicInteger closes = new AtomicInteger();
    public final AtomicInteger queries = new AtomicInteger();
    public volatile boolean failOpen, blockOpen;
    public final CountDownLatch openEntered = new CountDownLatch(1);
    public final CountDownLatch openRelease = new CountDownLatch(1);
    public final AtomicInteger opens = new AtomicInteger();
    private File syntheticFile;

    /** Only this suite's UUID cache/source/model.bin is eligible; never a selected model. */
    void setSyntheticFile(File file) throws IOException {
        File cache = getContext().getCacheDir().getCanonicalFile();
        File canonical = file.getCanonicalFile();
        String relative = cache.toPath().relativize(canonical.toPath()).toString();
        if (!canonical.equals(file.getAbsoluteFile()) || !file.isFile()
                || !relative.matches("m5saf-[0-9a-f-]{36}/source/model\\.bin"))
            throw new IOException("synthetic cache file required");
        syntheticFile = canonical;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Uri tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "synthetic-root");
        Uri expected = DocumentsContract.buildDocumentUriUsingTree(tree, "synthetic-root/model.bin");
        if (!expected.equals(uri) || !"r".equals(mode))
            throw new FileNotFoundException("synthetic document/read-only mode required");
        opens.incrementAndGet();
        openEntered.countDown();
        if (blockOpen) FakePorts.await(openRelease, "provider open release");
        if (failOpen) throw new SecurityException(CANARY);
        if (syntheticFile == null) throw new FileNotFoundException("synthetic file not configured");
        return ParcelFileDescriptor.open(syntheticFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String order) {
        queries.incrementAndGet();
        if (!AUTHORITY.equals(uri.getAuthority())) throw new AssertionError("synthetic authority only");
        entered.countDown();
        if (blockQuery) FakePorts.await(release, "provider query release");
        if (failQuery) throw new SecurityException(CANARY);
        MatrixCursor cursor = new MatrixCursor(new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS}) {
            @Override public void close() {
                closes.incrementAndGet(); super.close();
                if (failClose) throw new IllegalStateException(CANARY);
            }
        };
        cursor.addRow(new Object[] {"synthetic-root/model.bin", "model.bin", "application/octet-stream", 0});
        return cursor;
    }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
