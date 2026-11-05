package org.llmasr.minimal.model

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.function.Supplier

/** SAF is a one-shot read capability, not a path or trust anchor. No provider metadata query. */
class SafModelZipSource(resolver: ContentResolver?, uri: Uri?, filesDir: Supplier<File>, policy: Supplier<FixedModelPackage>) :
    ModelZipSource(Opener {
        if (uri == null || uri.scheme != "content" || resolver == null) throw IOException(ModelProviderBoundary.FILE_FAILURE)
        ModelProviderBoundary.open { resolver.openInputStream(uri) }
    }, filesDir, policy)
