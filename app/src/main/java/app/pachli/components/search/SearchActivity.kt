/* Copyright 2017 Andrew Dawson
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

package app.pachli.components.search

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.R
import app.pachli.components.compose.ComposeAutoCompleteAdapter
import app.pachli.components.search.SearchOperator.DateOperator
import app.pachli.components.search.SearchOperator.DateOperator.DateChoice
import app.pachli.components.search.SearchOperator.FromOperator
import app.pachli.components.search.SearchOperator.FromOperator.FromKind.FromAccount
import app.pachli.components.search.SearchOperator.FromOperator.FromKind.FromMe
import app.pachli.components.search.SearchOperator.HasEmbedOperator
import app.pachli.components.search.SearchOperator.HasEmbedOperator.EmbedKind
import app.pachli.components.search.SearchOperator.HasLinkOperator
import app.pachli.components.search.SearchOperator.HasLinkOperator.LinkKind
import app.pachli.components.search.SearchOperator.HasMediaOperator
import app.pachli.components.search.SearchOperator.HasMediaOperator.HasMediaOption.HasMedia
import app.pachli.components.search.SearchOperator.HasMediaOperator.HasMediaOption.NoMedia
import app.pachli.components.search.SearchOperator.HasMediaOperator.HasMediaOption.SpecificMedia
import app.pachli.components.search.SearchOperator.HasMediaOperator.MediaKind
import app.pachli.components.search.SearchOperator.HasPollOperator
import app.pachli.components.search.SearchOperator.IsReplyOperator
import app.pachli.components.search.SearchOperator.IsReplyOperator.ReplyKind
import app.pachli.components.search.SearchOperator.IsSensitiveOperator
import app.pachli.components.search.SearchOperator.IsSensitiveOperator.SensitiveKind
import app.pachli.components.search.SearchOperator.LanguageOperator
import app.pachli.components.search.SearchOperator.WhereOperator
import app.pachli.components.search.SearchOperator.WhereOperator.WhereLocation
import app.pachli.components.search.SearchOperatorViewData.DateOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.FromOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.HasEmbedOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.HasLinkOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.HasMediaOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.HasPollOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.IsReplyOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.IsSensitiveOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.LanguageOperatorViewData
import app.pachli.components.search.SearchOperatorViewData.WhereOperatorViewData
import app.pachli.components.search.adapter.SearchPagerAdapter
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.toggleVisibility
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.data.model.Server
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_FROM
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE
import app.pachli.core.model.ServerOperation.ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.preferences.PrefKeys
import app.pachli.core.ui.extensions.await
import app.pachli.core.ui.extensions.awaitSingleChoiceItem
import app.pachli.core.ui.extensions.reduceSwipeSensitivity
import app.pachli.core.ui.makeIcon
import app.pachli.databinding.ActivitySearchBinding
import app.pachli.databinding.SearchOperatorAttachmentDialogBinding
import app.pachli.databinding.SearchOperatorDateDialogBinding
import app.pachli.databinding.SearchOperatorFromDialogBinding
import app.pachli.databinding.SearchOperatorWhereLocationDialogBinding
import com.github.michaelbull.result.get
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayoutMediator
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import io.github.z4kn4fein.semver.constraints.toConstraint
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchActivity :
    BottomSheetActivity(),
    MenuProvider,
    SearchView.OnQueryTextListener,
    ComposeAutoCompleteAdapter.AutocompletionProvider {
    private val viewModel: SearchViewModel by viewModels()

    private val binding by viewBinding(ActivitySearchBinding::inflate)

    private lateinit var searchView: SearchView

    private val showFilterIcon: Boolean
        get() = viewModel.availableOperators.value.isNotEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        addMenuProvider(this)
        setupPages()
        bindOperators()
        handleIntent(intent)
    }

    private fun setupPages() {
        binding.pages.reduceSwipeSensitivity()
        binding.pages.adapter = SearchPagerAdapter(this, intent.pachliAccountId)

        val enableSwipeForTabs = sharedPreferencesRepository.getBoolean(PrefKeys.ENABLE_SWIPE_FOR_TABS, true)
        binding.pages.isUserInputEnabled = enableSwipeForTabs

        TabLayoutMediator(binding.tabs, binding.pages) { tab, position ->
            tab.text = getPageTitle(position)
        }.attach()
    }

    /**
     * Binds the initial search operator chips UI and updates as the search
     * operators change.
     */
    @OptIn(ExperimentalBadgeUtils::class)
    private fun bindOperators() {
        val viewDataToChip: Map<Class<out SearchOperatorViewData<SearchOperator>>, Chip> = mapOf(
            DateOperatorViewData::class.java to binding.chipDate,
            FromOperatorViewData::class.java to binding.chipFrom,
            HasEmbedOperatorViewData::class.java to binding.chipHasEmbed,
            HasLinkOperatorViewData::class.java to binding.chipHasLink,
            HasMediaOperatorViewData::class.java to binding.chipHasMedia,
            HasPollOperatorViewData::class.java to binding.chipHasPoll,
            IsReplyOperatorViewData::class.java to binding.chipIsReply,
            IsSensitiveOperatorViewData::class.java to binding.chipIsSensitive,
            LanguageOperatorViewData::class.java to binding.chipLanguage,
            WhereOperatorViewData::class.java to binding.chipWhere,
        )

        // Chips are initially hidden, toggled by the "filter" button
        binding.chipsFilter.hide()
        binding.chipsFilter2.hide()
        binding.chipsFilter3.hide()

        // Badge to draw on the filter button if any filters are active.
        val filterBadgeDrawable = BadgeDrawable.create(this).apply {
            text = "!"
            backgroundColor = MaterialColors.getColor(binding.toolbar, com.google.android.material.R.attr.colorPrimary)
        }
        BadgeUtils.attachBadgeDrawable(filterBadgeDrawable, binding.toolbar, R.id.action_filter_search)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.server.collectLatest {
                        // Ignore errors for the moment
                        val server = it?.get() ?: return@collectLatest
                        bindDateChip(server)
                        bindFromChip(server)
                        bindHasMediaChip(server)
                        bindHasEmbedChip(server)
                        bindHasLinkChip(server)
                        bindHasPollChip(server)
                        bindIsReplyChip(server)
                        bindIsSensitiveChip(server)
                        bindLanguageChip(server)
                        bindWhereChip(server)
                    }
                }

                launch {
                    viewModel.availableOperators.collectLatest {
                        invalidateOptionsMenu()
                        setSearchViewWidth(showFilterIcon)
                    }
                }

                launch {
                    viewModel.operatorViewData.collectLatest { operators ->
                        var showFilterBadgeDrawable = false

                        operators.forEach { viewData ->
                            viewDataToChip[viewData::class.java]?.let { chip ->
                                showFilterBadgeDrawable = showFilterBadgeDrawable or (viewData.operator.choice != null)
                                chip.isChecked = viewData.operator.choice != null
                                chip.setCloseIconVisible(viewData.operator.choice != null)
                                chip.text = viewData.chipLabel(this@SearchActivity)
                            }
                        }

                        filterBadgeDrawable.setVisible(showFilterBadgeDrawable)
                        viewModel.search()
                    }
                }
            }
        }
    }

    /** Binds the chip for [HasMediaOperatorViewData]. */
    private fun bindHasMediaChip(server: Server) {
        val constraint = ">=1.0.0".toConstraint()
        val canHasMedia = server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_MEDIA, constraint)
        val canHasImage = server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_IMAGE, constraint)
        val canHasVideo = server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_VIDEO, constraint)
        val canHasAudio = server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_AUDIO, constraint)

        // Entire chip is hidden if there is no support for filtering by any kind of media.
        if (!canHasMedia && !canHasImage && !canHasVideo && !canHasAudio) {
            binding.chipHasMedia.hide()
            return
        }
        binding.chipHasMedia.show()

        binding.chipHasMedia.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(HasMediaOperator()))
        }

        binding.chipHasMedia.setOnClickListener {
            binding.chipHasMedia.toggle()

            lifecycleScope.launch {
                val dialogBinding = SearchOperatorAttachmentDialogBinding.inflate(layoutInflater, null, false)

                with(dialogBinding) {
                    // Disable individual groups/headings based on the type of media that
                    // searches can be filtered by
                    titleMedia.visible(canHasMedia)
                    chipgroupMedia.visible(canHasMedia)
                    mediaDivider.visible(canHasMedia && (canHasImage || canHasVideo || canHasAudio))

                    titleImage.visible(canHasImage)
                    chipgroupImages.visible(canHasImage)

                    titleVideo.visible(canHasVideo)
                    chipgroupVideo.visible(canHasVideo)

                    titleAudio.visible(canHasAudio)
                    chipgroupAudio.visible(canHasAudio)

                    // Turning on "No media" unchecks the images, video, and audio chips
                    chipNoMedia.setOnCheckedChangeListener { _, isChecked ->
                        if (!isChecked) return@setOnCheckedChangeListener

                        chipgroupImages.clearCheck()
                        chipgroupVideo.clearCheck()
                        chipgroupAudio.clearCheck()
                    }

                    // Turning on "With media" unchecks any more specific "With..." chips,
                    // as they're superfluous
                    chipHasMedia.setOnCheckedChangeListener { _, isChecked ->
                        if (!isChecked) return@setOnCheckedChangeListener

                        chipHasImage.isChecked = false
                        chipHasVideo.isChecked = false
                        chipHasAudio.isChecked = false
                    }

                    // Clear chipGroupMedia if one of the more specific chips is clicked.
                    // Clicking a specific "At least one" button always clears chipGroupMedia,
                    // as that's a more specific choice.
                    //
                    // Clicking a specific "None" button only clears chipGroupMedia if chipGroupMedia
                    // is also "None"; chipGroupMedia set to "At least one" with a specific media
                    // choice set to "None" is a valid user choice.
                    chipgroupImages.setOnCheckedStateChangeListener { _, checkedIds ->
                        if (checkedIds.contains(R.id.chip_has_image) ||
                            (checkedIds.contains(R.id.chip_no_image) && chipgroupMedia.checkedChipId == R.id.chip_no_media)
                        ) {
                            chipgroupMedia.clearCheck()
                        }
                    }
                    chipgroupVideo.setOnCheckedStateChangeListener { _, checkedIds ->
                        if (checkedIds.contains(R.id.chip_has_video) ||
                            (checkedIds.contains(R.id.chip_no_video) && chipgroupMedia.checkedChipId == R.id.chip_no_media)
                        ) {
                            chipgroupMedia.clearCheck()
                        }
                    }
                    chipgroupAudio.setOnCheckedStateChangeListener { _, checkedIds ->
                        if (checkedIds.contains(R.id.chip_has_audio) ||
                            (checkedIds.contains(R.id.chip_no_audio) && chipgroupMedia.checkedChipId == R.id.chip_no_media)
                        ) {
                            chipgroupMedia.clearCheck()
                        }
                    }
                }

                // Initialise the UI from the existing operator
                val choice = viewModel.getOperator<HasMediaOperator>()?.choice

                choice?.let { option ->
                    when (option) {
                        NoMedia -> dialogBinding.chipNoMedia.isChecked = true
                        is HasMedia -> {
                            dialogBinding.chipHasMedia.isChecked = true

                            option.exclude.forEach { exceptMedia ->
                                when (exceptMedia) {
                                    MediaKind.IMAGE -> dialogBinding.chipNoImage.isChecked = true
                                    MediaKind.VIDEO -> dialogBinding.chipNoVideo.isChecked = true
                                    MediaKind.AUDIO -> dialogBinding.chipNoAudio.isChecked = true
                                }
                            }
                        }

                        is SpecificMedia -> {
                            option.include.forEach { withMedia ->
                                when (withMedia) {
                                    MediaKind.IMAGE -> dialogBinding.chipHasImage.isChecked = true
                                    MediaKind.VIDEO -> dialogBinding.chipHasVideo.isChecked = true
                                    MediaKind.AUDIO -> dialogBinding.chipHasAudio.isChecked = true
                                }
                            }
                            option.exclude.forEach { exceptMedia ->
                                when (exceptMedia) {
                                    MediaKind.IMAGE -> dialogBinding.chipNoImage.isChecked = true
                                    MediaKind.VIDEO -> dialogBinding.chipNoVideo.isChecked = true
                                    MediaKind.AUDIO -> dialogBinding.chipNoAudio.isChecked = true
                                }
                            }
                        }
                    }
                }

                val button = AlertDialog.Builder(this@SearchActivity)
                    .setView(dialogBinding.root)
                    .setTitle(R.string.search_operator_attachment_dialog_title)
                    .create()
                    .await(android.R.string.ok, android.R.string.cancel)

                if (button == AlertDialog.BUTTON_POSITIVE) {
                    val option = if (dialogBinding.chipNoMedia.isChecked) {
                        NoMedia
                    } else {
                        if (dialogBinding.chipHasMedia.isChecked) {
                            val except = buildList {
                                dialogBinding.chipNoImage.isChecked && add(MediaKind.IMAGE)
                                dialogBinding.chipNoVideo.isChecked && add(MediaKind.VIDEO)
                                dialogBinding.chipNoAudio.isChecked && add(MediaKind.AUDIO)
                            }
                            HasMedia(exclude = except)
                        } else {
                            val include = buildList {
                                dialogBinding.chipHasImage.isChecked && add(MediaKind.IMAGE)
                                dialogBinding.chipHasVideo.isChecked && add(MediaKind.VIDEO)
                                dialogBinding.chipHasAudio.isChecked && add(MediaKind.AUDIO)
                            }
                            val exclude = buildList {
                                dialogBinding.chipNoImage.isChecked && add(MediaKind.IMAGE)
                                dialogBinding.chipNoVideo.isChecked && add(MediaKind.VIDEO)
                                dialogBinding.chipNoAudio.isChecked && add(MediaKind.AUDIO)
                            }
                            if (include.isEmpty() && exclude.isEmpty()) {
                                null
                            } else {
                                SpecificMedia(include = include, exclude = exclude)
                            }
                        }
                    }
                    viewModel.replaceOperator(SearchOperatorViewData.from(HasMediaOperator(option)))
                }
            }
        }
    }

    private fun bindDateChip(server: Server) {
        if (!server.can(ORG_JOINMASTODON_SEARCH_QUERY_BY_DATE, ">=1.0.0".toConstraint())) {
            binding.chipDate.hide()
            return
        }
        binding.chipDate.show()

        binding.chipDate.chipIcon = makeIcon(this, GoogleMaterial.Icon.gmd_date_range, IconicsSize.dp(24))
        binding.chipDate.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(DateOperator()))
        }

        binding.chipDate.setOnClickListener {
            binding.chipDate.toggle()

            lifecycleScope.launch {
                val dialogBinding = SearchOperatorDateDialogBinding.inflate(layoutInflater, null, false)
                val choice = viewModel.getOperator<DateOperator>()?.choice

                dialogBinding.radioGroup.check(
                    when (choice) {
                        null -> R.id.radioAll
                        DateChoice.Today -> R.id.radioLastDay
                        DateChoice.Last7Days -> R.id.radioLast7Days
                        DateChoice.Last30Days -> R.id.radioLast30Days
                        DateChoice.Last6Months -> R.id.radioLast6Months
                        is DateChoice.DateRange -> -1
                    },
                )

                val dialog = AlertDialog.Builder(this@SearchActivity)
                    .setView(dialogBinding.root)
                    .setTitle(R.string.search_operator_date_dialog_title)
                    .create()

                // Wait until the dialog is shown to set up the custom range button click
                // listener, as it needs a reference to the dialog to be able to dismiss
                // it if appropriate.
                dialog.setOnShowListener {
                    dialogBinding.buttonCustomRange.setOnClickListener {
                        launch {
                            val picker = MaterialDatePicker.Builder.dateRangePicker()
                                .setTitleText(R.string.search_operator_date_dialog_title)
                                .setCalendarConstraints(
                                    CalendarConstraints.Builder()
                                        .setValidator(DateValidatorPointBackward.now())
                                        // Default behaviour is to show two months, with the current month
                                        // at the top. This wastes space, as the user can't select beyond
                                        // the current month, so open one month earlier to show this month
                                        // and the previous month on screen.
                                        .setOpenAt(
                                            LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC).toEpochMilli(),
                                        )
                                        .build(),
                                )
                                .build()
                                .await(supportFragmentManager, "dateRangePicker")

                            picker ?: return@launch

                            val after = Instant.ofEpochMilli(picker.first).atOffset(ZoneOffset.UTC).toLocalDate()
                            val before = Instant.ofEpochMilli(picker.second).atOffset(ZoneOffset.UTC).toLocalDate()

                            viewModel.replaceOperator(SearchOperatorViewData.from(DateOperator(DateChoice.DateRange(after, before))))
                            dialog.dismiss()
                        }
                    }
                }

                val button = dialog.await(android.R.string.ok, android.R.string.cancel)

                if (button == AlertDialog.BUTTON_POSITIVE) {
                    val operator = when (dialogBinding.radioGroup.checkedRadioButtonId) {
                        R.id.radioLastDay -> DateChoice.Today
                        R.id.radioLast7Days -> DateChoice.Last7Days
                        R.id.radioLast30Days -> DateChoice.Last30Days
                        R.id.radioLast6Months -> DateChoice.Last6Months
                        else -> null
                    }
                    viewModel.replaceOperator(SearchOperatorViewData.from(DateOperator(operator)))
                }
            }
        }
    }

    private fun bindFromChip(server: Server) {
        val canFrom = server.can(ORG_JOINMASTODON_SEARCH_QUERY_FROM, ">=1.0.0".toConstraint())
        val canFromMe = server.can(ORG_JOINMASTODON_SEARCH_QUERY_FROM, ">=1.1.0".toConstraint())
        if (!canFrom) {
            binding.chipFrom.hide()
            return
        }
        binding.chipFrom.show()

        binding.chipFrom.chipIcon = makeIcon(this@SearchActivity, GoogleMaterial.Icon.gmd_person_search, IconicsSize.dp(24))
        binding.chipFrom.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(FromOperator()))
        }

        binding.chipFrom.setOnClickListener {
            binding.chipFrom.toggle()

            lifecycleScope.launch {
                val dialogBinding = SearchOperatorFromDialogBinding.inflate(layoutInflater, null, false)

                dialogBinding.radioMe.visible(canFromMe)
                dialogBinding.radioIgnoreMe.visible(canFromMe)

                // Initialise the UI from the existing operator
                when (val choice = viewModel.getOperator<FromOperator>()?.choice) {
                    null -> dialogBinding.radioGroup.check(R.id.radioAll)
                    is FromMe -> {
                        if (choice.ignore) {
                            dialogBinding.radioGroup.check(R.id.radioIgnoreMe)
                        } else {
                            dialogBinding.radioGroup.check(R.id.radioMe)
                        }
                    }

                    is FromAccount -> {
                        if (choice.ignore) {
                            dialogBinding.radioGroup.check(R.id.radioIgnoreOtherAccount)
                        } else {
                            dialogBinding.radioGroup.check(R.id.radioOtherAccount)
                        }
                        dialogBinding.account.setText(choice.account)
                    }
                }

                dialogBinding.account.setAdapter(
                    ComposeAutoCompleteAdapter(
                        this@SearchActivity,
                        animateAvatar = false,
                        animateEmojis = false,
                        showBotBadge = true,
                    ),
                )

                val dialog = AlertDialog.Builder(this@SearchActivity)
                    .setView(dialogBinding.root)
                    .setTitle(R.string.search_operator_from_dialog_title)
                    .create()

                // Configure UI that needs to refer to the create dialog.
                dialog.setOnShowListener {
                    /**
                     * Updates UI state when the user clicks on options.
                     *
                     * - Adjusts focus on the account entry view as necessary
                     * - Disables the Ok button if one of the "other account" options is chosen and
                     * another account hasn't been entered.
                     */
                    fun updateUi() {
                        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

                        val enabled = when (dialogBinding.radioGroup.checkedRadioButtonId) {
                            R.id.radioAll, R.id.radioMe, R.id.radioIgnoreMe -> {
                                dialogBinding.account.clearFocus()
                                true
                            }

                            R.id.radioOtherAccount, R.id.radioIgnoreOtherAccount -> {
                                dialogBinding.account.requestFocus()
                                val text = dialogBinding.account.text
                                text.isNotBlank() && text.length >= 2
                            }

                            else -> true
                        }
                        okButton.isEnabled = enabled
                    }

                    dialogBinding.radioGroup.setOnCheckedChangeListener { _, _ -> updateUi() }
                    dialogBinding.account.doAfterTextChanged {
                        // Typing another account should set the other account radio button if one of
                        // the two has not already been set. This ensures the user doesn't enter text
                        // and tap OK before changing one of the radio buttons, losing their text.
                        val checkedId = dialogBinding.radioGroup.checkedRadioButtonId
                        if (checkedId != R.id.radioOtherAccount && checkedId != R.id.radioIgnoreOtherAccount) {
                            dialogBinding.radioGroup.check(R.id.radioOtherAccount)
                        }

                        updateUi()
                    }
                }

                val button = dialog.await(android.R.string.ok, android.R.string.cancel)

                if (button == AlertDialog.BUTTON_POSITIVE) {
                    val operator = when {
                        dialogBinding.radioMe.isChecked -> FromOperator(FromMe(ignore = false))
                        dialogBinding.radioIgnoreMe.isChecked -> FromOperator(FromMe(ignore = true))
                        dialogBinding.radioOtherAccount.isChecked -> FromOperator(
                            FromAccount(
                                account =
                                dialogBinding.account.text.toString(),
                                ignore = false,
                            ),
                        )

                        dialogBinding.radioIgnoreOtherAccount.isChecked -> FromOperator(
                            FromAccount(
                                account = dialogBinding.account.text.toString(),
                                ignore = true,
                            ),
                        )

                        else -> FromOperator()
                    }
                    viewModel.replaceOperator(SearchOperatorViewData.from(operator))
                }
            }
        }
    }

    private fun bindLanguageChip(server: Server) {
        if (!server.can(ORG_JOINMASTODON_SEARCH_QUERY_LANGUAGE, ">=1.0.0".toConstraint())) {
            binding.chipLanguage.hide()
            return
        }
        binding.chipLanguage.show()

        binding.chipLanguage.chipIcon = makeIcon(this, GoogleMaterial.Icon.gmd_translate, IconicsSize.dp(24))
        binding.chipLanguage.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(LanguageOperator()))
        }
        val locales = listOf(null) + viewModel.locales.value
        val displayLanguages = locales.map {
            it?.displayLanguage ?: getString(R.string.search_operator_language_dialog_all)
        }

        binding.chipLanguage.setOnClickListener {
            binding.chipLanguage.toggle()

            lifecycleScope.launch {
                val choice = viewModel.getOperator<LanguageOperator>()?.choice
                val index = locales.indexOf(choice)

                val result = AlertDialog.Builder(this@SearchActivity)
                    .setTitle(R.string.search_operator_language_dialog_title)
                    .awaitSingleChoiceItem(
                        displayLanguages,
                        index,
                        android.R.string.ok,
                        android.R.string.cancel,
                    )
                if (result.button == AlertDialog.BUTTON_POSITIVE && result.index != -1) {
                    viewModel.replaceOperator(
                        SearchOperatorViewData.from(LanguageOperator(locales[result.index])),
                    )
                }
            }
        }
    }

    private fun bindHasLinkChip(server: Server) {
        if (!server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_LINK, ">=1.0.0".toConstraint())) {
            binding.chipHasLink.hide()
            return
        }
        binding.chipHasLink.show()

        binding.chipHasLink.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(HasLinkOperator()))
        }

        val options = listOf(
            null to R.string.search_operator_link_dialog_all,
            LinkKind.LINKS_ONLY to R.string.search_operator_link_dialog_only,
            LinkKind.NO_LINKS to R.string.search_operator_link_dialog_no_link,
        )

        val displayOptions = options.map { getString(it.second) }

        binding.chipHasLink.setOnClickListener {
            binding.chipHasLink.toggle()

            lifecycleScope.launch {
                val selectedOption = viewModel.getOperator<HasLinkOperator>()?.choice
                val index = options.indexOfFirst { it.first == selectedOption }

                val result = AlertDialog.Builder(this@SearchActivity)
                    .setTitle(R.string.search_operator_link_dialog_title)
                    .awaitSingleChoiceItem(
                        displayOptions,
                        index,
                        android.R.string.ok,
                        android.R.string.cancel,
                    )

                if (result.button == AlertDialog.BUTTON_POSITIVE && result.index != -1) {
                    viewModel.replaceOperator(
                        SearchOperatorViewData.from(HasLinkOperator(options[result.index].first)),
                    )
                }
            }
        }
    }

    private fun bindHasEmbedChip(server: Server) {
        if (!server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_EMBED, ">=1.0.0".toConstraint())) {
            binding.chipHasEmbed.hide()
            return
        }
        binding.chipHasEmbed.show()

        binding.chipHasEmbed.chipIcon = makeIcon(this, GoogleMaterial.Icon.gmd_photo_size_select_actual, IconicsSize.dp(24))
        binding.chipHasEmbed.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(HasEmbedOperator()))
        }

        val options = listOf(
            null to R.string.search_operator_embed_dialog_all,
            EmbedKind.EMBED_ONLY to R.string.search_operator_embed_dialog_only,
            EmbedKind.NO_EMBED to R.string.search_operator_embed_dialog_no_embeds,
        )

        val displayOptions = options.map { getString(it.second) }

        binding.chipHasEmbed.setOnClickListener {
            binding.chipHasEmbed.toggle()

            lifecycleScope.launch {
                val selectedOption = viewModel.getOperator<HasEmbedOperator>()?.choice
                val index = options.indexOfFirst { it.first == selectedOption }

                val result = AlertDialog.Builder(this@SearchActivity)
                    .setTitle(R.string.search_operator_embed_dialog_title)
                    .awaitSingleChoiceItem(
                        displayOptions,
                        index,
                        android.R.string.ok,
                        android.R.string.cancel,
                    )

                if (result.button == AlertDialog.BUTTON_POSITIVE && result.index != -1) {
                    viewModel.replaceOperator(
                        SearchOperatorViewData.from(HasEmbedOperator(options[result.index].first)),
                    )
                }
            }
        }
    }

    private fun bindHasPollChip(server: Server) {
        if (!server.can(ORG_JOINMASTODON_SEARCH_QUERY_HAS_POLL, ">=1.0.0".toConstraint())) {
            binding.chipHasPoll.hide()
            return
        }
        binding.chipHasPoll.show()

        binding.chipHasPoll.chipIcon = makeIcon(this, GoogleMaterial.Icon.gmd_poll, IconicsSize.dp(24))
        binding.chipHasPoll.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(HasPollOperator()))
        }

        val options = listOf(
            null to R.string.search_operator_poll_dialog_all,
            HasPollOperator.PollKind.POLLS_ONLY to R.string.search_operator_poll_dialog_only,
            HasPollOperator.PollKind.NO_POLLS to R.string.search_operator_poll_dialog_no_polls,
        )

        val displayOptions = options.map { getString(it.second) }

        binding.chipHasPoll.setOnClickListener {
            binding.chipHasPoll.toggle()

            lifecycleScope.launch {
                val choice = viewModel.getOperator<HasPollOperator>()?.choice
                val index = options.indexOfFirst { it.first == choice }

                val result = AlertDialog.Builder(this@SearchActivity)
                    .setTitle(R.string.search_operator_poll_dialog_title)
                    .awaitSingleChoiceItem(
                        displayOptions,
                        index,
                        android.R.string.ok,
                        android.R.string.cancel,
                    )

                if (result.button == AlertDialog.BUTTON_POSITIVE && result.index != -1) {
                    viewModel.replaceOperator(
                        SearchOperatorViewData.from(HasPollOperator(options[result.index].first)),
                    )
                }
            }
        }
    }

    private fun bindIsReplyChip(server: Server) {
        if (!server.can(ORG_JOINMASTODON_SEARCH_QUERY_IS_REPLY, ">=1.0.0".toConstraint())) {
            binding.chipIsReply.hide()
            return
        }
        binding.chipIsReply.show()

        binding.chipIsReply.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(IsReplyOperator()))
        }

        val options = listOf(
            null to R.string.search_operator_replies_dialog_all,
            ReplyKind.REPLIES_ONLY to R.string.search_operator_replies_dialog_replies_only,
            ReplyKind.NO_REPLIES to R.string.search_operator_replies_dialog_no_replies,
        )

        val displayOptions = options.map { getString(it.second) }

        binding.chipIsReply.setOnClickListener {
            binding.chipIsReply.toggle()

            lifecycleScope.launch {
                val choice = viewModel.getOperator<IsReplyOperator>()?.choice
                val index = options.indexOfFirst { it.first == choice }

                val result = AlertDialog.Builder(this@SearchActivity)
                    .setTitle(R.string.search_operator_replies_dialog_title)
                    .awaitSingleChoiceItem(
                        displayOptions,
                        index,
                        android.R.string.ok,
                        android.R.string.cancel,
                    )

                if (result.button == AlertDialog.BUTTON_POSITIVE && result.index != -1) {
                    viewModel.replaceOperator(
                        SearchOperatorViewData.from(IsReplyOperator(options[result.index].first)),
                    )
                }
            }
        }
    }

    private fun bindIsSensitiveChip(server: Server) {
        if (!server.can(ORG_JOINMASTODON_SEARCH_QUERY_IS_SENSITIVE, ">=1.0.0".toConstraint())) {
            binding.chipIsSensitive.hide()
            return
        }
        binding.chipIsSensitive.show()

        binding.chipIsSensitive.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(IsSensitiveOperator()))
        }

        val options = listOf(
            null to R.string.search_operator_sensitive_dialog_all,
            SensitiveKind.SENSITIVE_ONLY to R.string.search_operator_sensitive_dialog_sensitive_only,
            SensitiveKind.NO_SENSITIVE to R.string.search_operator_sensitive_dialog_no_sensitive,
        )

        val displayOptions = options.map { getString(it.second) }

        binding.chipIsSensitive.setOnClickListener {
            binding.chipIsSensitive.toggle()

            lifecycleScope.launch {
                val choice = viewModel.getOperator<IsSensitiveOperator>()?.choice
                val index = options.indexOfFirst { it.first == choice }

                val result = AlertDialog.Builder(this@SearchActivity)
                    .setTitle(R.string.search_operator_sensitive_dialog_title)
                    .awaitSingleChoiceItem(
                        displayOptions,
                        index,
                        android.R.string.ok,
                        android.R.string.cancel,
                    )

                if (result.button == AlertDialog.BUTTON_POSITIVE && result.index != -1) {
                    viewModel.replaceOperator(
                        SearchOperatorViewData.from(IsSensitiveOperator(options[result.index].first)),
                    )
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindWhereChip(server: Server) {
        val canInLibrary = server.can(ORG_JOINMASTODON_SEARCH_QUERY_IN_LIBRARY, ">=1.0.0".toConstraint())
        val canInPublic = server.can(ORG_JOINMASTODON_SEARCH_QUERY_IN_PUBLIC, ">=1.0.0".toConstraint())

        if (!canInLibrary && !canInPublic) {
            binding.chipWhere.hide()
            return
        }
        binding.chipWhere.show()

        binding.chipWhere.chipIcon = makeIcon(this, GoogleMaterial.Icon.gmd_find_in_page, IconicsSize.dp(24))
        binding.chipWhere.setOnCloseIconClickListener {
            viewModel.replaceOperator(SearchOperatorViewData.from(WhereOperator()))
        }

        binding.chipWhere.setOnClickListener {
            binding.chipWhere.toggle()

            lifecycleScope.launch {
                val choice = viewModel.getOperator<WhereOperator>()?.choice

                val dialogBinding = SearchOperatorWhereLocationDialogBinding.inflate(layoutInflater, null, false)

                dialogBinding.whereLibraryTitle.visible(canInLibrary)
                dialogBinding.whereLibraryDescription.visible(canInLibrary)

                dialogBinding.wherePublicTitle.visible(canInPublic)
                dialogBinding.wherePublicDescription.visible(canInPublic)

                dialogBinding.whereAllDialogGroup.check(
                    when (choice) {
                        null -> R.id.where_all_title
                        WhereLocation.LIBRARY -> R.id.where_library_title
                        WhereLocation.PUBLIC -> R.id.where_public_title
                    },
                )

                // Dispatch touch events on the descriptions to the view with
                // the radiobutton. Dispatching touch events ensures behaviour
                // -- like the ripple that appears if the view is long-pressed --
                // is retained.
                dialogBinding.whereAllDescription.setOnTouchListener { _, event ->
                    dialogBinding.whereAllTitle.onTouchEvent(event)
                }
                dialogBinding.whereLibraryDescription.setOnTouchListener { _, event ->
                    dialogBinding.whereLibraryTitle.onTouchEvent(event)
                }
                dialogBinding.wherePublicDescription.setOnTouchListener { _, event ->
                    dialogBinding.wherePublicTitle.onTouchEvent(event)
                }

                val button = AlertDialog.Builder(this@SearchActivity)
                    .setView(dialogBinding.root)
                    .setTitle(R.string.search_operator_where_dialog_title)
                    .create()
                    .await(android.R.string.ok, android.R.string.cancel)

                if (button == AlertDialog.BUTTON_POSITIVE) {
                    val location = when (dialogBinding.whereAllDialogGroup.checkedRadioButtonId) {
                        R.id.where_library_title -> WhereLocation.LIBRARY
                        R.id.where_public_title -> WhereLocation.PUBLIC
                        else -> null
                    }
                    viewModel.replaceOperator(SearchOperatorViewData.from(WhereOperator(location)))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.search_toolbar, menu)

        menu.findItem(R.id.action_filter_search)?.apply {
            icon = makeIcon(this@SearchActivity, GoogleMaterial.Icon.gmd_tune, IconicsSize.dp(20))
        }

        val searchViewMenuItem = menu.findItem(R.id.action_search)
        searchViewMenuItem.expandActionView()
        searchView = searchViewMenuItem.actionView as SearchView
        bindSearchView()
    }

    override fun onPrepareMenu(menu: Menu) {
        menu.findItem(R.id.action_filter_search)?.apply {
            isVisible = showFilterIcon
        }
        return super<BottomSheetActivity>.onPrepareMenu(menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_filter_search -> {
                binding.chipsFilter.toggleVisibility()
                binding.chipsFilter2.toggleVisibility()
                binding.chipsFilter3.toggleVisibility()
                true
            }

            else -> super.onMenuItemSelected(menuItem)
        }
    }

    private fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> getString(R.string.title_posts)
            1 -> getString(R.string.title_accounts)
            2 -> getString(app.pachli.core.ui.R.string.title_hashtags_dialog)
            else -> throw IllegalArgumentException("Unknown page index: $position")
        }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            searchView.clearFocus()
            viewModel.currentQuery = intent.getStringExtra(SearchManager.QUERY).orEmpty()
            viewModel.search()
        }
    }

    private fun bindSearchView() {
        searchView.setIconifiedByDefault(false)
        searchView.setSearchableInfo((getSystemService(Context.SEARCH_SERVICE) as? SearchManager)?.getSearchableInfo(componentName))

        setSearchViewWidth(showFilterIcon)

        // Keep text that was entered also when switching to a different tab (before the search is executed)
        searchView.setOnQueryTextListener(this)
        searchView.setQuery(viewModel.currentSearchFieldContent ?: "", false)

        // Only focus if the query is empty. This ensures that if the user is returning
        // to the search results after visiting a result the full list is available,
        // instead of being obscured by the keyboard.
        if (viewModel.currentQuery.isBlank()) searchView.requestFocus()
    }

    /**
     * Compute and set the width of [searchView].
     *
     * @param showingFilterIcon True if the filter icon is showing and the width should
     * be adjusted to account for this.
     */
    private fun setSearchViewWidth(showingFilterIcon: Boolean) {
        if (!this::searchView.isInitialized) return

        // SearchView has a bug. If it's displayed 'app:showAsAction="always"' it's too wide,
        // pushing other icons (including the options menu '...' icon) off the edge of the
        // screen.
        //
        // E.g., see:
        //
        // - https://stackoverflow.com/questions/41662373/android-toolbar-searchview-too-wide-to-move-other-items
        // - https://stackoverflow.com/questions/51525088/how-to-control-size-of-a-searchview-in-toolbar
        // - https://stackoverflow.com/questions/36976163/push-icons-away-when-expandig-searchview-in-android-toolbar
        // - https://issuetracker.google.com/issues/36976484
        //
        // The fix is to use 'app:showAsAction="ifRoom|collapseActionView"' and then immediately
        // expand it after inflating. That sets the width correctly.
        //
        // But if you do that code in AppCompatDelegateImpl activates, and when the user presses
        // the "Back" button the SearchView is first set to its collapsed state. The user has to
        // press "Back" again to exit the activity. This is clearly unacceptable.
        //
        // It appears to be impossible to override this behaviour on API level < 33.
        //
        // SearchView does allow you to specify the maximum width. So take the screen width,
        // subtract 48dp * iconCount (for the menu, filter, and back icons), convert to pixels, and use that.
        val iconCount = if (showingFilterIcon) 3 else 2
        val pxScreenWidth = resources.displayMetrics.widthPixels
        val pxBuffer = ((48 * iconCount) * resources.displayMetrics.density).toInt()
        searchView.maxWidth = pxScreenWidth - pxBuffer
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        viewModel.currentSearchFieldContent = newText

        return false
    }

    // Seach for autocomplete suggestions
    override suspend fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.searchAccountAutocompleteSuggestions(token)
    }
}
