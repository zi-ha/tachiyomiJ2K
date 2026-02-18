package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

open class BaseChapterHolder(
    view: View,
    private val adapter: BaseChapterAdapter<*>,
) : BaseFlexibleViewHolder(view, adapter) {
    init {
        view.findViewById<View>(R.id.download_button)?.isVisible = false
    }

    internal fun downloadOrRemoveMenu(
        downloadButton: View,
        extraChapter: Chapter? = null,
        // extraStatus removed
    ) {
        // No-op
    }
}
