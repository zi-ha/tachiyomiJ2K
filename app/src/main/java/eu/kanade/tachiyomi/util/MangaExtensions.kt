package eu.kanade.tachiyomi.util

import android.app.Activity
import android.content.Context
import android.view.View
import com.bluelinelabs.conductor.Controller
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.category.addtolibrary.SetCategoriesSheet
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import java.util.Date
import java.util.Locale

fun Manga.isLocal() = source == LocalSource.ID

fun Manga.shouldDownloadNewChapters(
    db: DatabaseHelper,
    prefs: PreferencesHelper,
): Boolean = false

fun Manga.moveCategories(
    db: DatabaseHelper,
    activity: Activity,
    onMangaMoved: () -> Unit,
) {
    moveCategories(db, activity, false, onMangaMoved)
}

fun Manga.moveCategories(
    db: DatabaseHelper,
    activity: Activity,
    addingToLibrary: Boolean,
    onMangaMoved: () -> Unit,
) {
    val categories = db.getCategories().executeAsBlocking()
    val categoriesForManga = db.getCategoriesForManga(this).executeAsBlocking()
    val ids = categoriesForManga.mapNotNull { it.id }.toTypedArray()
    SetCategoriesSheet(
        activity,
        this,
        categories.toMutableList(),
        ids,
        addingToLibrary,
    ) {
        onMangaMoved()
    }.show()
}

fun List<Manga>.moveCategories(
    db: DatabaseHelper,
    activity: Activity,
    onMangaMoved: () -> Unit,
) {
    if (this.isEmpty()) return
    val categories = db.getCategories().executeAsBlocking()
    val commonCategories =
        map { db.getCategoriesForManga(it).executeAsBlocking() }
            .reduce { set1: Iterable<Category>, set2 -> set1.intersect(set2).toMutableList() }
            .toTypedArray()
    val mangaCategories = map { db.getCategoriesForManga(it).executeAsBlocking() }
    val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2).toMutableList() }
    val mixedCategories =
        mangaCategories
            .flatten()
            .distinct()
            .subtract(common)
            .toMutableList()
    SetCategoriesSheet(
        activity,
        this,
        categories.toMutableList(),
        categories
            .map {
                when (it) {
                    in commonCategories -> TriStateCheckBox.State.CHECKED
                    in mixedCategories -> TriStateCheckBox.State.IGNORE
                    else -> TriStateCheckBox.State.UNCHECKED
                }
            }.toTypedArray(),
        false,
    ) {
        onMangaMoved()
    }.show()
}

fun Manga.addOrRemoveToFavorites(
    db: DatabaseHelper,
    preferences: PreferencesHelper,
    view: View,
    activity: Activity,
    sourceManager: SourceManager,
    controller: Controller,
    checkForDupes: Boolean = true,
    onMangaAdded: (Pair<Long, Boolean>?) -> Unit,
    onMangaMoved: () -> Unit,
    onMangaDeleted: () -> Unit,
): Snackbar? {
    if (!favorite) {
        // Removed duplicate check for local reader

        val categories = db.getCategories().executeAsBlocking()
        val defaultCategoryId = preferences.defaultCategory()
        val defaultCategory = categories.find { it.id == defaultCategoryId }
        val lastUsedCategories =
            Category.lastCategoriesAddedTo.mapNotNull { catId ->
                categories.find { it.id == catId }
            }
        when {
            defaultCategory != null -> {
                favorite = true
                date_added = Date().time
                db.insertManga(this).executeAsBlocking()
                val mc = MangaCategory.create(this, defaultCategory)
                db.setMangaCategories(listOf(mc), listOf(this))
                (activity as? MainActivity)?.showNotificationPermissionPrompt()
                onMangaMoved()
                return view.snack(activity.getString(R.string.added_to_, defaultCategory.name)) {
                    setAction(R.string.change) {
                        moveCategories(db, activity, onMangaMoved)
                    }
                }
            }
            defaultCategoryId == -2 &&
                (
                    lastUsedCategories.isNotEmpty() ||
                        Category.lastCategoriesAddedTo.firstOrNull() == 0
                ) -> { // last used category(s)
                favorite = true
                date_added = Date().time
                db.insertManga(this).executeAsBlocking()
                db.setMangaCategories(
                    lastUsedCategories.map { MangaCategory.create(this, it) },
                    listOf(this),
                )
                (activity as? MainActivity)?.showNotificationPermissionPrompt()
                onMangaMoved()
                return view.snack(
                    activity.getString(
                        R.string.added_to_,
                        when (lastUsedCategories.size) {
                            0 -> activity.getString(R.string.default_category).lowercase(Locale.ROOT)
                            1 -> lastUsedCategories.firstOrNull()?.name ?: ""
                            else ->
                                activity.resources.getQuantityString(
                                    R.plurals.category_plural,
                                    lastUsedCategories.size,
                                    lastUsedCategories.size,
                                )
                        },
                    ),
                ) {
                    setAction(R.string.change) {
                        moveCategories(db, activity, onMangaMoved)
                    }
                }
            }
            defaultCategoryId == 0 || categories.isEmpty() -> { // 'Default' or no category
                favorite = true
                date_added = Date().time
                db.insertManga(this).executeAsBlocking()
                db.setMangaCategories(emptyList(), listOf(this))
                onMangaMoved()
                (activity as? MainActivity)?.showNotificationPermissionPrompt()
                return if (categories.isNotEmpty()) {
                    view.snack(activity.getString(R.string.added_to_, activity.getString(R.string.default_value))) {
                        setAction(R.string.change) {
                            moveCategories(db, activity, onMangaMoved)
                        }
                    }
                } else {
                    view.snack(R.string.added_to_library)
                }
            }
            else -> { // Always ask
                showSetCategoriesSheet(db, activity, categories, onMangaAdded, onMangaMoved)
            }
        }
    } else {
        val lastAddedDate = date_added
        favorite = false
        date_added = 0
        db.insertManga(this).executeAsBlocking()
        onMangaMoved()
        return view.snack(view.context.getString(R.string.removed_from_library), Snackbar.LENGTH_INDEFINITE) {
            setAction(R.string.undo) {
                favorite = true
                date_added = lastAddedDate
                db.insertManga(this@addOrRemoveToFavorites).executeAsBlocking()
                onMangaMoved()
            }
            addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        super.onDismissed(transientBottomBar, event)
                        if (!favorite) {
                            onMangaDeleted()
                        }
                    }
                },
            )
        }
    }
    return null
}

private fun Manga.showSetCategoriesSheet(
    db: DatabaseHelper,
    activity: Activity,
    categories: List<Category>,
    onMangaAdded: (Pair<Long, Boolean>?) -> Unit,
    onMangaMoved: () -> Unit,
) {
    val categoriesForManga = db.getCategoriesForManga(this).executeAsBlocking()
    val ids = categoriesForManga.mapNotNull { it.id }.toTypedArray()

    SetCategoriesSheet(
        activity,
        this,
        categories.toMutableList(),
        ids,
        true,
    ) {
        (activity as? MainActivity)?.showNotificationPermissionPrompt()
        onMangaAdded(null)
    }.show()
}

fun Manga.autoAddTrack(
    db: DatabaseHelper,
    onMangaMoved: () -> Unit,
) {
    // No-op
}

fun Context.mapStatus(status: Int): String =
    getString(
        when (status) {
            SManga.ONGOING -> R.string.ongoing
            SManga.COMPLETED -> R.string.completed
            SManga.LICENSED -> R.string.licensed
            SManga.PUBLISHING_FINISHED -> R.string.publishing_finished
            SManga.CANCELLED -> R.string.cancelled
            SManga.ON_HIATUS -> R.string.on_hiatus
            else -> R.string.unknown
        },
    )

fun Context.mapSeriesType(seriesType: Int): String =
    getString(
        when (seriesType) {
            Manga.TYPE_MANGA -> R.string.manga
            Manga.TYPE_MANHWA -> R.string.manhwa
            Manga.TYPE_MANHUA -> R.string.manhua
            Manga.TYPE_COMIC -> R.string.comic
            Manga.TYPE_WEBTOON -> R.string.webtoon
            else -> R.string.unknown
        },
    )
