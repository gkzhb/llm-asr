package org.llmasr.minimal.model;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Selected-tree adapter. Provider exception messages/URIs never cross this boundary. */
public final class SafModelSource implements ModelSource {
    private final ContentResolver resolver;
    private final Uri tree;
    public SafModelSource(ContentResolver resolver, Uri tree) { this.resolver = resolver; this.tree = tree; }
    private static void check(ModelRepository.CancelGate cancel) throws IOException {
        ModelProviderBoundary.checkpoint(cancel);
    }
    @Override public Map<String, UriRef> enumerate(Collection<String> expected, int maxEntries) throws IOException {
        return enumerate(expected, maxEntries, ModelRepository.NEVER_CANCEL);
    }
    @Override public Map<String, UriRef> enumerate(Collection<String> expected, int maxEntries,
                                                  ModelRepository.CancelGate cancel) throws IOException {
        Map<String, UriRef> children = new HashMap<>();
        Set<String> ids = new HashSet<>();
        check(cancel);
        Uri list = ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE,
            () -> DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)));
        check(cancel);
        Cursor cursor = ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE,
            () -> resolver.query(list, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS}, null, null, null));
        // The close boundary participates in try-with-resources, including cancellation cleanup.
        try (java.io.Closeable closing = () -> {
            if (cursor != null) ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE,
                () -> { cursor.close(); return null; });
        }) {
            check(cancel);
            if (cursor == null) throw new IOException("无法列出所选目录，请重新选择本地模型目录");
            int seen = 0;
            while (true) {
                check(cancel);
                boolean more = ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE, cursor::moveToNext);
                check(cancel);
                if (!more) break;
                if (++seen > Math.min(10000, maxEntries)) throw new IOException("目录条目过多，请选择专用模型目录");
                String name = ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE, () -> cursor.getString(1));
                if (name == null || !expected.contains(name)) continue;
                String id = ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE, () -> cursor.getString(0));
                String mime = ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE, () -> cursor.getString(2));
                boolean nullFlags = ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE, () -> cursor.isNull(3));
                long flags = ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE, () -> cursor.getLong(3));
                if (id == null || id.isEmpty() || mime == null || nullFlags
                        || DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)
                        || (flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0)
                    throw new IOException("受管文件不是可读取的普通文件：" + name);
                if (!ids.add(id) || children.containsKey(name)) throw new IOException("重复模型文件：" + name);
                children.put(name, new UriRef(ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE,
                    () -> DocumentsContract.buildDocumentUriUsingTree(tree, id))));
                check(cancel);
            }
        }
        check(cancel);
        return children;
    }
    @Override public InputStream open(UriRef ref) throws IOException {
        if (!(ref.handle() instanceof Uri)) throw new IOException(ModelProviderBoundary.FILE_FAILURE);
        return ModelProviderBoundary.open(() -> resolver.openInputStream((Uri)ref.handle()));
    }
}
