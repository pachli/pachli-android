/*
 * Copyright 2019 Conny Duck
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import app.pachli.adapter.ItemInteractionListener
import app.pachli.adapter.TabAdapter
import app.pachli.appstore.EventHub
import app.pachli.appstore.MainTabsChangedEvent
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.Lists
import app.pachli.core.data.repository.ListsRepository
import app.pachli.core.data.repository.ListsRepository.Companion.compareByListTitle
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Timeline
import app.pachli.core.navigation.ListsActivityIntent
import app.pachli.core.network.model.MastoList
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.databinding.ActivityTabPreferenceBinding
import app.pachli.databinding.DialogSelectListBinding
import at.connyduck.sparkbutton.helpers.Utils
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import dagger.hilt.android.AndroidEntryPoint
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TabPreferenceActivity : BaseActivity(), ItemInteractionListener {

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var listsRepository: ListsRepository

    private val binding by viewBinding(ActivityTabPreferenceBinding::inflate)

    private lateinit var currentTabs: MutableList<TabViewData>
    private lateinit var currentTabsAdapter: TabAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var addTabAdapter: TabAdapter

    private var tabsChanged = false

    private val selectedItemElevation by unsafeLazy { resources.getDimension(DR.dimen.selected_drag_item_elevation) }

    private val hashtagRegex by unsafeLazy { Pattern.compile("([\\w_]*[\\p{Alpha}_][\\w_]*)", Pattern.CASE_INSENSITIVE) }

    private val onFabDismissedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            toggleFab(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        supportActionBar?.apply {
            setTitle(R.string.title_tab_preferences)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        currentTabs = accountManager.activeAccount?.tabPreferences.orEmpty().map { TabViewData.from(it) }.toMutableList()
        currentTabsAdapter = TabAdapter(currentTabs, false, this)
        binding.currentTabsRecyclerView.adapter = currentTabsAdapter
        binding.currentTabsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.currentTabsRecyclerView.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )

        addTabAdapter = TabAdapter(listOf(TabViewData.from(Timeline.Conversations)), true, this)
        binding.addTabRecyclerView.adapter = addTabAdapter
        binding.addTabRecyclerView.layoutManager = LinearLayoutManager(this)

        touchHelper = ItemTouchHelper(
            object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                    return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.END)
                }

                override fun isItemViewSwipeEnabled(): Boolean {
                    // Swiping enabled in TabAdapter.onBindViewHolder if the timeline is not Timeline.Home
                    return false
                }

                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    val temp = currentTabs[viewHolder.bindingAdapterPosition]
                    currentTabs[viewHolder.bindingAdapterPosition] = currentTabs[target.bindingAdapterPosition]
                    currentTabs[target.bindingAdapterPosition] = temp

                    currentTabsAdapter.notifyItemMoved(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    saveTabs()
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    onTabRemoved(viewHolder.bindingAdapterPosition)
                }

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.elevation = selectedItemElevation
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.elevation = 0f
                }
            },
        )

        touchHelper.attachToRecyclerView(binding.currentTabsRecyclerView)

        binding.actionButton.setOnClickListener {
            toggleFab(true)
        }

        binding.scrim.setOnClickListener {
            toggleFab(false)
        }

        updateAvailableTabs()

        onBackPressedDispatcher.addCallback(onFabDismissedCallback)
    }

    override fun onTabAdded(tab: TabViewData) {
        toggleFab(false)

        if (tab.timeline is Timeline.Hashtags) {
            showAddHashtagDialog()
            return
        }

        if (tab.timeline is Timeline.UserList) {
            showSelectListDialog()
            return
        }

        currentTabs.add(tab)
        currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
        updateAvailableTabs()
        saveTabs()
    }

    override fun onTabRemoved(position: Int) {
        currentTabs.removeAt(position)
        currentTabsAdapter.notifyItemRemoved(position)
        updateAvailableTabs()
        saveTabs()
    }

    override fun onActionChipClicked(timelineHashtags: Timeline.Hashtags, tabPosition: Int) {
        showAddHashtagDialog(timelineHashtags, tabPosition)
    }

    override fun onChipClicked(timeline: Timeline.Hashtags, tabPosition: Int, chipPosition: Int) {
        currentTabs[tabPosition] = currentTabs[tabPosition].copy(
            timeline = timeline.copy(
                tags = timeline.tags.filterIndexed { i, _ -> i != chipPosition },
            ),
        )
        saveTabs()

        currentTabsAdapter.notifyItemChanged(tabPosition)
    }

    private fun toggleFab(expand: Boolean) {
        val transition = MaterialContainerTransform().apply {
            startView = if (expand) binding.actionButton else binding.sheet
            val endView: View = if (expand) binding.sheet else binding.actionButton
            this.endView = endView
            addTarget(endView)
            scrimColor = Color.TRANSPARENT
            setPathMotion(MaterialArcMotion())
        }

        TransitionManager.beginDelayedTransition(binding.root, transition)
        binding.actionButton.visible(!expand)
        binding.sheet.visible(expand)
        binding.scrim.visible(expand)

        onFabDismissedCallback.isEnabled = expand
    }

    private fun showAddHashtagDialog(timeline: Timeline.Hashtags? = null, tabPosition: Int = 0) {
        val frameLayout = FrameLayout(this)
        val padding = Utils.dpToPx(this, 8)
        frameLayout.updatePadding(left = padding, right = padding)

        val editText = AppCompatEditText(this)
        editText.setHint(R.string.edit_hashtag_hint)
        editText.text?.clear()
        frameLayout.addView(editText)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_hashtag_title)
            .setView(frameLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val input = editText.text.toString().trim()
                if (timeline == null) {
                    val newTab = TabViewData.from(Timeline.Hashtags(listOf(input)))
                    currentTabs.add(newTab)
                    currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
                } else {
                    currentTabs[tabPosition] = currentTabs[tabPosition].copy(
                        timeline = timeline.copy(tags = timeline.tags + input),
                    )

                    currentTabsAdapter.notifyItemChanged(tabPosition)
                }

                updateAvailableTabs()
                saveTabs()
            }
            .create()

        editText.doOnTextChanged { s, _, _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validateHashtag(s)
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validateHashtag(editText.text)
        editText.requestFocus()
    }

    private fun showSelectListDialog() {
        val adapter = object : ArrayAdapter<MastoList>(this, android.R.layout.simple_list_item_1) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                getItem(position)?.let { item -> (view as TextView).text = item.title }
                return view
            }
        }

        val selectListBinding = DialogSelectListBinding.inflate(layoutInflater, null, false)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_list_title)
            .setNeutralButton(R.string.select_list_manage, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setView(selectListBinding.root)
            .setAdapter(adapter) { _, position ->
                adapter.getItem(position)?.let { item ->
                    val newTab = TabViewData.from(Timeline.UserList(item.id, item.title))
                    currentTabs.add(newTab)
                    currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
                    updateAvailableTabs()
                    saveTabs()
                }
            }.create()

        selectListBinding.progressBar.show()

        // Set the "Manage lists" button listener after creating the dialog. This ensures
        // that clicking the button does not dismiss the dialog, so when the user returns
        // from managing the lists the dialog is still displayed.
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            button.setOnClickListener {
                startActivity(ListsActivityIntent(applicationContext))
            }
        }

        dialog.show()

        lifecycleScope.launch {
            listsRepository.lists.collect { result ->
                result.onSuccess { lists ->
                    if (lists is Lists.Loaded) {
                        selectListBinding.progressBar.hide()
                        adapter.clear()
                        adapter.addAll(lists.lists.sortedWith(compareByListTitle))
                        if (lists.lists.isEmpty()) selectListBinding.noLists.show()
                    }
                }

                result.onFailure {
                    selectListBinding.progressBar.hide()
                    dialog.dismiss()
                    Snackbar.make(binding.root, R.string.error_list_load, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun validateHashtag(input: CharSequence?): Boolean {
        val trimmedInput = input?.trim() ?: ""
        return trimmedInput.isNotEmpty() && hashtagRegex.matcher(trimmedInput).matches()
    }

    private fun updateAvailableTabs() {
        val addableTabs: MutableList<TabViewData> = mutableListOf()

        val homeTab = TabViewData.from(Timeline.Home)
        if (!currentTabs.contains(homeTab)) {
            addableTabs.add(homeTab)
        }
        val notificationTab = TabViewData.from(Timeline.Notifications)
        if (!currentTabs.contains(notificationTab)) {
            addableTabs.add(notificationTab)
        }
        val localTab = TabViewData.from(Timeline.PublicLocal)
        if (!currentTabs.contains(localTab)) {
            addableTabs.add(localTab)
        }
        val federatedTab = TabViewData.from(Timeline.PublicFederated)
        if (!currentTabs.contains(federatedTab)) {
            addableTabs.add(federatedTab)
        }
        val directMessagesTab = TabViewData.from(Timeline.Conversations)
        if (!currentTabs.contains(directMessagesTab)) {
            addableTabs.add(directMessagesTab)
        }
        val bookmarksTab = TabViewData.from(Timeline.Bookmarks)
        if (!currentTabs.contains(bookmarksTab)) {
            addableTabs.add(bookmarksTab)
        }
        val favouritesTab = TabViewData.from(Timeline.Favourites)
        if (!currentTabs.contains(favouritesTab)) {
            addableTabs.add(favouritesTab)
        }
        val trendingTagsTab = TabViewData.from(Timeline.TrendingHashtags)
        if (!currentTabs.contains(trendingTagsTab)) {
            addableTabs.add(trendingTagsTab)
        }
        val trendingLinksTab = TabViewData.from(Timeline.TrendingLinks)
        if (!currentTabs.contains(trendingLinksTab)) {
            addableTabs.add(trendingLinksTab)
        }
        val trendingStatusesTab = TabViewData.from(Timeline.TrendingStatuses)
        if (!currentTabs.contains(trendingStatusesTab)) {
            addableTabs.add(trendingStatusesTab)
        }

        addableTabs.add(TabViewData.from(Timeline.Hashtags(emptyList())))
        addableTabs.add(TabViewData.from(Timeline.UserList("", "")))

        addTabAdapter.updateData(addableTabs)
    }

    override fun onStartDelete(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startSwipe(viewHolder)
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startDrag(viewHolder)
    }

    private fun saveTabs() {
        accountManager.activeAccount?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                it.tabPreferences = currentTabs.map { it.timeline }
                accountManager.saveAccount(it)
            }
        }
        tabsChanged = true
    }

    override fun onPause() {
        super.onPause()
        if (tabsChanged) {
            lifecycleScope.launch {
                eventHub.dispatch(MainTabsChangedEvent(currentTabs.map { it.timeline }))
            }
        }
    }
}
