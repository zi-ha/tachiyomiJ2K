package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.annotation.ColorInt
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellable
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Date
import java.util.concurrent.CancellationException

class ReaderViewModel(
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val db: DatabaseHelper = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    val manga: Manga?
        get() = state.value.manga

    val source: Source?
        get() = manga?.source?.let { sourceManager.getOrStub(it) }

    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    private var loader: ChapterLoader? = null
    private var chapterReadStartTime: Long? = null
    private var finished = false

    private val chapterList by lazy {
        val manga = manga!!
        val dbChapters = db.getChapters(manga).executeAsBlocking()

        val selectedChapter =
            dbChapters.find { it.id == chapterId }
                ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader =
            chapterFilter.filterChaptersForReader(dbChapters, manga, selectedChapter)
        val chapterSort = ChapterSort(manga, chapterFilter, preferences)
        chaptersForReader.sortedWith(chapterSort.sortComparator(true)).map(::ReaderChapter)
    }

    private var chapterItems = emptyList<ReaderChapterItem>()
    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    init {
        var secondRun = false
        state
            .map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { currentChapter ->
                chapterId = currentChapter.chapter.id!!
                if (secondRun || !currentChapter.chapter.read) {
                    currentChapter.requestedPage = currentChapter.chapter.last_page_read
                }
                secondRun = true
            }.launchIn(viewModelScope)
    }

    fun onBackPressed() {
        if (finished) return
        finished = true
        val currentChapters = state.value.viewerChapters
        if (currentChapters != null) {
            currentChapters.unref()
            saveReadingProgress(currentChapters.currChapter)
        }
    }

    fun onSaveInstanceState() {
        val currentChapter = getCurrentChapter() ?: return
        saveChapterProgress(currentChapter)
    }

    fun needsInit(): Boolean = manga == null

    suspend fun init(
        mangaId: Long,
        initialChapterId: Long,
    ): Result<Boolean> {
        if (!needsInit()) return Result.success(true)
        return withIOContext {
            try {
                val manga = db.getManga(mangaId).executeAsBlocking()
                if (manga != null) {
                    mutableState.update { it.copy(manga = manga) }
                    if (chapterId == -1L) {
                        chapterId = initialChapterId
                    }

                    NotificationReceiver.dismissNotification(
                        preferences.context,
                        manga.id!!.hashCode(),
                        Notifications.ID_NEW_CHAPTERS,
                    )

                    val source = sourceManager.getOrStub(manga.source)
                    val context = Injekt.get<Application>()
                    loader = ChapterLoader(context, manga, source)

                    loadChapter(loader!!, chapterList.first { chapterId == it.chapter.id })
                    Result.success(true)
                } else {
                    Result.success(false)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                Result.failure(e)
            }
        }
    }

    suspend fun getChapters(): List<ReaderChapterItem> {
        val manga = manga ?: return emptyList()
        chapterItems =
            withContext(Dispatchers.IO) {
                val chapterSort = ChapterSort(manga, chapterFilter, preferences)
                val dbChapters = db.getChapters(manga).executeAsBlocking()
                chapterSort
                    .getChaptersSorted(
                        dbChapters,
                        filterForReader = true,
                        currentChapter = getCurrentChapter()?.chapter,
                    ).map {
                        ReaderChapterItem(
                            it,
                            manga,
                            it.id == (getCurrentChapter()?.chapter?.id ?: chapterId),
                        )
                    }
            }

        return chapterItems
    }

    private suspend fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return
        Timber.d("Loading ${chapter.chapter.url}")
        withIOContext {
            try {
                loadChapter(loader, chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                Timber.e(e)
            }
        }
    }

    private suspend fun loadChapter(
        loader: ChapterLoader,
        chapter: ReaderChapter,
    ): ViewerChapters {
        loader.loadChapter(chapter)

        val chapterPos = chapterList.indexOf(chapter)
        val newChapters =
            ViewerChapters(
                chapter,
                chapterList.getOrNull(chapterPos - 1),
                chapterList.getOrNull(chapterPos + 1),
            )

        withUIContext {
            mutableState.update {
                newChapters.ref()
                it.viewerChapters?.unref()
                it.copy(viewerChapters = newChapters)
            }
        }
        return newChapters
    }

    suspend fun loadChapter(chapter: ReaderChapter): Int? {
        val loader = loader ?: return -1
        Timber.d("Loading adjacent ${chapter.chapter.url}")
        var lastPage: Int? = if (chapter.chapter.pages_left <= 1) 0 else chapter.chapter.last_page_read
        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            withIOContext {
                loadChapter(loader, chapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            Timber.e(e)
            lastPage = null
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
        return lastPage
    }

    fun getChapterUrl(chapter: Chapter? = null): String? = null

    suspend fun loadChapterURL(uri: android.net.Uri) {
        // No-op
    }

    fun toggleBookmark(chapter: Chapter) {
        chapter.bookmark = !chapter.bookmark
        db.updateChapterProgress(chapter).executeAsBlocking()
    }

    private suspend fun preload(chapter: ReaderChapter) {
        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }
        Timber.d("Preloading ${chapter.chapter.url}")
        val loader = loader ?: return
        withIOContext {
            try {
                loader.loadChapter(chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                return@withIOContext
            }
            eventChannel.trySend(Event.ReloadViewerChapters)
        }
    }

    fun adjacentChapter(next: Boolean): ReaderChapter? {
        val chapters = state.value.viewerChapters
        return if (next) chapters?.nextChapter else chapters?.prevChapter
    }

    fun onPageSelected(
        page: ReaderPage,
        hasExtraPage: Boolean,
    ) {
        val currentChapters = state.value.viewerChapters ?: return
        val selectedChapter = page.chapter

        selectedChapter.chapter.last_page_read = page.index
        selectedChapter.chapter.pages_left =
            (selectedChapter.pages?.size ?: page.index) - page.index

        if (!preferences.incognitoMode().get() &&
            (
                (selectedChapter.pages?.lastIndex == page.index && page.firstHalf != true) ||
                    (hasExtraPage && selectedChapter.pages?.lastIndex?.minus(1) == page.index)
            )
        ) {
            selectedChapter.chapter.read = true
        }

        if (selectedChapter != currentChapters.currChapter) {
            Timber.d("Setting ${selectedChapter.chapter.url} as active")
            saveReadingProgress(currentChapters.currChapter)
            setReadStartTime()
            scope.launch { loadNewChapter(selectedChapter) }
        }
    }

    private fun saveReadingProgress(readerChapter: ReaderChapter) {
        db.inTransaction {
            saveChapterProgress(readerChapter)
            saveChapterHistory(readerChapter)
        }
    }

    fun saveCurrentChapterReadingProgress() = getCurrentChapter()?.let { saveReadingProgress(it) }

    private fun saveChapterProgress(readerChapter: ReaderChapter) {
        readerChapter.requestedPage = readerChapter.chapter.last_page_read
        db.getChapter(readerChapter.chapter.id!!).executeAsBlocking()?.let { dbChapter ->
            readerChapter.chapter.bookmark = dbChapter.bookmark
        }
        if (!preferences.incognitoMode().get()) {
            db.updateChapterProgress(readerChapter.chapter).executeAsBlocking()
        }
    }

    private fun saveChapterHistory(readerChapter: ReaderChapter) {
        if (!preferences.incognitoMode().get()) {
            val readAt = Date().time
            val sessionReadDuration = chapterReadStartTime?.let { readAt - it } ?: 0
            val oldTimeRead = db.getHistoryByChapterUrl(readerChapter.chapter.url).executeAsBlocking()?.time_read ?: 0
            val history =
                History.create(readerChapter.chapter).apply {
                    last_read = readAt
                    time_read = sessionReadDuration + oldTimeRead
                }
            db.upsertHistoryLastRead(history).executeAsBlocking()
            chapterReadStartTime = null
        }
    }

    fun setReadStartTime() {
        chapterReadStartTime = Date().time
    }

    suspend fun preloadChapter(chapter: ReaderChapter) {
        preload(chapter)
    }

    fun getCurrentChapter(): ReaderChapter? = state.value.viewerChapters?.currChapter

    fun getMangaReadingMode(): Int {
        val default = preferences.defaultReadingMode()
        val manga = manga ?: return default
        val readerType = manga.defaultReaderType()
        if (manga.viewer_flags == -1) {
            val cantSwitchToLTR =
                (
                    readerType == ReadingModeType.LEFT_TO_RIGHT.flagValue &&
                        default != ReadingModeType.RIGHT_TO_LEFT.flagValue
                )
            if (manga.viewer_flags == -1) {
                manga.viewer_flags = 0
            }
            manga.readingModeType = if (cantSwitchToLTR) 0 else readerType
            db.updateViewerFlags(manga).asRxObservable().subscribe()
        }
        return if (manga.readingModeType == 0) default else manga.readingModeType
    }

    fun setMangaReadingMode(readingModeType: Int) {
        val manga = manga ?: return
        runBlocking(Dispatchers.IO) {
            manga.readingModeType = readingModeType
            db.updateViewerFlags(manga).executeAsBlocking()
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.last_page_read
                mutableState.update {
                    it.copy(
                        manga = db.getManga(manga.id!!).executeAsBlocking(),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.ReloadMangaAndChapters)
            }
        }
    }

    fun getMangaOrientationType(): Int {
        val default = preferences.defaultOrientationType().get()
        return when (manga?.orientationType) {
            OrientationType.DEFAULT.flagValue -> default
            else -> manga?.orientationType ?: default
        }
    }

    fun setMangaOrientationType(rotationType: Int) {
        val manga = manga ?: return
        this.manga?.orientationType = rotationType
        db.updateViewerFlags(manga).executeAsBlocking()
        Timber.i("Manga orientation is ${manga.orientationType}")
        viewModelScope.launchIO {
            db.updateViewerFlags(manga).executeAsBlocking()
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                mutableState.update {
                    it.copy(
                        manga = db.getManga(manga.id!!).executeAsBlocking(),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.SetOrientation(getMangaOrientationType()))
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    private fun saveImage(
        page: ReaderPage,
        directory: File,
        manga: Manga,
    ): File {
        val stream = page.stream!!
        val type = ImageUtil.findImageType(stream) ?: throw Exception("Not an image")
        val context = Injekt.get<Application>()
        directory.mkdirs()
        val chapter = page.chapter.chapter
        val filename =
            DiskUtil.buildValidFilename(
                "${manga.title} - ${chapter.preferredChapterName(context, manga, preferences)}".take(225),
            ) + " - ${page.number}.${type.extension}"
        val destFile = File(directory, filename)
        stream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    private fun saveImages(
        page1: ReaderPage,
        page2: ReaderPage,
        isLTR: Boolean,
        @ColorInt bg: Int,
        directory: File,
        manga: Manga,
    ): File {
        val stream1 = page1.stream!!
        ImageUtil.findImageType(stream1) ?: throw Exception("Not an image")
        val stream2 = page2.stream!!
        ImageUtil.findImageType(stream2) ?: throw Exception("Not an image")
        val imageBytes = stream1().readBytes()
        val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val imageBytes2 = stream2().readBytes()
        val imageBitmap2 = BitmapFactory.decodeByteArray(imageBytes2, 0, imageBytes2.size)
        val stream = ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, bg)
        directory.mkdirs()
        val chapter = page1.chapter.chapter
        val context = Injekt.get<Application>()
        val filename =
            DiskUtil.buildValidFilename(
                "${manga.title} - ${chapter.preferredChapterName(context, manga, preferences)}".take(225),
            ) + " - ${page1.number}-${page2.number}.jpg"
        val destFile = File(directory, filename)
        stream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        stream.close()
        return destFile
    }

    fun saveImage(page: ReaderPage) {
        if (page.status != Page.State.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context.localeContext)
        notifier.onClear()
        val baseDir =
            Environment.getExternalStorageDirectory().absolutePath +
                File.separator + Environment.DIRECTORY_PICTURES +
                File.separator + context.getString(R.string.app_name)
        val destDir =
            if (preferences.folderPerManga().get()) {
                File(baseDir + File.separator + DiskUtil.buildValidFilename(manga.title))
            } else {
                File(baseDir)
            }
        viewModelScope.launchNonCancellable {
            try {
                val file = saveImage(page, destDir, manga)
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(file)))
            } catch (e: Exception) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    fun saveImages(
        firstPage: ReaderPage,
        secondPage: ReaderPage,
        isLTR: Boolean,
        @ColorInt bg: Int,
    ) {
        scope.launch {
            if (firstPage.status != Page.State.READY) return@launch
            if (secondPage.status != Page.State.READY) return@launch
            val manga = manga ?: return@launch
            val context = Injekt.get<Application>()
            val notifier = SaveImageNotifier(context.localeContext)
            notifier.onClear()
            val baseDir =
                Environment.getExternalStorageDirectory().absolutePath +
                    File.separator + Environment.DIRECTORY_PICTURES +
                    File.separator + context.getString(R.string.app_name)
            val destDir =
                if (preferences.folderPerManga().get()) {
                    File(baseDir + File.separator + DiskUtil.buildValidFilename(manga.title))
                } else {
                    File(baseDir)
                }
            try {
                val file = saveImages(firstPage, secondPage, isLTR, bg, destDir, manga)
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(file)))
            } catch (e: Exception) {
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    fun shareImage(page: ReaderPage) {
        if (page.status != Page.State.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()
        val destDir = File(context.cacheDir, "shared_image")
        viewModelScope.launchNonCancellable {
            destDir.deleteRecursively()
            val file = saveImage(page, destDir, manga)
            eventChannel.send(Event.ShareImage(file, page))
        }
    }

    fun shareImages(
        firstPage: ReaderPage,
        secondPage: ReaderPage,
        isLTR: Boolean,
        @ColorInt bg: Int,
    ) {
        scope.launch {
            if (firstPage.status != Page.State.READY) return@launch
            if (secondPage.status != Page.State.READY) return@launch
            val manga = manga ?: return@launch
            val context = Injekt.get<Application>()
            val destDir = File(context.cacheDir, "shared_image")
            destDir.deleteRecursively()
            try {
                val file = saveImages(firstPage, secondPage, isLTR, bg, destDir, manga)
                eventChannel.send(Event.ShareImage(file, firstPage, secondPage))
            } catch (_: Exception) {
            }
        }
    }

    fun setAsCover(page: ReaderPage) {
        if (page.status != Page.State.READY) return
        val manga = manga ?: return
        val stream = page.stream ?: return
        viewModelScope.launchNonCancellable {
            val result =
                try {
                    val context = Injekt.get<Application>()
                    coverCache.deleteFromCache(manga)
                    LocalSource.updateCover(context, manga, stream())
                    R.string.cover_updated
                    SetAsCoverResult.Success
                } catch (e: Exception) {
                    SetAsCoverResult.Error
                }
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    enum class SetAsCoverResult {
        Success,
        AddToLibraryFirst,
        Error,
    }

    sealed class SaveImageResult {
        class Success(
            val file: File,
        ) : SaveImageResult()

        class Error(
            val error: Throwable,
        ) : SaveImageResult()
    }

    data class State(
        val manga: Manga? = null,
        val viewerChapters: ViewerChapters? = null,
        val isLoadingAdjacentChapter: Boolean = false,
        val lastPage: Int? = null,
    )

    sealed class Event {
        object ReloadViewerChapters : Event()

        object ReloadMangaAndChapters : Event()

        data class SetOrientation(
            val orientation: Int,
        ) : Event()

        data class SetCoverResult(
            val result: SetAsCoverResult,
        ) : Event()

        data class SavedImage(
            val result: SaveImageResult,
        ) : Event()

        data class ShareImage(
            val file: File,
            val page: ReaderPage,
            val extraPage: ReaderPage? = null,
        ) : Event()
    }
}
