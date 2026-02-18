package eu.kanade.tachiyomi.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.add
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.titleRes
import eu.kanade.tachiyomi.util.view.openInBrowser
import eu.kanade.tachiyomi.util.view.snack
import java.text.DateFormat

class AboutController : SettingsController() {
    /**
     * Checks for new releases
     */
    private val dateFormat: DateFormat by lazy {
        preferences.dateFormat()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.about

            preference {
                key = "pref_whats_new"
                titleRes = R.string.whats_new_this_release
                onClick {
                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/Jays2Kings/tachiyomiJ2K/commits/master".toUri(),
                        )
                    startActivity(intent)
                }
            }
            preference {
                key = "pref_version"
                titleRes = R.string.version
                summary =
                    if (BuildConfig.DEBUG) {
                        "r" + BuildConfig.COMMIT_COUNT
                    } else {
                        BuildConfig.VERSION_NAME
                    }

                onClick {
                    activity?.let {
                        val deviceInfo = "" // CrashLogUtil(it.localeContext).getDebugInfo()
                        val clipboard = it.getSystemService<ClipboardManager>()!!
                        val appInfo = it.getString(R.string.app_info)
                        clipboard.setPrimaryClip(ClipData.newPlainText(appInfo, deviceInfo))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            view?.snack(context.getString(R.string._copied_to_clipboard, appInfo))
                        }
                    }
                }
            }
            preference {
                key = "pref_build_time"
                titleRes = R.string.build_time
                summary = getFormattedBuildTime(dateFormat)
            }

            preferenceCategory {
                preference {
                    key = "pref_about_help_translate"
                    titleRes = R.string.help_translate

                    onClick {
                        openInBrowser("https://hosted.weblate.org/projects/tachiyomi/tachiyomi-j2k/")
                    }
                }
                preference {
                    key = "pref_about_helpful_translation_links"
                    titleRes = R.string.helpful_translation_links

                    onClick {
                        openInBrowser("https://tachiyomi.org/help/contribution/#translation")
                    }
                }
            }
            add(AboutLinksPreference(context))
        }

    // class NewUpdateDialogController ... removed

    companion object {
        fun getFormattedBuildTime(dateFormat: DateFormat): String {
            return ""
//            try {
//                val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
//                inputDf.timeZone = TimeZone.getTimeZone("UTC")
//                val buildTime =
//                    inputDf.parse(BuildConfig.BUILD_TIME) ?: return BuildConfig.BUILD_TIME
//
//                return buildTime.toTimestampString(dateFormat)
//            } catch (e: ParseException) {
//                return BuildConfig.BUILD_TIME
//            }
        }
    }
}
