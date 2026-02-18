package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

class SourceManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, Source>(mapOf(LocalSource.ID to LocalSource(context))))

    val catalogueSources: Flow<List<CatalogueSource>> = sourcesMapFlow.map { it.values.filterIsInstance<CatalogueSource>() }

    fun get(sourceKey: Long): Source? = sourcesMapFlow.value[sourceKey]

    fun getOrStub(sourceKey: Long): Source = sourcesMapFlow.value[sourceKey] ?: StubSource(sourceKey)

    fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<CatalogueSource>()

    inner class StubSource(
        override val id: Long,
    ) : Source {
        override val name: String
            get() = id.toString()

        override suspend fun getMangaDetails(manga: SManga): SManga = throw getSourceNotInstalledException()

        override suspend fun getChapterList(manga: SManga): List<SChapter> = throw getSourceNotInstalledException()

        override suspend fun getPageList(chapter: SChapter): List<Page> = throw getSourceNotInstalledException()

        override fun toString(): String = name

        private fun getSourceNotInstalledException(): Exception =
            SourceNotFoundException(
                context.getString(R.string.source_not_installed_, id.toString()),
                id,
            )

        override fun hashCode(): Int = id.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StubSource
            return id == other.id
        }
    }
}

class SourceNotFoundException(
    message: String,
    val id: Long,
) : Exception(message)
