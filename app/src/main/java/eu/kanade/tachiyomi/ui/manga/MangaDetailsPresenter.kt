package eu.kanade.tachiyomi.ui.manga

import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.trimOrNull
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date
import java.util.Locale

class MangaDetailsPresenter(
    val manga: Manga,
    val source: Source,
    val preferences: PreferencesHelper = Injekt.get(),
    val coverCache: CoverCache = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    chapterFilter: ChapterFilter = Injekt.get(),
) : BaseCoroutinePresenter<MangaDetailsController>() {
    private val customMangaManager: CustomMangaManager by injectLazy()
    private val mangaShortcutManager: MangaShortcutManager by injectLazy()
    val sourceManager: SourceManager by injectLazy()

    private val chapterSort = ChapterSort(manga, chapterFilter, preferences)

    var isLockedFromSearch = false
    var hasRequested = false
    var isLoading = false
    var scrollType = 0

    var chapters: List<ChapterItem> = emptyList()
        private set

    var allChapters: List<ChapterItem> = emptyList()
        private set

    var allHistory: List<History> = emptyList()
        private set

    val headerItem by lazy { MangaHeaderItem(manga, view?.fromCatalogue == true) }
    var tabletChapterHeaderItem: MangaHeaderItem? = null
    var allChapterScanlators: Set<String> = emptySet()

    fun onFirstLoad() {
        val controller = view ?: return
        headerItem.isTablet = controller.isTablet
        if (controller.isTablet) {
            tabletChapterHeaderItem = MangaHeaderItem(manga, false)
            tabletChapterHeaderItem?.isChapterHeader = true
        }
        isLockedFromSearch =
            controller.shouldLockIfNeeded &&
            SecureActivityDelegate.shouldBeLocked()
        headerItem.isLocked = isLockedFromSearch

        if (manga.isLocal()) {
            refreshAll()
        } else {
            runBlocking { getChapters() }
            controller.updateChapters(this.chapters)
            getHistory()
        }
    }

    fun fetchChapters() {
        presenterScope.launch {
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
            getHistory()
        }
    }

    suspend fun getChaptersNow(): List<ChapterItem> {
        getChapters()
        return chapters
    }

    private suspend fun getChapters() {
        val chapters = db.getChapters(manga).executeOnIO().map { it.toModel() }
        allChapterScanlators = chapters.flatMap { ChapterUtil.getScanlators(it.chapter.scanlator) }.toSet()
        allChapters = chapters
        this.chapters = applyChapterFilters(chapters)
    }

    private fun getHistory() {
        presenterScope.launchIO {
            allHistory = manga.id?.let { db.getHistoryByMangaId(it).executeAsBlocking() }.orEmpty()
        }
    }

    private fun Chapter.toModel(): ChapterItem {
        val model = ChapterItem(this, manga)
        model.isLocked = isLockedFromSearch
        return model
    }

    fun sortDescending() = manga.sortDescending(preferences)

    fun sortingOrder() = manga.chapterOrder(preferences)

    private fun applyChapterFilters(chapterList: List<ChapterItem>): List<ChapterItem> {
        if (isLockedFromSearch) {
            return chapterList
        }
        getScrollType(chapterList)
        return chapterSort.getChaptersSorted(chapterList)
    }

    fun getChapterUrl(chapter: Chapter): String? = null

    private fun getScrollType(chapters: List<ChapterItem>) {
        scrollType =
            when {
                ChapterUtil.hasMultipleVolumes(chapters) -> MULTIPLE_VOLUMES
                ChapterUtil.hasMultipleSeasons(chapters) -> MULTIPLE_SEASONS
                ChapterUtil.hasTensOfChapters(chapters) -> TENS_OF_CHAPTERS
                else -> 0
            }
    }

    fun getNextUnreadChapter(): ChapterItem? = chapterSort.getNextUnreadChapter(chapters)

    fun anyRead(): Boolean = allChapters.any { it.read }

    fun hasBookmark(): Boolean = allChapters.any { it.bookmark }

    fun getUnreadChaptersSorted() =
        allChapters
            .filter { !it.read }
            .distinctBy { it.name }
            .sortedWith(chapterSort.sortComparator(true))

    fun deleteChapter(chapter: ChapterItem) {
    }

    fun deleteChapters(
        chapters: List<ChapterItem>,
        update: Boolean = true,
        isEverything: Boolean = false,
    ) {
    }

    fun refreshMangaFromDb(): Manga {
        val dbManga = db.getManga(manga.id!!).executeAsBlocking()
        manga.copyFrom(dbManga!!)
        return dbManga
    }

    fun refreshAll() {
        if (!manga.isLocal()) return
        presenterScope.launch {
            // Local refresh logic if needed
        }
    }

    fun bookmarkChapters(
        selectedChapters: List<ChapterItem>,
        bookmarked: Boolean,
    ) {
        presenterScope.launch(Dispatchers.IO) {
            selectedChapters.forEach {
                it.bookmark = bookmarked
            }
            db.updateChaptersProgress(selectedChapters).executeAsBlocking()
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
        }
    }

    fun markChaptersRead(
        selectedChapters: List<ChapterItem>,
        read: Boolean,
        deleteNow: Boolean = true,
        lastRead: Int? = null,
        pagesLeft: Int? = null,
    ) {
        presenterScope.launch(Dispatchers.IO) {
            selectedChapters.forEach {
                it.read = read
                if (!read) {
                    it.last_page_read = lastRead ?: 0
                    it.pages_left = pagesLeft ?: 0
                }
            }
            db.updateChaptersProgress(selectedChapters).executeAsBlocking()
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
        }
    }

    fun setSortOrder(
        sort: Int,
        descend: Boolean,
    ) {
        manga.setChapterOrder(sort, if (descend) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
        if (mangaSortMatchesDefault()) {
            manga.setSortToGlobal()
        }
        asyncUpdateMangaAndChapters()
    }

    fun setGlobalChapterSort(
        sort: Int,
        descend: Boolean,
    ) {
        preferences.sortChapterOrder().set(sort)
        preferences.chaptersDescAsDefault().set(descend)
        manga.setSortToGlobal()
        asyncUpdateMangaAndChapters()
    }

    fun mangaSortMatchesDefault(): Boolean =
        (
            manga.sortDescending == preferences.chaptersDescAsDefault().get() &&
                manga.sorting == preferences.sortChapterOrder().get()
        ) ||
            !manga.usesLocalSort

    fun mangaFilterMatchesDefault(): Boolean =
        (
            manga.readFilter == preferences.filterChapterByRead().get() &&
                manga.downloadedFilter == preferences.filterChapterByDownloaded().get() &&
                manga.bookmarkedFilter == preferences.filterChapterByBookmarked().get() &&
                manga.hideChapterTitles == preferences.hideChapterTitlesByDefault().get()
        ) ||
            !manga.usesLocalFilter

    fun resetSortingToDefault() {
        manga.setSortToGlobal()
        asyncUpdateMangaAndChapters()
    }

    fun setFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        manga.readFilter =
            when (unread) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_UNREAD
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_READ
                else -> Manga.SHOW_ALL
            }
        manga.downloadedFilter = Manga.SHOW_ALL
        manga.bookmarkedFilter =
            when (bookmarked) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_BOOKMARKED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
                else -> Manga.SHOW_ALL
            }
        manga.setFilterToLocal()
        if (mangaFilterMatchesDefault()) {
            manga.setFilterToGlobal()
        }
        asyncUpdateMangaAndChapters()
    }

    fun hideTitle(hide: Boolean) {
        manga.displayMode = if (hide) Manga.CHAPTER_DISPLAY_NUMBER else Manga.CHAPTER_DISPLAY_NAME
        manga.setFilterToLocal()
        db.updateChapterFlags(manga).executeAsBlocking()
        if (mangaFilterMatchesDefault()) {
            manga.setFilterToGlobal()
        }
        view?.refreshAdapter()
    }

    fun resetFilterToDefault() {
        manga.setFilterToGlobal()
        asyncUpdateMangaAndChapters()
    }

    fun setGlobalChapterFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        preferences.filterChapterByRead().set(
            when (unread) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_UNREAD
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_READ
                else -> Manga.SHOW_ALL
            },
        )
        preferences.filterChapterByBookmarked().set(
            when (bookmarked) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_BOOKMARKED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
                else -> Manga.SHOW_ALL
            },
        )
        preferences.hideChapterTitlesByDefault().set(manga.hideChapterTitles)
        manga.setFilterToGlobal()
        asyncUpdateMangaAndChapters()
    }

    private fun asyncUpdateMangaAndChapters(justChapters: Boolean = false) {
        presenterScope.launch {
            if (!justChapters) db.updateChapterFlags(manga).executeOnIO()
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
        }
    }

    fun currentFilters(): String {
        val filtersId = mutableListOf<Int?>()
        filtersId.add(if (manga.readFilter(preferences) == Manga.CHAPTER_SHOW_READ) R.string.read else null)
        filtersId.add(if (manga.readFilter(preferences) == Manga.CHAPTER_SHOW_UNREAD) R.string.unread else null)
        filtersId.add(if (manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_BOOKMARKED) R.string.bookmarked else null)
        filtersId.add(if (manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_BOOKMARKED) R.string.not_bookmarked else null)
        filtersId.add(if (manga.filtered_scanlators?.isNotEmpty() == true) R.string.scanlators else null)
        return filtersId
            .filterNotNull()
            .joinToString(", ") { view?.view?.context?.getString(it) ?: "" }
    }

    fun setScanlatorFilter(filteredScanlators: Set<String>) {
        val manga = manga
        manga.filtered_scanlators =
            if (filteredScanlators.size == allChapterScanlators.size ||
                filteredScanlators.isEmpty()
            ) {
                null
            } else {
                ChapterUtil.getScanlatorString(filteredScanlators)
            }
        db.updateMangaFilteredScanlators(manga).executeAsBlocking()
        asyncUpdateMangaAndChapters()
    }

    fun toggleFavorite(): Boolean {
        manga.favorite = !manga.favorite

        when (manga.favorite) {
            true -> {
                manga.date_added = Date().time
            }
            false -> manga.date_added = 0
        }

        db.insertManga(manga).executeAsBlocking()
        view?.updateHeader()
        return manga.favorite
    }

    fun getCategories(): List<Category> = db.getCategories().executeAsBlocking()

    fun confirmDeletion() {
        launchIO {
            coverCache.deleteFromCache(manga)
            customMangaManager.saveMangaInfo(CustomMangaManager.MangaJson(manga.id!!))
            asyncUpdateMangaAndChapters(true)
        }
    }

    private fun onUpdateManga(mangaId: Long?) = fetchChapters()

    private fun saveImage(
        cover: Bitmap,
        directory: File,
        manga: Manga,
    ): File? {
        directory.mkdirs()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title} - Cover.jpg")

        val destFile = File(directory, filename)
        val stream: OutputStream = FileOutputStream(destFile)
        cover.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        stream.flush()
        stream.close()
        return destFile
    }

    fun updateManga(
        title: String?,
        author: String?,
        artist: String?,
        uri: Uri?,
        description: String?,
        tags: Array<String>?,
        status: Int?,
        seriesType: Int?,
        lang: String?,
        resetCover: Boolean = false,
    ) {
        if (manga.isLocal()) {
            manga.title = if (title.isNullOrBlank()) manga.url else title.trim()
            manga.author = author?.trimOrNull()
            manga.artist = artist?.trimOrNull()
            manga.description = description?.trimOrNull()
            val tagsString =
                tags?.joinToString(", ") { tag ->
                    tag.replaceFirstChar {
                        it.uppercase(Locale.getDefault())
                    }
                }
            manga.genre = if (tags.isNullOrEmpty()) null else tagsString?.trim()
            if (seriesType != null) {
                manga.genre =
                    setSeriesType(seriesType, manga.genre).joinToString(", ") {
                        it.replaceFirstChar { genre ->
                            genre.titlecase(Locale.getDefault())
                        }
                    }
                manga.viewer_flags = -1
                db.updateViewerFlags(manga).executeAsBlocking()
            }
            manga.status = status ?: SManga.UNKNOWN
            LocalSource(preferences.context).updateMangaInfo(manga, lang)
            db.updateMangaInfo(manga).executeAsBlocking()
        } else {
            var genre =
                if (!tags.isNullOrEmpty() && tags.joinToString(", ") != manga.originalGenre) {
                    tags
                        .map { tag -> tag.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
                        .toTypedArray()
                } else {
                    null
                }
            if (seriesType != null) {
                genre = setSeriesType(seriesType, genre?.joinToString(", "))
                manga.viewer_flags = -1
                db.updateViewerFlags(manga).executeAsBlocking()
            }
            val manga =
                CustomMangaManager.MangaJson(
                    manga.id!!,
                    title?.trimOrNull(),
                    author?.trimOrNull(),
                    artist?.trimOrNull(),
                    description?.trimOrNull(),
                    genre,
                    if (status != this.manga.originalStatus) status else null,
                )
            customMangaManager.saveMangaInfo(manga)
        }
        if (uri != null) {
            editCoverWithStream(uri)
        } else if (resetCover) {
            coverCache.deleteCustomCover(manga)
            view?.setPaletteColor()
        }
        view?.updateHeader()
    }

    private fun setSeriesType(
        seriesType: Int,
        genres: String? = null,
    ): Array<String> {
        val tags = (genres ?: manga.genre)?.split(",")?.map { it.trim() }?.toMutableList() ?: mutableListOf()
        tags.removeAll { manga.isSeriesTag(it) }
        when (seriesType) {
            Manga.TYPE_MANGA -> tags.add("Manga")
            Manga.TYPE_MANHUA -> tags.add("Manhua")
            Manga.TYPE_MANHWA -> tags.add("Manhwa")
            Manga.TYPE_COMIC -> tags.add("Comic")
            Manga.TYPE_WEBTOON -> tags.add("Webtoon")
        }
        return tags.toTypedArray()
    }

    fun editCoverWithStream(uri: Uri): Boolean {
        val inputStream =
            preferences.context.contentResolver.openInputStream(uri) ?: return false
        if (manga.isLocal()) {
            LocalSource.updateCover(preferences.context, manga, inputStream)
            view?.setPaletteColor()
            return true
        }

        if (manga.favorite) {
            coverCache.setCustomCoverToCache(manga, inputStream)
            view?.setPaletteColor()
            return true
        }
        return false
    }

    fun saveCover(): Boolean =
        try {
            val directory =
                if (preferences.folderPerManga().get()) {
                    val baseDir =
                        Environment.getExternalStorageDirectory().absolutePath +
                            File.separator + Environment.DIRECTORY_PICTURES +
                            File.separator + preferences.context.getString(R.string.app_name)

                    File(baseDir + File.separator + DiskUtil.buildValidFilename(manga.title))
                } else {
                    File(
                        Environment.getExternalStorageDirectory().absolutePath +
                            File.separator + Environment.DIRECTORY_PICTURES +
                            File.separator + preferences.context.getString(R.string.app_name),
                    )
                }
            val file = saveCover(directory)
            DiskUtil.scanMedia(preferences.context, file)
            true
        } catch (e: Exception) {
            false
        }

    private fun saveCover(directory: File): File {
        val cover = coverCache.getCustomCoverFile(manga).takeIf { it.exists() } ?: coverCache.getCoverFile(manga)
        val type =
            ImageUtil.findImageType(cover.inputStream())
                ?: throw Exception("Not an image")

        directory.mkdirs()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title}.${type.extension}")

        val destFile = File(directory, filename)
        cover.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    fun isTracked(): Boolean = false

    fun hasTrackers(): Boolean = false

    companion object {
        const val MULTIPLE_VOLUMES = 1
        const val TENS_OF_CHAPTERS = 2
        const val MULTIPLE_SEASONS = 3
    }
}
