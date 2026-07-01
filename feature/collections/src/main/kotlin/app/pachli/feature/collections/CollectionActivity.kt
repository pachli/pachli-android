/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.feature.collections

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewGroupCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.getOrNull
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.CollectionActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.ui.AlertSuspendDialogFragment
import app.pachli.core.ui.HashtagSpan
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.core.ui.extensions.setMinimumTouchTarget
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.makeIcon
import app.pachli.feature.collections.ICollectionViewModel.AccountAction
import app.pachli.feature.collections.ICollectionViewModel.UiAction
import app.pachli.feature.collections.databinding.ActivityCollectionBinding
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.google.android.material.appbar.AppBarLayout
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import dagger.hilt.android.AndroidEntryPoint
import kotlin.properties.Delegates
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Displays the details for a single [app.pachli.core.model.Collection] and its
 * members.
 */
@AndroidEntryPoint
class CollectionActivity : ViewUrlActivity() {
    private val binding by viewBinding(ActivityCollectionBinding::inflate)

    private val viewModel by viewModels<CollectionViewModel>()

    private var avatarRadius by Delegates.notNull<Int>()

    private val pachliAccountId by unsafeLazy { intent.pachliAccountId }

    /** True when the transition to this activity has completed. */
    private var transitionComplete = false

    private var ownerAccountId: String? = null

    private val accountClickListener: View.OnClickListener = {
        ownerAccountId?.let { ownerAccountId ->
            startActivityWithTransition(
                AccountActivityIntent(this, intent.pachliAccountId, ownerAccountId),
                TransitionKind.SLIDE_FROM_END,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)

        // Apply left/right insets to the main layout, but don't consume, so children
        // can set the top/bottom.
        binding.root.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
            consume = false,
        )
        // CollapsingToolbarLayout consumes all insets without passing them on to
        // children, which means toolbar never sees them. Override that behaviour
        // with a custom inset listener that does nothing but does not consume them.
        binding.collapsingToolbar.applyWindowInsets(consume = false, top = InsetType.PADDING)
        binding.toolbar.applyWindowInsets(top = InsetType.PADDING)

        binding.collapsingToolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
            this.scrollEffect = FadeChildScrollEffect
        }

        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.title_collection)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbar.setOnClickListener {
            binding.fragmentContainer.getFragment<CollectionFragment?>()?.onReselect()
        }

        val collectionId = CollectionActivityIntent.getCollectionId(intent)

        val fragmentTag = CollectionFragment.fragmentTag(pachliAccountId, collectionId)

        val fragment = supportFragmentManager.findFragmentByTag(fragmentTag) as CollectionFragment?
            ?: CollectionFragment.newInstance(pachliAccountId, collectionId)

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, fragmentTag)
        }

        avatarRadius = binding.avatar.context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)

        binding.avatar.setOnClickListener(accountClickListener)
        binding.avatarBadge.setOnClickListener(accountClickListener)
        binding.roleChipGroup.setOnClickListener(accountClickListener)
        binding.displayName.setOnClickListener(accountClickListener)
        binding.username.setOnClickListener(accountClickListener)
        binding.accountPronouns.setOnClickListener(accountClickListener)

        supportPostponeEnterTransition()

        bind()

        viewModel.accept(UiAction.GetCollection(pachliAccountId, collectionId))
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.collectionViewData.collectLatest {
                    bindCollectionViewData(it)

                    // Delay transition until the first data load.
                    if (!transitionComplete) {
                        transitionComplete = true
                        supportStartPostponedEnterTransition()
                    }
                }
            }
        }
    }

    private fun bindCollectionViewData(result: Result<Loadable<ICollectionViewModel.CollectionViewData>, ICollectionViewModel.UiError.GetCollection>) {
        // Only update on success
        // TODO: Show error state.
        val viewData = result.get()?.getOrNull() ?: return

        val collection = viewData.collection
        ownerAccountId = collection.accountId

        binding.collectionName.text = collection.name.unicodeWrap()

        if (collection.description.isBlank()) {
            binding.collectionDescription.hide()
        } else {
            binding.collectionDescription.text = collection.description.unicodeWrap()
            binding.collectionDescription.show()
        }

        val shallowTag = collection.hashtag
        if (shallowTag == null || shallowTag.name.isBlank()) {
            binding.collectionHashtag.hide()
            binding.root.touchDelegate = null
            binding.collectionHashtag.setOnClickListener(null)
        } else {
            val spannable = SpannableString("#${shallowTag.name}")
            val hashtagSpan = HashtagSpan(shallowTag.name, viewModel.uiOptions.value.linksToUnderline.contains(LinksToUnderline.HASHTAGS), shallowTag.url, null)
            spannable.setSpan(hashtagSpan, 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.collectionHashtag.text = spannable

            binding.collectionHashtag.setMinimumTouchTarget()
            binding.collectionHashtag.setOnClickListener {
                val intent = TimelineActivityIntent.hashtag(this, intent.pachliAccountId, shallowTag.name)
                startActivityWithDefaultTransition(intent)
            }
            binding.collectionHashtag.show()
        }

        val owner = viewData.owner
        if (owner == null) {
            binding.avatar.setImageDrawable(AppCompatResources.getDrawable(binding.avatar.context, DR.drawable.avatar_default))
            binding.avatarBadge.hide()
            binding.displayName.text = "?"
            binding.username.text = "?"
            binding.accountPronouns.hide()
        } else {
            loadAvatar(
                glide,
                owner.account.avatar,
                binding.avatar,
                avatarRadius,
                viewModel.uiOptions.value.animateAvatars,
            )
            binding.avatarBadge.visible(owner.account.bot && viewModel.uiOptions.value.showBotOverlay)

            binding.roleChipGroup.setRoles(owner.account.roles)

            binding.displayName.text = owner.account.name.unicodeWrap().emojify(
                glide,
                owner.account.emojis,
                binding.displayName,
                viewModel.uiOptions.value.animateEmojis,
            )
            binding.username.text = getString(
                DR.string.post_username_format,
                owner.account.username,
            )
            if (viewModel.uiOptions.value.showPronouns) {
                binding.accountPronouns.text = owner.account.pronouns
                binding.accountPronouns.show()
            } else {
                binding.accountPronouns.hide()
            }
        }

        with(binding.collectionDiscoverable) {
            if (collection.discoverable) {
                text = context.getString(app.pachli.core.ui.R.string.collection_discoverable_true_label)
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_public, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            } else {
                text = context.getString(app.pachli.core.ui.R.string.collection_discoverable_false_label)
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_lock, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            }
        }

        with(binding.collectionSensitive) {
            if (collection.sensitive) {
                text = context.getString(app.pachli.core.ui.R.string.collection_sensitive_label)
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_visibility_off, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                show()
            } else {
                hide()
            }
        }

        viewData.isMember?.let { account ->
            binding.collectionRemoveSelf.setOnClickListener {
                lifecycleScope.launch {
                    val button = newConfirmRevokeDialogFragment().await(supportFragmentManager)
                    if (button == AlertDialog.BUTTON_POSITIVE) {
                        binding.collectionRemoveSelf.hide()
                        viewModel.accept(
                            AccountAction.Revoke(
                                pachliAccountId = pachliAccountId,
                                collection = collection,
                                account = account,
                            ),
                        )
                    }
                }
            }
            binding.collectionRemoveSelf.show()
        } ?: binding.collectionRemoveSelf.hide()
    }
}

/**
 * Creates a dialog allowing the user to confirm their account should
 * be removed from a collection.
 */
fun Context.newConfirmRevokeDialogFragment() = AlertSuspendDialogFragment.newInstance(
    title = getString(R.string.title_confirm_collection_revoke),
    message = getString(R.string.confirm_collection_revoke_msg),
    positiveText = getString(android.R.string.ok),
    negativeText = getString(android.R.string.cancel),
)

internal fun Activity.newConfirmUnfollowAccountDialogFragment() = AlertSuspendDialogFragment.newInstance(
    title = null, // getString(R.string.title_confirm_collection_revoke),
    message = getString(app.pachli.core.ui.R.string.dialog_unfollow_warning),
    positiveText = getString(android.R.string.ok),
    negativeText = getString(android.R.string.cancel),
)
