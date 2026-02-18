package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabaseController
import eu.kanade.tachiyomi.ui.setting.debug.DebugController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.openInBrowser
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File

class SettingsAdvancedController : SettingsController() {
    private val chapterCache: ChapterCache by injectLazy()
    private val db: DatabaseHelper by injectLazy()
    private val coverCache: CoverCache by injectLazy()

    @SuppressLint("BatteryLife")
    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.advanced

            preference {
                key = "dump_crash_logs"
                titleRes = R.string.dump_crash_logs
                summaryRes = R.string.saves_error_logs

                onClick {
                    CrashLogUtil(context.localeContext).dumpLogs()
                }
            }

            preference {
                key = "debug_info"
                titleRes = R.string.pref_debug_info

                onClick {
                    router.pushController(DebugController().withFadeTransaction())
                }
            }

            preferenceCategory {
                titleRes = R.string.label_background_activity

                preference {
                    key = "pref_dont_kill_my_app"
                    title = "Don't kill my app!"
                    summaryRes = R.string.about_dont_kill_my_app

                    onClick {
                        openInBrowser("https://dontkillmyapp.com/")
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.data_management
                preference {
                    key = CLEAR_CACHE_KEY
                    titleRes = R.string.clear_chapter_cache
                    summary = context.getString(R.string.used_, chapterCache.readableSize)

                    onClick { clearChapterCache() }
                }

                preference {
                    key = "clean_cached_covers"
                    titleRes = R.string.clean_up_cached_covers
                    summary =
                        context.getString(
                            R.string.delete_old_covers_in_library_used_,
                            coverCache.getChapterCacheSize(),
                        )

                    onClick {
                        context.toast(R.string.starting_cleanup)
                        (activity as? AppCompatActivity)?.lifecycleScope?.launchIO {
                            coverCache.deleteOldCovers()
                        }
                    }
                }

                preference {
                    key = "pref_clear_webview_data"
                    titleRes = R.string.pref_clear_webview_data

                    onClick { clearWebViewData() }
                }
                preference {
                    key = "clear_database"
                    titleRes = R.string.clear_database
                    summaryRes = R.string.clear_database_summary
                    onClick { router.pushController(ClearDatabaseController().withFadeTransaction()) }
                }
            }
        }

    private fun clearChapterCache() {
        if (activity == null) return
        viewScope.launchIO {
            val files = chapterCache.cacheDir.listFiles() ?: return@launchIO
            var deletedFiles = 0
            try {
                files.forEach { file ->
                    if (chapterCache.removeFileFromCache(file.name)) {
                        deletedFiles++
                    }
                }
                withUIContext {
                    activity?.toast(
                        resources?.getQuantityString(
                            R.plurals.cache_cleared,
                            deletedFiles,
                            deletedFiles,
                        ) ?: "Cache cleared",
                    )
                    findPreference(CLEAR_CACHE_KEY)?.summary =
                        resources?.getString(R.string.used_, chapterCache.readableSize)
                }
            } catch (_: Exception) {
                withUIContext {
                    activity?.toast(R.string.cache_delete_error)
                }
            }
        }
    }

    private fun clearWebViewData() {
        if (activity == null) return
        try {
            val webview = WebView(activity!!)
            webview.setDefaultSettings()
            webview.clearCache(true)
            webview.clearFormData()
            webview.clearHistory()
            webview.clearSslPreferences()
            WebStorage.getInstance().deleteAllData()
            activity?.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
            activity?.toast(R.string.webview_data_deleted)
        } catch (e: Throwable) {
            Timber.e(e)
            activity?.toast(R.string.cache_delete_error)
        }
    }

    private companion object {
        const val CLEAR_CACHE_KEY = "pref_clear_cache_key"
    }
}
