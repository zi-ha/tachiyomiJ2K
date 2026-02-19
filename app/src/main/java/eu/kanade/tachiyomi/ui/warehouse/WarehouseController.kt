package eu.kanade.tachiyomi.ui.warehouse

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.junrar.Archive
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.models.LocalLocation
import eu.kanade.tachiyomi.databinding.WarehouseControllerBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.requestFilePermissionsSafe
import eu.kanade.tachiyomi.util.view.scrollViewWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.util.Date

class WarehouseController(
    bundle: Bundle? = null,
) : BaseController<WarehouseControllerBinding>(bundle),
    WarehouseAdapter.OnLocationClickListener {
    private val preferences: PreferencesHelper by injectLazy()
    private val db: DatabaseHelper by injectLazy()

    private val localSource by lazy { LocalSource(preferences.context) }

    private val adapter = WarehouseAdapter(this)

    override fun getTitle(): String? = resources?.getString(R.string.bookshelf)

    override fun createBinding(inflater: LayoutInflater): WarehouseControllerBinding = WarehouseControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        requestFilePermissionsSafe(500, preferences)

        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter

        scrollViewWith(
            binding.recycler,
            padBottom = true,
            afterInsets = { insets ->
                val bottomNavHeight = activityBinding?.bottomNav?.takeIf { it.isVisible }?.height ?: 0
                binding.fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.getInsets(systemBars()).bottom + bottomNavHeight + 16.dpToPx
                }
            },
        )

        loadLocations()

        binding.fab.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE)
        }
    }

    private fun loadLocations() {
        adapter.items = preferences.getLocalLocations()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return

            // Persist permissions
            val contentResolver = activity?.contentResolver ?: return
            val takeFlags: Int =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Try to get path from Uri
            val path =
                uri.path?.let {
                    val split = it.split(":")
                    if (split.size > 1 && split[0].endsWith("primary")) {
                        "/storage/emulated/0/${split[1]}"
                    } else {
                        it
                    }
                } ?: ""

            // Show dialog
            showAddDialog(uri, path)
        }
    }

    private fun showAddDialog(
        uri: Uri,
        initialPath: String,
    ) {
        val context = view?.context ?: return
        // Try to guess a name
        val defaultName = uri.lastPathSegment?.split(":")?.last() ?: "Warehouse"

        val input = TextInputEditText(context)
        input.setText(defaultName)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_add)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString()
                val location = LocalLocation(initialPath, name, true)
                val current = preferences.getLocalLocations().toMutableList()
                current.add(location)
                preferences.setLocalLocations(current)
                loadLocations()
                // Auto scan
                onRefresh(location)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onRefresh(location: LocalLocation) {
        viewScope.launch {
            binding.scanProgress.isVisible = true
            val result =
                withContext(Dispatchers.IO) {
                    scanAndSync(File(location.directory))
                }
            binding.scanProgress.isVisible = false
            activity?.toast(
                resources?.getString(
                    R.string.bookshelf_scan_result,
                    result.added,
                    result.updated,
                ),
            )
        }
    }

    override fun onDelete(location: LocalLocation) {
        val current = preferences.getLocalLocations().toMutableList()
        current.remove(location)
        preferences.setLocalLocations(current)
        loadLocations()
    }

    override fun onToggle(location: LocalLocation) {
        val current = preferences.getLocalLocations().toMutableList()
        val index = current.indexOfFirst { it.directory == location.directory && it.name == location.name }
        if (index != -1) {
            current[index] = location
            preferences.setLocalLocations(current)
            loadLocations()
        }
    }

    private data class ScanResult(
        val added: Int,
        val updated: Int,
    )

    private suspend fun scanAndSync(root: File): ScanResult {
        if (!root.exists() || !root.isDirectory) return ScanResult(0, 0)

        val entries =
            root
                .listFiles()
                ?.asSequence()
                ?.filterNot { it.name.startsWith('.') }
                ?.filterNot { it.name == ".covers" }
                ?.filter {
                    it.isDirectory ||
                        it.extension.lowercase() in setOf("zip", "cbz", "rar", "cbr")
                }?.toList()
                ?: emptyList()

        var added = 0
        var updated = 0

        for (entry in entries) {
            val manga = upsertManga(entry)
            if (manga.second) {
                added += 1
            } else {
                updated += 1
            }
            val dbManga = manga.first
            syncChapters(dbManga)
            if (dbManga.thumbnail_url.isNullOrBlank()) {
                val cover = resolveCover(entry, dbManga)
                if (cover != null) {
                    dbManga.thumbnail_url = cover.absolutePath
                    db.insertManga(dbManga).executeAsBlocking()
                }
            }
        }

        return ScanResult(added, updated)
    }

    private fun upsertManga(entry: File): Pair<Manga, Boolean> {
        val url =
            try {
                entry.canonicalPath
            } catch (_: Exception) {
                entry.absolutePath
            }
        val existing =
            db.getManga(url, LocalSource.ID).executeAsBlocking()
                ?: db.getManga(entry.name, LocalSource.ID).executeAsBlocking()?.also {
                    it.url = url
                }

        val manga =
            (existing ?: (SManga.create() as Manga)).apply {
                this.url = url
                source = LocalSource.ID
                title = if (entry.isFile) entry.nameWithoutExtension else entry.name
                favorite = true
                if (date_added == 0L) {
                    date_added = Date().time
                }
            }

        db.insertManga(manga).executeAsBlocking()
        val saved =
            manga.id?.let { db.getManga(it).executeAsBlocking() }
                ?: db.getManga(url, LocalSource.ID).executeAsBlocking()!!
        if (existing == null) {
            attachDefaultCategory(saved)
        }
        return saved to (existing == null)
    }

    private fun attachDefaultCategory(manga: Manga) {
        val categories = db.getCategories().executeAsBlocking()
        if (categories.isEmpty()) return
        val defaultCategoryId = preferences.defaultCategory()
        val category =
            categories.find { it.id == defaultCategoryId }
                ?: categories.find { it.id == 0 }
                ?: return
        val mc = MangaCategory.create(manga, category)
        db.setMangaCategories(listOf(mc), listOf(manga))
    }

    private suspend fun resolveCover(
        entry: File,
        manga: Manga,
    ): File? {
        if (entry.isDirectory) {
            val children = entry.listFiles()?.toList().orEmpty()
            val hasChapters = children.any { it.isDirectory || it.extension.lowercase() in setOf("zip", "cbz", "rar", "cbr") }
            if (hasChapters) {
                val cover =
                    children
                        .firstOrNull {
                            it.isFile &&
                                it.nameWithoutExtension == "_cover_" &&
                                ImageUtil.isImage(it.name) { FileInputStream(it) }
                        } ?: children
                        .firstOrNull {
                            it.isFile &&
                                it.nameWithoutExtension == "cover" &&
                                ImageUtil.isImage(it.name) { FileInputStream(it) }
                        }
                if (cover != null) {
                    return try {
                        LocalSource.updateCover(preferences.context, manga, cover.inputStream())
                    } catch (_: Exception) {
                        null
                    }
                }
                val chapters = localSource.getChapterList(manga)
                val chapter = chapters.lastOrNull() ?: return null
                return try {
                    when (val format = localSource.getFormat(chapter)) {
                        is LocalSource.Format.Directory -> {
                            val first =
                                format.file
                                    .listFiles()
                                    ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                    ?.firstOrNull {
                                        it.isFile &&
                                            it.nameWithoutExtension != "_cover_" &&
                                            ImageUtil.isImage(it.name) { FileInputStream(it) }
                                    } ?: return null
                            LocalSource.updateCover(preferences.context, manga, first.inputStream())
                        }
                        is LocalSource.Format.Zip -> {
                            java.util.zip.ZipFile(format.file).use { zip ->
                                val zEntry =
                                    zip
                                        .entries()
                                        .toList()
                                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                        .firstOrNull { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
                                        ?: return null
                                LocalSource.updateCover(preferences.context, manga, zip.getInputStream(zEntry))
                            }
                        }
                        is LocalSource.Format.Rar -> {
                            Archive(format.file).use { archive ->
                                val header =
                                    archive.fileHeaders
                                        .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                                        .firstOrNull { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }
                                        ?: return null
                                LocalSource.updateCover(preferences.context, manga, archive.getInputStream(header))
                            }
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            } else {
                val first =
                    children
                        .asSequence()
                        .filter { it.isFile && it.nameWithoutExtension != "_cover_" }
                        .filter { ImageUtil.isImage(it.name) { FileInputStream(it) } }
                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        .firstOrNull()
                if (first != null) {
                    return try {
                        LocalSource.updateCover(preferences.context, manga, first.inputStream())
                    } catch (_: Exception) {
                        null
                    }
                }
                return null
            }
        }
        if (entry.isFile && entry.extension.lowercase() in setOf("zip", "cbz", "rar", "cbr")) {
            val chapter =
                SChapter.create().apply {
                    url = entry.absolutePath
                    name = entry.nameWithoutExtension
                    date_upload = entry.lastModified()
                    chapter_number = 1f
                }
            return try {
                when (val format = localSource.getFormat(chapter)) {
                    is LocalSource.Format.Zip -> {
                        java.util.zip.ZipFile(format.file).use { zip ->
                            val zEntry =
                                zip
                                    .entries()
                                    .toList()
                                    .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                    .firstOrNull { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
                                    ?: return null
                            LocalSource.updateCover(preferences.context, manga, zip.getInputStream(zEntry))
                        }
                    }
                    is LocalSource.Format.Rar -> {
                        Archive(format.file).use { archive ->
                            val header =
                                archive.fileHeaders
                                    .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                                    .firstOrNull { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }
                                    ?: return null
                            LocalSource.updateCover(preferences.context, manga, archive.getInputStream(header))
                        }
                    }
                    is LocalSource.Format.Directory -> null
                }
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private suspend fun syncChapters(manga: Manga) {
        val mangaId = manga.id ?: return
        val chapters = localSource.getChapterList(manga)
        if (chapters.isEmpty()) return

        val dbChapters = db.getChapters(manga).executeAsBlocking()
        val toAdd =
            chapters
                .filter { chapter -> dbChapters.none { it.url == chapter.url } }
                .map { sChapter ->
                    ChapterImpl().apply {
                        url = sChapter.url
                        name = sChapter.name
                        date_upload = sChapter.date_upload
                        chapter_number = sChapter.chapter_number
                        scanlator = sChapter.scanlator
                        manga_id = mangaId
                    }
                }

        val toDelete = dbChapters.filter { chapter -> chapters.none { it.url == chapter.url } }

        if (toAdd.isNotEmpty()) {
            db.insertChapters(toAdd).executeAsBlocking()
        }

        if (toDelete.isNotEmpty()) {
            db.deleteChapters(toDelete).executeAsBlocking()
        }
    }

    companion object {
        private const val REQUEST_CODE_OPEN_DOCUMENT_TREE = 101
    }
}
