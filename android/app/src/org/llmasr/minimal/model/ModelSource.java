package org.llmasr.minimal.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Source of bytes for a model import. Android SAF is one implementation; a
 * fake source is used in host tests to inject read/close failures and to
 * cover the bounded enumeration contract without needing a ContentResolver.
 */
public interface ModelSource {
    /** Bounded enumeration. Must return at most maxEntries (the SAF implementation
     * also rejects directories with more than 10 000 children). Names not in
     * the supplied expected set are filtered out, and duplicates are rejected
     * so the caller can fail closed on hostile directory contents.
     */
    Map<String, UriRef> enumerate(java.util.Collection<String> expected, int maxEntries) throws IOException;

    /** Compatibility overload. Adapters can additionally checkpoint each query row. */
    default Map<String, UriRef> enumerate(java.util.Collection<String> expected, int maxEntries,
                                         ModelRepository.CancelGate cancel) throws IOException {
        if (cancel.cancelled()) throw new ModelRepository.CancelledException();
        Map<String, UriRef> refs = enumerate(expected, maxEntries);
        if (cancel.cancelled()) throw new ModelRepository.CancelledException();
        return refs;
    }

    /** Opens a single entry. Returning null or throwing both signal failure;
     * the repository treats both as fail-closed.
     */
    InputStream open(UriRef ref) throws IOException;

    /** Opaque handle. SAF uses android.net.Uri; tests use a String tag. The
     * repository does not inspect the handle, it only forwards it back to open().
     */
    final class UriRef {
        private final Object handle;
        public UriRef(Object handle) { this.handle = handle; }
        public Object handle() { return handle; }
        @Override public String toString() { return String.valueOf(handle); }
    }
}
