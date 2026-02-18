package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.library.display.TabbedLibraryDisplaySheet
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsLibraryController : SettingsController() {
    private val db: DatabaseHelper = Injekt.get()

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.library
            preferenceCategory {
                titleRes = R.string.general
                switchPreference {
                    key = Keys.removeArticles
                    titleRes = R.string.sort_by_ignoring_articles
                    summaryRes = R.string.when_sorting_ignore_articles
                    defaultValue = false
                }

                switchPreference {
                    key = Keys.showLibrarySearchSuggestions
                    titleRes = R.string.search_suggestions
                    summaryRes = R.string.search_tips_show_periodically

                    onChange {
                        it as Boolean
                        if (it) {
                            // launchIO { LibraryPresenter.setSearchSuggestion(preferences, db, Injekt.get()) }
                        } else {
                            // DelayedLibrarySuggestionsJob.setupTask(context, false)
                            preferences.librarySearchSuggestion().set("")
                        }
                        true
                    }
                }

                preference {
                    key = "library_display_options"
                    isPersistent = false
                    titleRes = R.string.display_options
                    summaryRes = R.string.can_be_found_in_library_filters

                    onClick {
                        TabbedLibraryDisplaySheet(this@SettingsLibraryController).show()
                    }
                }
            }

            val dbCategories = db.getCategories().executeAsBlocking()

            preferenceCategory {
                titleRes = R.string.categories
                preference {
                    key = "edit_categories"
                    isPersistent = false
                    val catCount = db.getCategories().executeAsBlocking().size
                    titleRes = if (catCount > 0) R.string.edit_categories else R.string.add_categories
                    if (catCount > 0) summary = context.resources.getQuantityString(R.plurals.category_plural, catCount, catCount)
                    onClick { router.pushController(CategoryController().withFadeTransaction()) }
                }
                intListPreference(activity) {
                    key = Keys.defaultCategory
                    titleRes = R.string.default_category

                    val categories = listOf(Category.createDefault(context)) + dbCategories
                    entries =
                        listOf(context.getString(R.string.last_used), context.getString(R.string.always_ask)) +
                        categories.map { it.name }.toTypedArray()
                    entryValues = listOf(-2, -1) + categories.mapNotNull { it.id }.toList()
                    defaultValue = "-2"

                    val categoryName: (Int) -> String = { catId ->
                        when (catId) {
                            -2 -> context.getString(R.string.last_used)
                            -1 -> context.getString(R.string.always_ask)
                            else ->
                                categories.find { it.id == preferences.defaultCategory() }?.name
                                    ?: context.getString(R.string.last_used)
                        }
                    }
                    summary = categoryName(preferences.defaultCategory())
                    onChange { newValue ->
                        summary = categoryName(newValue as Int)
                        true
                    }
                }
            }
        }
}
