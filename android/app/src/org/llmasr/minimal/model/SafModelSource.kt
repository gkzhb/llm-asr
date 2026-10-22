package org.llmasr.minimal.model
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.InputStream
import java.util.HashMap
import java.util.HashSet
class SafModelSource(private val resolver: ContentResolver?, private val tree: Uri?) : ModelSource {
    companion object {
        @Throws(IOException::class) private fun check(cancel: ModelRepository.CancelGate?) { ModelProviderBoundary.checkpoint(cancel) }
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(IOException::class) override open fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int): MutableMap<String?, ModelSource.UriRef?>? = enumerate(expected,maxEntries,ModelRepository.NEVER_CANCEL)
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Throws(IOException::class) override open fun enumerate(expected: MutableCollection<String?>?, maxEntries: Int, cancel: ModelRepository.CancelGate?): MutableMap<String?, ModelSource.UriRef?>? {
        val children=HashMap<String?,ModelSource.UriRef?>()
        val ids=HashSet<String?>()
        check(cancel)
        val list=ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)) }
        check(cancel)
        val cursor=ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { resolver!!.query(list,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS),null,null,null) }
        val closing=java.io.Closeable { if(cursor!=null)ModelProviderBoundary.call<Any?>(ModelProviderBoundary.DIRECTORY_FAILURE) { cursor.close();null } }
        var primary: Throwable?=null
        try {
            check(cancel)
            if(cursor==null)throw IOException("无法列出所选目录，请重新选择本地模型目录")
            var seen=0
            while(true) {
                check(cancel)
                val more=ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { cursor.moveToNext() }
                check(cancel)
                if(!more)break
                if(++seen>Math.min(10000,maxEntries))throw IOException("目录条目过多，请选择专用模型目录")
                val name=ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { cursor.getString(1) }
                if(name==null || !expected!!.contains(name))continue
                val id=ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { cursor.getString(0) }
                val mime=ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { cursor.getString(2) }
                val nullFlags=ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { cursor.isNull(3) }
                val flags=ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { cursor.getLong(3) }
                if(id==null || id.isEmpty() || mime==null || nullFlags || DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) || (flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT.toLong()) != 0L)throw IOException("受管文件不是可读取的普通文件："+name)
                if(!ids.add(id) || children.containsKey(name))throw IOException("重复模型文件："+name)
                children[name]=ModelSource.UriRef(ModelProviderBoundary.call(ModelProviderBoundary.DIRECTORY_FAILURE) { DocumentsContract.buildDocumentUriUsingTree(tree,id) })
                check(cancel)
            }
        } catch(failure: Throwable) { primary=failure;throw failure }
        finally {
            if(primary==null)closing.close()
            else try { closing.close() } catch(closeFailure: Throwable) { (primary as java.lang.Throwable).addSuppressed(closeFailure) }
        }
        check(cancel)
        return children
    }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Throws(IOException::class) override open fun open(ref: ModelSource.UriRef?): InputStream? {
        if(ref!!.handle() !is Uri)throw IOException(ModelProviderBoundary.FILE_FAILURE)
        return ModelProviderBoundary.open { resolver!!.openInputStream(ref.handle() as Uri) }
    }
}
