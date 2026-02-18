package eu.kanade.tachiyomi

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.plusAssign
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
import eu.kanade.tachiyomi.ui.library.LibrarySort
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.recents.RecentsPresenter
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.coroutines.CoroutineScope
import java.io.File
import kotlin.math.max

object Migrations {
    /**
     * Performs a migration when the application is updated.
     *
     * @param preferences Preferences of the application.
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(
        preferences: PreferencesHelper,
        preferenceStore: PreferenceStore,
        scope: CoroutineScope,
    ): Boolean {
        val context = preferences.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val oldVersion = preferences.lastVersionCode().get()
        if (oldVersion < BuildConfig.VERSION_CODE) {
            preferences.lastVersionCode().set(BuildConfig.VERSION_CODE)

            // Always set up background tasks to ensure they're running
            BackupCreatorJob.setupTask(context)

            if (oldVersion == 0) {
                return BuildConfig.DEBUG
            }

            if (oldVersion < 15) {
                // Delete internal chapter cache dir.
                File(context.cacheDir, "chapter_disk_cache").deleteRecursively()
            }
            if (oldVersion < 19) {
                // Move covers to external files dir.
                val oldDir = File(context.externalCacheDir, "cover_disk_cache")
                if (oldDir.exists()) {
                    val destDir = context.getExternalFilesDir("covers")
                    if (destDir != null) {
                        oldDir.listFiles()?.forEach {
                            it.renameTo(File(destDir, it.name))
                        }
                    }
                }
            }
            if (oldVersion < 26) {
                // Delete external chapter cache dir.
                val extCache = context.externalCacheDir
                if (extCache != null) {
                    val chapterCache = File(extCache, "chapter_disk_cache")
                    if (chapterCache.exists()) {
                        chapterCache.deleteRecursively()
                    }
                }
            }
            if (oldVersion < 62) {
                LibraryPresenter.updateDB()
                BackupCreatorJob.setupTask(context)
            }
            if (oldVersion < 66) {
                LibraryPresenter.updateCustoms()
            }

            if (oldVersion < 73) {
                // Reset rotation to Free after replacing Lock
                if (prefs.contains("pref_rotation_type_key")) {
                    prefs.edit {
                        putInt("pref_rotation_type_key", 1)
                    }
                }
            }

            if (oldVersion < 77) {
                // Migrate Rotation and Viewer values to default values for viewer_flags
                val newOrientation =
                    when (prefs.getInt("pref_rotation_type_key", 1)) {
                        1 -> OrientationType.FREE.flagValue
                        2 -> OrientationType.PORTRAIT.flagValue
                        3 -> OrientationType.LANDSCAPE.flagValue
                        4 -> OrientationType.LOCKED_PORTRAIT.flagValue
                        5 -> OrientationType.LOCKED_LANDSCAPE.flagValue
                        else -> OrientationType.FREE.flagValue
                    }

                // Reading mode flag and prefValue is the same value
                val newReadingMode = prefs.getInt("pref_default_viewer_key", 1)

                prefs.edit {
                    putInt("pref_default_orientation_type_key", newOrientation)
                    remove("pref_rotation_type_key")
                    putInt("pref_default_reading_mode_key", newReadingMode)
                    remove("pref_default_viewer_key")
                }
            }
            if (oldVersion < 83) {
                if (preferences.enabledLanguages().isSet()) {
                    preferences.enabledLanguages() += "all"
                }
            }

            if (oldVersion < 88) {
                scope.launchIO {
                    LibraryPresenter.updateRatiosAndColors()
                }
                val oldReaderTap = prefs.getBoolean("reader_tap", true)
                if (!oldReaderTap) {
                    preferences.navigationModePager().set(5)
                    preferences.navigationModeWebtoon().set(5)
                }
            }
            if (oldVersion < 90) {
                val oldSecureScreen = prefs.getBoolean("secure_screen", false)
                if (oldSecureScreen) {
                    preferences.secureScreen().set(PreferenceValues.SecureScreenMode.ALWAYS)
                }
            }
            if (oldVersion < 97) {
                val oldDLAfterReading = prefs.getInt("auto_download_after_reading", 0)
                if (oldDLAfterReading > 0) {
                    preferences.autoDownloadWhileReading().set(max(2, oldDLAfterReading))
                }
            }
            if (oldVersion < 102) {
                val oldGroupHistory = prefs.getBoolean("group_chapters_history", true)
                if (!oldGroupHistory) {
                    preferences.groupChaptersHistory().set(RecentsPresenter.GroupType.Never)
                }
            }

            if (oldVersion < 110) {
                try {
                    val librarySortString = prefs.getString("library_sorting_mode", "")
                    if (!librarySortString.isNullOrEmpty()) {
                        prefs.edit {
                            remove("library_sorting_mode")
                            putInt(
                                "library_sorting_mode",
                                LibrarySort.deserialize(librarySortString).mainValue,
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
            if (oldVersion < 111) {
                prefs.edit {
                    remove("trusted_signatures")
                }
            }

            return true
        }
        return false
    }
}
