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
import app.pachli.BottomSheetActivity
import app.pachli.R
import app.pachli.adapter.EmojiAdapter
import app.pachli.adapter.OnEmojiSelectedListener
import app.pachli.core.navigation.StatusListActivityIntent
import app.pachli.core.preferences.PrefKeys
import app.pachli.databinding.ActivityAnnouncementsBinding
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Success
import app.pachli.util.hide
import app.pachli.util.show
import app.pachli.util.unsafeLazy
import app.pachli.util.viewBinding
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
    BottomSheetActivity(),
    AnnouncementActionListener,
    OnEmojiSelectedListener,
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

        val wellbeingEnabled = sharedPreferencesRepository.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false)
        val animateEmojis = sharedPreferencesRepository.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        val useAbsoluteTime = sharedPreferencesRepository.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false)

        adapter = AnnouncementAdapter(emptyList(), this, wellbeingEnabled, animateEmojis, useAbsoluteTime)

        binding.announcementsList.adapter = adapter

        viewModel.announcements.observe(this) {
            when (it) {
                is Success -> {
                    binding.progressBar.hide()
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (it.data.isNullOrEmpty()) {
                        binding.errorMessageView.setup(R.drawable.elephant_friend_empty, R.string.no_announcements)
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
                    binding.errorMessageView.setup(R.drawable.errorphant_error, R.string.error_generic) {
                        refreshAnnouncements()
                    }
                    binding.errorMessageView.show()
                }
            }
        }

        viewModel.emojis.observe(this) {
            picker.adapter = EmojiAdapter(it, this, animateEmojis)
        }

        viewModel.load()
        binding.progressBar.show()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_announcements, menu)
        menu.findItem(R.id.action_search)?.apply {
            icon = IconicsDrawable(this@AnnouncementsActivity, GoogleMaterial.Icon.gmd_search).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.includedToolbar.toolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
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

    override fun onEmojiSelected(shortcode: String) {
        viewModel.addReaction(currentAnnouncementId!!, shortcode)
        pickerDialog.dismiss()
    }

    override fun addReaction(announcementId: String, name: String) {
        viewModel.addReaction(announcementId, name)
    }

    override fun removeReaction(announcementId: String, name: String) {
        viewModel.removeReaction(announcementId, name)
    }

    override fun onViewTag(tag: String) {
        val intent = StatusListActivityIntent.hashtag(this, tag)
        startActivityWithSlideInAnimation(intent)
    }

    override fun onViewAccount(id: String) {
        viewAccount(id)
    }

    override fun onViewUrl(url: String) {
        viewUrl(url)
    }
}
