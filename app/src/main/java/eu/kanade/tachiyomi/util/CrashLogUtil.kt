package eu.kanade.tachiyomi.util

import android.content.Context
import android.net.Uri
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.toast
import java.io.IOException

class CrashLogUtil(
    private val context: Context,
) {
    fun dumpLogs() {
        try {
            val file = context.createFileInCacheDir("tachiyomi_crash_logs.txt")
            file.appendText(getDebugInfo() + "\n\n")
            Runtime.getRuntime().exec("logcat *:E -d -f ${file.absolutePath}")
            showNotification(file.getUriCompat(context))
        } catch (e: IOException) {
            context.toast("Failed to get logs")
        }
    }

    private fun getDebugInfo(): String =
        "App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, ${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE})\n" +
            "Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
            "Device Model: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Device Brand: ${Build.BRAND}"

    private fun showNotification(uri: Uri) {
        context.toast("Logs dumped to ${uri.path}")
    }
}
