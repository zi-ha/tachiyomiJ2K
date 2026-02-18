package eu.kanade.tachiyomi.ui.source.browse

import android.os.Bundle
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseSourcePresenter(
    val sourceId: Long,
    val sourceManager: SourceManager = Injekt.get(),
) : BaseCoroutinePresenter<BrowseSourceController>() {
    val source: CatalogueSource? = sourceManager.get(sourceId) as? CatalogueSource

    fun onCreate(savedState: Bundle?) {
        // super.onCreate(savedState)
        // TODO: Implement loading logic for LocalSource
    }
}
