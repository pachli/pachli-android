/* Copyright 2020 Tusky Contributors
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

package app.pachli.components.announcements

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.PopupWindow
import androidx.activity.viewModels
import androidx.core.view.MenuProvider
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.R
import app.pachli.adapter.EmojiAdapter
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.BackgroundMessage
import app.pachli.databinding.ActivityAnnouncementsBinding
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Success
import app.pachli.view.EmojiPicker
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AnnouncementsActivity :
    ViewUrlActivity(),
    AnnouncementActionListener,
    MenuProvider {

    private val viewModel: AnnouncementsViewModel by viewModels()

    private val binding by viewBinding(ActivityAnnouncementsBinding::inflate)

    private lateinit var adapter: AnnouncementAdapter

    private val picker by unsafeLazy { EmojiPicker(this) }
    private val pickerDialog by unsafeLazy {
        PopupWindow(this)
            .apply {
                contentView = picker
                isFocusable = true
                setOnDismissListener {
                    currentAnnouncementId = null
                }
            }
    }
    private var currentAnnouncementId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        addMenuProvider(this)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_announcements)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshAnnouncements)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        binding.announcementsList.setHasFixedSize(true)
        binding.announcementsList.layoutManager = LinearLayoutManager(this)
        binding.announcementsList.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )

        val hideStatsInDetailedPosts = sharedPreferencesRepository.hideStatsInDetailedView
        val animateEmojis = sharedPreferencesRepository.animateEmojis
        val useAbsoluteTime = sharedPreferencesRepository.useAbsoluteTime

        adapter = AnnouncementAdapter(glide, emptyList(), this, hideStatsInDetailedPosts, animateEmojis, useAbsoluteTime)

        binding.announcementsList.adapter = adapter

        viewModel.announcements.observe(this) {
            when (it) {
                is Success -> {
                    binding.progressBar.hide()
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (it.data.isNullOrEmpty()) {
                        binding.errorMessageView.setup(BackgroundMessage.Empty(R.string.no_announcements))
                        binding.errorMessageView.show()
                    } else {
                        binding.errorMessageView.hide()
                    }
                    adapter.updateList(it.data ?: listOf())
                }
                is Loading -> {
                    binding.errorMessageView.hide()
                }
                is Error -> {
                    binding.progressBar.hide()
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.errorMessageView.setup(BackgroundMessage.GenericError()) {
                        refreshAnnouncements()
                    }
                    binding.errorMessageView.show()
                }
            }
        }

        viewModel.emojis.observe(this) {
            picker.adapter = EmojiAdapter(
                glide,
                it,
                animateEmojis,
                getString(R.string.label_emoji_no_category),
            ) {
                viewModel.addReaction(currentAnnouncementId!!, it)
                pickerDialog.dismiss()
            }
        }

        viewModel.load()
        binding.progressBar.show()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.activity_announcements, menu)
        menu.findItem(R.id.action_search)?.apply {
            icon = IconicsDrawable(this@AnnouncementsActivity, GoogleMaterial.Icon.gmd_search).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.includedToolbar.toolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        super.onMenuItemSelected(menuItem)
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                refreshAnnouncements()
                true
            }
            else -> false
        }
    }

    private fun refreshAnnouncements() {
        viewModel.load()
        binding.swipeRefreshLayout.isRefreshing = true
    }

    override fun openReactionPicker(announcementId: String, target: View) {
        currentAnnouncementId = announcementId
        pickerDialog.showAsDropDown(target)
    }

    override fun addReaction(announcementId: String, name: String) {
        viewModel.addReaction(announcementId, name)
    }

    override fun removeReaction(announcementId: String, name: String) {
        viewModel.removeReaction(announcementId, name)
    }

    override fun onViewTag(tag: String) {
        val intent = TimelineActivityIntent.hashtag(this, intent.pachliAccountId, tag)
        startActivityWithDefaultTransition(intent)
    }

    override fun onViewAccount(id: String) {
        viewAccount(intent.pachliAccountId, id)
    }

    override fun onViewUrl(url: String) {
        viewUrl(intent.pachliAccountId, url)
    }
}
