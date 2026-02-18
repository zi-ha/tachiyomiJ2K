package eu.kanade.tachiyomi.ui.source

import android.view.LayoutInflater
import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.BrowseControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class BrowseController :
    BaseController<BrowseControllerBinding>(),
    FlexibleAdapter.OnItemClickListener,
    SourceAdapter.SourceListener {
    private val preferences: PreferencesHelper = Injekt.get()

    private var adapter: SourceAdapter? = null

    val presenter = SourcePresenter(this)

    override fun getTitle(): String? = view?.context?.getString(R.string.browse)

    override fun createBinding(inflater: LayoutInflater) = BrowseControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        adapter = SourceAdapter(this)
        binding.sourceRecycler.layoutManager = LinearLayoutManagerAccurateOffset(view.context)
        binding.sourceRecycler.adapter = adapter
        binding.sourceRecycler.addItemDecoration(SourceDividerItemDecoration(view.context))
        adapter?.isSwipeEnabled = true

        scrollViewWith(binding.sourceRecycler)

        presenter.onCreate()
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    override fun onItemClick(
        view: View,
        position: Int,
    ): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        val source = item.source
        openCatalogue(source, BrowseSourceController(source))
        return false
    }

    override fun onPinClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        val isPinned = item.isPinned ?: item.header?.code?.equals(SourcePresenter.PINNED_KEY) ?: false
        pinCatalogue(item.source, isPinned)
    }

    override fun onLatestClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        openCatalogue(item.source, BrowseSourceController(item.source, useLatest = true))
    }

    private fun pinCatalogue(
        source: CatalogueSource,
        isPinned: Boolean,
    ) {
        val current = preferences.pinnedCatalogues().get()
        if (isPinned) {
            preferences.pinnedCatalogues().set(current - source.id.toString())
        } else {
            preferences.pinnedCatalogues().set(current + source.id.toString())
        }
        presenter.updateSources()
    }

    private fun openCatalogue(
        source: CatalogueSource,
        controller: BrowseSourceController,
    ) {
        if (!preferences.incognitoMode().get()) {
            preferences.lastUsedCatalogueSource().set(source.id)
            if (source !is LocalSource) {
                val list = preferences.lastUsedSources().get().toMutableSet()
                list.removeAll { it.startsWith("${source.id}:") }
                list.add("${source.id}:${Date().time}")
                val sortedList =
                    list
                        .filter { it.split(":").size == 2 }
                        .sortedByDescending { it.split(":").last().toLong() }
                preferences.lastUsedSources().set(sortedList.take(2).toSet())
            }
        }
        router.pushController(controller.withFadeTransaction())
    }

    fun setSources(
        sources: List<SourceItem>,
        lastUsed: SourceItem?,
    ) {
        adapter?.updateDataSet(sources, false)
        setLastUsedSource(lastUsed)
    }

    fun setLastUsedSource(item: SourceItem?) {
        adapter?.removeAllScrollableHeaders()
        if (item != null) {
            adapter?.addScrollableHeader(item)
            adapter?.addScrollableHeader(LangItem(SourcePresenter.LAST_USED_KEY))
        }
    }
}
