package eu.kanade.tachiyomi.ui.manga.chapter

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible

open class BaseChapterAdapter<T : IFlexible<*>>(
    obj: Any,
) : FlexibleAdapter<T>(null, obj, true) {
    val baseDelegate = obj
}
