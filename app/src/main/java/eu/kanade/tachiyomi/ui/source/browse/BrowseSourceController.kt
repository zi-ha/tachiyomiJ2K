package eu.kanade.tachiyomi.ui.source.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.databinding.BrowseSourceControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController

class BrowseSourceController(
    bundle: Bundle? = null,
) : BaseController<BrowseSourceControllerBinding>(bundle),
    FlexibleAdapter.OnItemClickListener {
    constructor(source: CatalogueSource, useLatest: Boolean = false) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)
            putBoolean(USE_LATEST_KEY, useLatest)
        },
    )

    // val presenter = BrowseSourcePresenter(args.getLong(SOURCE_ID_KEY))

    override fun createBinding(inflater: LayoutInflater) = BrowseSourceControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
    }

    override fun onItemClick(
        view: View,
        position: Int,
    ): Boolean {
        // TODO
        return false
    }

    companion object {
        const val SOURCE_ID_KEY = "sourceId"
        const val USE_LATEST_KEY = "useLatest"
    }
}
