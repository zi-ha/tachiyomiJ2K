package eu.kanade.tachiyomi.data.image.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.manga.MangaCoverMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import uy.kohesive.injekt.injectLazy
import java.io.File

class MangaCoverFetcher(
    private val manga: Manga,
    private val options: Options,
    private val coverCache: CoverCache,
    private val diskCacheLazy: Lazy<DiskCache>,
) : Fetcher {
    private val diskCacheKey: String? by lazy { MangaCoverKeyer().key(manga, options) }
    private lateinit var url: String

    val fileScope = CoroutineScope(Job() + Dispatchers.IO)

    override suspend fun fetch(): FetchResult {
        url = manga.thumbnail_url ?: error("No cover specified")
        return when (getResourceType(url)) {
            Type.URL -> error("Network covers not supported")
            Type.File -> {
                setRatioAndColorsInScope(manga, File(url.substringAfter("file://")))
                fileLoader(File(url.substringAfter("file://")))
            }
            null -> error("Invalid image")
        }
    }

    private fun fileLoader(file: File): FetchResult =
        SourceResult(
            source = ImageSource(file = file.toOkioPath(), diskCacheKey = diskCacheKey),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )

    private fun getResourceType(cover: String?): Type? =
        when {
            cover.isNullOrEmpty() -> null
            cover.startsWith("http") || cover.startsWith("Custom-", true) -> Type.URL
            cover.startsWith("/") || cover.startsWith("file://") -> Type.File
            else -> null
        }

    private fun setRatioAndColorsInScope(
        manga: Manga,
        ogFile: File? = null,
        force: Boolean = false,
    ) {
        fileScope.launch {
            MangaCoverMetadata.setRatioAndColors(manga, ogFile, force)
        }
    }

    class Factory(
        private val diskCacheLazy: Lazy<DiskCache>,
    ) : Fetcher.Factory<Manga> {
        private val coverCache: CoverCache by injectLazy()

        override fun create(
            data: Manga,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = MangaCoverFetcher(data, options, coverCache, diskCacheLazy)
    }

    private enum class Type {
        File,
        URL,
    }

    companion object {
        const val USE_CUSTOM_COVER = "use_custom_cover"
    }
}
