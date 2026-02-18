package eu.kanade.tachiyomi.ui.recents

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionSet
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupRestoreJob
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterHistory
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.RecentsControllerBinding
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.main.TabbedInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.recents.options.TabbedRecentsOptionsSheet
// import eu.kanade.tachiyomi.ui.source.browse.ProgressItem
import eu.kanade.tachiyomi.util.system.addCheckBoxPrompt
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.isPromptChecked
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.setCustomTitleAndMessage
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.fullAppBarHeight
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.moveRecyclerViewUp
import eu.kanade.tachiyomi.util.view.requestFilePermissionsSafe
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.smoothScrollToTop
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import java.util.Locale

/**
 * Fragment that shows recently read manga.
 * Uses R.layout.fragment_recently_read.
 * UI related actions should be called from here.
 */
class RecentsController(
    bundle: Bundle? = null,
) : BaseCoroutineController<RecentsControllerBinding, RecentsPresenter>(bundle),
    RecentMangaAdapter.RecentsInterface,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.OnItemMoveListener,
    FlexibleAdapter.EndlessScrollListener,
    TabbedInterface,
    RootSearchInterface,
    FloatingSearchInterface {
    init {
        setHasOptionsMenu(true)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    /** Adapter containing the recent manga. */
    private lateinit var adapter: RecentMangaAdapter
    var displaySheet: TabbedRecentsOptionsSheet? = null

    // private var progressItem: ProgressItem? = null
    override var presenter = RecentsPresenter()
    private var snack: Snackbar? = null
    private var lastChapterId: Long? = null
    private var showingDownloads = false
    private var headerHeight = 0
    private var ogRadius = 0f
    private var deviceRadius = 0f to 0f
    private var lastScale = 1f

    private var query = ""
        set(value) {
            field = value
            presenter.query = value
        }

    override val mainRecycler: RecyclerView
        get() = binding.recycler

    override fun getTitle(): String? = view?.context?.getString(R.string.recents)

    override fun getSearchTitle(): String? =
        searchTitle(
            view
                ?.context
                ?.getString(
                    when (presenter.viewType) {
                        RecentsViewType.History -> R.string.history
                        RecentsViewType.Updates -> R.string.updates
                        else -> R.string.updates_and_history
                    },
                )?.lowercase(Locale.ROOT),
        )

    override fun showTabs(): Boolean = activity?.isTablet() != true || activityBinding?.sideNav == null

    override fun createBinding(inflater: LayoutInflater) = RecentsControllerBinding.inflate(inflater)

    /**
     * Called when view is created
     *
     * @param view created view
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        // Initialize adapter
        val isReturning = this::adapter.isInitialized
        adapter = RecentMangaAdapter(this)
        adapter.setPreferenceFlows()
        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManagerAccurateOffset(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.recycledViewPool.setMaxRecycledViews(0, 0)
        binding.recycler.addItemDecoration(RecentMangaDivider(view.context))
        adapter.isSwipeEnabled = true
        adapter.itemTouchHelperCallback.setSwipeFlags(
            if (view.resources.isLTR) ItemTouchHelper.LEFT else ItemTouchHelper.RIGHT,
        )
        binding.swipeRefresh.setStyle()
        scrollViewWith(
            binding.recycler,
            swipeRefreshLayout = binding.swipeRefresh,
            ignoreInsetVisibility = true,
            afterInsets = {
                val appBarHeight = activityBinding?.appBar?.attrToolbarHeight ?: 0
                val systemInsets = it.ignoredSystemInsets
                headerHeight = systemInsets.top + appBarHeight + 48.dpToPx
                binding.recycler.updatePaddingRelative(
                    bottom = activityBinding?.bottomNav?.height ?: systemInsets.bottom,
                )
                // binding.downloadBottomSheet.sheetLayout.isVisible = false
                val bigToolbarHeight = fullAppBarHeight ?: 0

                binding.recentsEmptyView.updatePadding(
                    top = bigToolbarHeight + systemInsets.top,
                    bottom = activityBinding?.bottomNav?.height ?: systemInsets.bottom,
                )
                binding.progress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = (bigToolbarHeight + systemInsets.top) / 2
                }
                if (activityBinding?.bottomNav == null) {
                    setBottomPadding()
                }
                deviceRadius =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val wInsets = it.toWindowInsets()
                        val lCorner = wInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                        val rCorner = wInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                        (lCorner?.radius?.toFloat() ?: 0f) to (rCorner?.radius?.toFloat() ?: 0f)
                    } else {
                        ogRadius to ogRadius
                    }
            },
            onBottomNavUpdate = {
                setBottomPadding()
            },
        )

        if (!isReturning && adapter.itemCount == 0) {
            activityBinding?.appBar?.y = 0f
            activityBinding?.appBar?.updateAppBarAfterY(binding.recycler)
            activityBinding?.appBar?.lockYPos = true
        }
        viewScope.launchUI {
            val height =
                activityBinding?.bottomNav?.height ?: view.rootWindowInsetsCompat
                    ?.getInsets(
                        systemBars(),
                    )?.bottom ?: 0
            binding.recycler.updatePaddingRelative(bottom = height)
            updateTitleAndMenu()
        }

        if (presenter.recentItems.isNotEmpty()) {
            adapter.updateDataSet(presenter.recentItems)
        } else {
            binding.recentsFrameLayout.alpha = 0f
        }

        binding.swipeRefresh.isRefreshing = false
        binding.swipeRefresh.setOnRefreshListener {
            // No-op or local refresh if needed
            binding.swipeRefresh.isRefreshing = false
        }
        ogRadius = view.resources.getDimension(R.dimen.rounded_radius)
        requestFilePermissionsSafe(301, presenter.preferences)
    }

    fun updateTitleAndMenu() {
        if (isControllerVisible) {
            val activity = (activity as? MainActivity) ?: return
            activityBinding?.appBar?.isInvisible = false
            (activity as? MainActivity)?.setStatusBarColorTransparent(false)
            setTitle()
            if (activityBinding?.sideNav?.isExpanded == true) {
                activityBinding
                    ?.sideNav
                    ?.menu
                    ?.findItem(
                        when (presenter.viewType) {
                            RecentsViewType.GroupedAll -> R.id.nav_summary
                            RecentsViewType.UngroupedAll -> R.id.nav_ungrouped
                            RecentsViewType.History -> R.id.nav_history
                            else -> R.id.nav_updates
                        },
                    )?.isChecked = true
            }
        }
    }

    private fun setBottomPadding() {
        val bottomBar = activityBinding?.bottomNav
        val pad = bottomBar?.translationY?.minus(bottomBar.height) ?: 0f
        // Removed download bottom sheet padding logic
    }

    fun setRefreshing(refresh: Boolean) {
        binding.swipeRefresh.isRefreshing = refresh
    }

    override fun onItemMove(
        fromPosition: Int,
        toPosition: Int,
    ) { }

    override fun shouldMoveItem(
        fromPosition: Int,
        toPosition: Int,
    ) = true

    override fun onActionStateChanged(
        viewHolder: RecyclerView.ViewHolder?,
        actionState: Int,
    ) {
        binding.swipeRefresh.isEnabled = actionState != ItemTouchHelper.ACTION_STATE_SWIPE ||
            binding.swipeRefresh.isRefreshing
    }

    override fun canStillGoBack(): Boolean = presenter.preferences.recentsViewType().get() != presenter.viewType.mainValue

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        super.handleOnBackProgressed(backEvent)
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        super.handleOnBackProgressed(backEvent)
    }

    override fun handleOnBackCancelled() {
        super.handleOnBackCancelled()
    }

    override fun handleBack(): Boolean {
        val viewType = RecentsViewType.valueOf(presenter.preferences.recentsViewType().get())
        if (viewType != presenter.viewType) {
            tempJumpTo(viewType)
            return true
        }
        return false
    }

    fun setPadding(sheetIsHidden: Boolean) {
        val cInsets = view?.rootWindowInsetsCompat ?: return
        binding.recycler.updatePaddingRelative(
            bottom = activityBinding?.bottomNav?.height ?: cInsets.getInsets(systemBars()).bottom,
        )
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (!isBindingInitialized) return
        if (!presenter.isLoading) {
            refresh()
        }
        setBottomPadding()
    }

    override fun onDestroy() {
        super.onDestroy()
        snack?.dismiss()
        snack = null
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        displaySheet?.dismiss()
        displaySheet = null
    }

    fun refresh() = presenter.getRecents()

    fun showLists(
        recents: List<RecentMangaItem>,
        hasNewItems: Boolean,
        shouldMoveToTop: Boolean = false,
    ) {
        if (view == null) return
        if (!binding.progress.isVisible && recents.isNotEmpty()) {
            (activity as? MainActivity)?.showNotificationPermissionPrompt()
        }
        binding.progress.isVisible = false
        binding.recentsFrameLayout.alpha = 1f
        binding.swipeRefresh.isRefreshing = false
        adapter.removeAllScrollableHeaders()
        adapter.updateDataSet(recents)
        adapter.onLoadMoreComplete(null)
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
        if (!hasNewItems ||
            presenter.viewType == RecentsViewType.GroupedAll ||
            recents.isEmpty()
        ) {
            loadNoMore()
        } else if (presenter.viewType != RecentsViewType.GroupedAll) {
            resetProgressItem()
        }
        if (recents.isEmpty()) {
            binding.recentsEmptyView.show(
                if (!isSearching()) {
                    R.drawable.ic_history_off_24dp
                } else {
                    R.drawable.ic_search_off_24dp
                },
                if (isSearching()) {
                    R.string.no_results_found
                } else {
                    when (presenter.viewType) {
                        RecentsViewType.Updates -> R.string.no_recent_chapters
                        RecentsViewType.History -> R.string.no_recently_read_manga
                        else -> R.string.no_recent_read_updated_manga
                    }
                },
            )
        } else {
            binding.recentsEmptyView.hide()
        }
        val isSearchExpanded = activityBinding?.searchToolbar?.isSearchExpanded == true
        if (shouldMoveToTop) {
            if (isSearchExpanded) {
                moveRecyclerViewUp(scrollUpAnyway = true)
            } else {
                (binding.recycler.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(0, 0)
            }
        }
        if (lastChapterId != null) {
            refreshItem(lastChapterId ?: 0L)
            lastChapterId = null
        }
    }

    fun updateChapterDownload(
        download: Any,
        updateDLSheet: Boolean = true,
    ) {
        // No-op
    }

    fun updateDownloadStatus(isRunning: Boolean) {
        // No-op
    }

    private fun refreshItem(chapterId: Long) {
        val recentItemPos =
            adapter.currentItems.indexOfFirst {
                it is RecentMangaItem &&
                    it.mch.chapter.id == chapterId
            }
        if (recentItemPos > -1) adapter.notifyItemChanged(recentItemPos)
    }

    override fun downloadChapter(position: Int) {
        // No-op
    }

    override fun startDownloadNow(position: Int) {
        // No-op
    }

    override fun downloadChapter(
        position: Int,
        chapter: Chapter,
    ) {
        // No-op
    }

    override fun startDownloadNow(
        position: Int,
        chapter: Chapter,
    ) {
        // No-op
    }

    override fun onCoverClick(position: Int) {
        val manga = (adapter.getItem(position) as? RecentMangaItem)?.mch?.manga ?: return
        router.pushController(MangaDetailsController(manga).withFadeTransaction())
    }

    override fun onRemoveHistoryClicked(position: Int) {
        onItemLongClick(position)
    }

    override fun onSubChapterClicked(
        position: Int,
        chapter: Chapter,
        view: View,
    ) {
        val manga = (adapter.getItem(position) as? RecentMangaItem)?.mch?.manga ?: return
        openChapter(view, manga, chapter)
    }

    override fun areExtraChaptersExpanded(position: Int): Boolean {
        if (alwaysExpanded()) return true
        val item = (adapter.getItem(position) as? RecentMangaItem) ?: return false
        val date = presenter.dateFormat.format(item.mch.history.last_read)
        val invertDefault = !adapter.collapseGrouped
        return presenter.expandedSectionsMap["${item.mch.manga} - $date"]?.xor(invertDefault)
            ?: invertDefault
    }

    override fun updateExpandedExtraChapters(
        position: Int,
        expanded: Boolean,
    ) {
        if (alwaysExpanded()) return
        val item = (adapter.getItem(position) as? RecentMangaItem) ?: return
        val date = presenter.dateFormat.format(item.mch.history.last_read)
        val invertDefault = !adapter.collapseGrouped
        presenter.expandedSectionsMap["${item.mch.manga} - $date"] = expanded.xor(invertDefault)
    }

    fun tempJumpTo(viewType: RecentsViewType) {
        presenter.toggleGroupRecents(viewType, false)
        activityBinding?.mainTabs?.run { selectTab(getTabAt(viewType.mainValue)) }
        (activity as? MainActivity)?.reEnableBackPressedCallBack()
        updateTitleAndMenu()
    }

    fun setViewType(viewType: RecentsViewType) {
        if (viewType != presenter.viewType) {
            presenter.toggleGroupRecents(viewType)
            updateTitleAndMenu()
        }
    }

    override fun getViewType(): RecentsViewType = presenter.viewType

    override fun scope() = viewScope

    override fun onItemClick(
        view: View?,
        position: Int,
    ): Boolean {
        val item = adapter.getItem(position) ?: return false
        if (item is RecentMangaItem) {
            if (item.mch.manga.id == null) {
                val headerItem = adapter.getHeaderOf(item) as? RecentMangaHeaderItem
                tempJumpTo(
                    when (headerItem?.recentsType) {
                        RecentMangaHeaderItem.NEW_CHAPTERS -> RecentsViewType.Updates
                        RecentMangaHeaderItem.CONTINUE_READING -> RecentsViewType.History
                        else -> return false
                    },
                )
            } else {
                if (activity == null) return false
                openChapter(view?.findViewById(R.id.recent_card), item.mch.manga, item.chapter)
            }
        } else if (item is RecentMangaHeaderItem) {
            return false
        }
        return true
    }

    private fun openChapter(
        view: View?,
        manga: Manga,
        chapter: Chapter,
    ) {
        val activity = activity ?: return
        activity.apply {
            if (view != null) {
                val (intent, bundle) =
                    ReaderActivity
                        .newIntentWithTransitionOptions(activity, manga, chapter, view)
                startActivity(intent, bundle)
            } else {
                val intent = ReaderActivity.newIntent(activity, manga, chapter)
                startActivity(intent)
            }
        }
    }

    override fun onItemLongClick(position: Int) {
        val item = adapter.getItem(position) as? RecentMangaItem ?: return
        showRemoveHistoryDialog(item.mch.manga, item.mch.history, item.mch.chapter)
    }

    override fun onItemLongClick(
        position: Int,
        chapter: ChapterHistory,
    ): Boolean {
        val history = chapter.history ?: return false
        val item = adapter.getItem(position) as? RecentMangaItem ?: return false
        if (history.id != null) {
            showRemoveHistoryDialog(item.mch.manga, history, chapter)
        }
        return history.id != null
    }

    private fun showRemoveHistoryDialog(
        manga: Manga,
        history: History,
        chapter: Chapter,
    ) {
        val activity = activity ?: return
        if (history.id != null) {
            activity
                .materialAlertDialog()
                .setCustomTitleAndMessage(
                    R.string.reset_chapter_question,
                    activity.getString(
                        R.string.this_will_remove_the_read_date_for_x_question,
                        chapter.name,
                    ),
                ).addCheckBoxPrompt(
                    activity.getString(
                        R.string.reset_all_chapters_for_this_,
                        manga.seriesType(activity),
                    ),
                ).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset) { dialog, _ ->
                    removeHistory(manga, history, dialog.isPromptChecked)
                }.show()
        }
    }

    private fun removeHistory(
        manga: Manga,
        history: History,
        all: Boolean,
    ) {
        if (all) {
            // Reset last read of chapter to 0L
            presenter.removeAllFromHistory(manga.id!!)
        } else {
            // Remove all chapters belonging to manga from library
            presenter.removeFromHistory(history)
        }
    }

    override fun markAsRead(position: Int) {
        val preferences = presenter.preferences
        val db = presenter.db
        val item = adapter.getItem(position) as? RecentMangaItem ?: return
        val holder = binding.recycler.findViewHolderForAdapterPosition(position)
        val holderId = (holder as? RecentMangaHolder)?.chapterId
        adapter.notifyItemChanged(position)
        val transition = TransitionSet().addTransition(androidx.transition.Fade())
        transition.duration =
            view!!
                .resources
                .getInteger(android.R.integer.config_shortAnimTime)
                .toLong()
        androidx.transition.TransitionManager.beginDelayedTransition(binding.recycler, transition)
        if (holderId == -1L) return
        val chapter =
            holderId?.let { item.mch.extraChapters.find { holderId == it.id } }
                ?: item.chapter
        val manga = item.mch.manga
        val lastRead = chapter.last_page_read
        val pagesLeft = chapter.pages_left
        lastChapterId = chapter.id
        val wasRead = chapter.read
        presenter.markChapterRead(chapter, !wasRead)
        snack =
            view?.snack(
                if (wasRead) {
                    R.string.marked_as_unread
                } else {
                    R.string.marked_as_read
                },
                Snackbar.LENGTH_INDEFINITE,
            ) {
                anchorView = activityBinding?.bottomNav
                var undoing = false
                setAction(R.string.undo) {
                    presenter.markChapterRead(chapter, wasRead, lastRead, pagesLeft)
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(
                            transientBottomBar: Snackbar?,
                            event: Int,
                        ) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing && !wasRead) {
                                if (preferences.removeAfterMarkedAsRead()) {
                                    lastChapterId = chapter.id
                                    presenter.deleteChapter(chapter, manga)
                                }
                                // updateTrackChapterMarkedAsRead(db, preferences, chapter, manga.id) { ... }
                            }
                        }
                    },
                )
            }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    private fun isSearching() = query.isNotEmpty()

    override fun alwaysExpanded() = query.isNotEmpty() || (presenter.viewType.isHistory && !presenter.groupHistory.isByTime)

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.recents, menu)

        val searchItem = activityBinding?.searchToolbar?.searchItem
        val searchView = activityBinding?.searchToolbar?.searchView
        activityBinding?.searchToolbar?.setQueryHint(view?.context?.getString(R.string.search_recents), !isSearching())
        if (isSearching()) {
            searchItem?.expandActionView()
            searchView?.setQuery(query, true)
            searchView?.clearFocus()
        }
        setOnQueryTextChangeListener(activityBinding?.searchToolbar?.searchView) {
            if (query != it) {
                query = it ?: return@setOnQueryTextChangeListener false
                resetProgressItem()
                refresh()
            }
            true
        }
    }

    override fun onChangeStarted(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            if (type == ControllerChangeType.POP_ENTER) presenter.onCreate()
            if (isControllerVisible) {
                activityBinding?.mainTabs?.let { tabs ->
                    tabs.removeAllTabs()
                    tabs.clearOnTabSelectedListeners()
                    val selectedTab = presenter.viewType
                    RecentsViewType.entries.forEach { viewType ->
                        tabs.addTab(
                            tabs.newTab().setText(viewType.stringRes).also { tab ->
                                tab.view.compatToolTipText = null
                            },
                            viewType == selectedTab,
                        )
                    }
                    tabs.addOnTabSelectedListener(
                        object : TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: TabLayout.Tab?) {
                                setViewType(RecentsViewType.valueOf(tab?.position))
                            }

                            override fun onTabUnselected(tab: TabLayout.Tab?) {}

                            override fun onTabReselected(tab: TabLayout.Tab?) {
                                binding.recycler.smoothScrollToTop()
                            }
                        },
                    )
                    (activity as? MainActivity)?.showTabBar(showTabs())
                }
            }
        } else {
            val lastController = router.backstack.lastOrNull()?.controller
            if (lastController !is DialogController) {
                (activity as? MainActivity)?.showTabBar(show = false, animate = lastController !is SmallToolbarInterface)
            }
            snack?.dismiss()
        }
        setBottomPadding()
    }

    override fun onChangeEnded(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        super.onChangeEnded(handler, type)
        if (type == ControllerChangeType.POP_ENTER) {
            setBottomPadding()
        }
        if (type.isEnter && isControllerVisible) {
            updateTitleAndMenu()
        }
    }

    fun hasQueue() = presenter.downloadManager.hasQueue()

    override fun showSheet() {
        if (!isBindingInitialized) return
        if (binding.downloadBottomSheet.dlBottomSheet.sheetBehavior
                ?.isHideable == false ||
            hasQueue()
        ) {
            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior
                ?.expand()
        }
    }

    override fun hideSheet() {
        if (!isBindingInitialized) return
        if (binding.downloadBottomSheet.dlBottomSheet.sheetBehavior
                ?.isHideable == true
        ) {
            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior
                ?.hide()
        } else {
            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior
                ?.collapse()
        }
    }

    override fun toggleSheet() {
        if (showingDownloads) {
            binding.downloadBottomSheet.dlBottomSheet.dismiss()
        } else {
            binding.downloadBottomSheet.dlBottomSheet.sheetBehavior
                ?.expand()
        }
    }

    override fun expandSearch() {
        if (showingDownloads) {
            binding.downloadBottomSheet.dlBottomSheet.dismiss()
        } else {
            activityBinding
                ?.searchToolbar
                ?.menu
                ?.findItem(R.id.action_search)
                ?.expandActionView()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.display_options -> {
                displaySheet =
                    TabbedRecentsOptionsSheet(
                        this,
                        (presenter.viewType.mainValue - 1).coerceIn(0, 2),
                    )
                displaySheet?.show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun noMoreLoad(newItemsSize: Int) {}

    override fun onLoadMore(
        lastPosition: Int,
        currentPage: Int,
    ) {
        val view = view ?: return
        if (presenter.finished ||
            BackupRestoreJob.isRunning(view.context.applicationContext) ||
            (presenter.viewType == RecentsViewType.GroupedAll && !isSearching())
        ) {
            loadNoMore()
            return
        }
        presenter.requestNext()
    }

    private fun loadNoMore() {
        adapter.onLoadMoreComplete(null)
    }

    /**
     * Sets a new progress item and reenables the scroll listener.
     */
    private fun resetProgressItem() {
        adapter.onLoadMoreComplete(null)
        // progressItem = ProgressItem()
        // adapter.setEndlessScrollListener(this, progressItem!!)
    }
}
