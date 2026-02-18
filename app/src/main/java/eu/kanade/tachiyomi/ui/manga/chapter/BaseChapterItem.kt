package eu.kanade.tachiyomi.ui.manga.chapter

import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.kanade.tachiyomi.data.database.models.Chapter

abstract class BaseChapterItem<T : BaseChapterHolder, H : AbstractHeaderItem<*>>(
    val chapter: Chapter,
    header: H? = null,
) : AbstractSectionableItem<T, H?>(header),
    Chapter by chapter {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is BaseChapterItem<*, *>) {
            return chapter.id == other.chapter.id
        }
        return false
    }

    override fun hashCode(): Int = (chapter.id ?: 0L).hashCode()
}
