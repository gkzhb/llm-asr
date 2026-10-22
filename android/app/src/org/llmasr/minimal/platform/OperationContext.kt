package org.llmasr.minimal.platform
import android.content.ContentResolver
import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
interface OperationContext {
    fun filesDir(): File?
    fun cacheDir(): File?
    @Throws(IOException::class) fun openAsset(name: String?): InputStream?
    fun contentResolver(): ContentResolver?
    class Android(appContext: Context?) : OperationContext {
        private val appContext: Context
        init {
            if(appContext==null)throw IllegalArgumentException("appContext required")
            val ac=appContext.getApplicationContext()
            if(ac==null)throw IllegalArgumentException("applicationContext required")
            this.appContext=ac
        }
        @Suppress("UNCHECKED_CAST") private fun <T> unchanged(value: Any?): T = value as T
        override fun filesDir(): File? = appContext.getFilesDir()
        override fun cacheDir(): File? = appContext.getCacheDir()
        @Throws(IOException::class) override fun openAsset(name: String?): InputStream? = appContext.getAssets().open(unchanged<String>(name))
        override fun contentResolver(): ContentResolver? = appContext.getContentResolver()
    }
}
