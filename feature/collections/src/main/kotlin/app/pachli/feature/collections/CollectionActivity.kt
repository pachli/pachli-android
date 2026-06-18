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

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewGroupCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.repository.Loadable
import app.pachli.core.data.repository.getOrNull
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.ICollection
import app.pachli.core.navigation.CollectionActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.makeIcon
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
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbar.setOnClickListener {
            binding.fragmentContainer.getFragment<CollectionFragment?>()?.onReselect()
        }

        val pachliAccountId = intent.pachliAccountId
        val collection = CollectionActivityIntent.getCollection(intent)
        val collectionId = collection.serverId

        val fragmentTag = CollectionFragment.fragmentTag(pachliAccountId, collectionId)

        val fragment =
            supportFragmentManager.findFragmentByTag(fragmentTag) as CollectionFragment?
                ?: CollectionFragment.newInstance(pachliAccountId, collectionId)

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, fragmentTag)
        }

        avatarRadius = binding.avatar.context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)

        // Populate the UI from the data in the intent. It will update with fresh
        // data from the repository when the fetch completes.
        bindCollection(collection)

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.collectionViewData.collectLatest(::bindCollectionViewData)
            }
        }
    }

    private fun bindCollectionViewData(result: Result<Loadable<ICollectionViewModel.CollectionViewData>, ICollectionViewModel.UiError.GetCollection>) {
        // Only update on success
        val collectionViewData = result.get()?.getOrNull() ?: return
        bindCollection(collectionViewData.collection)

        val owner = collectionViewData.owner
        if (owner == null) {
            binding.avatar.setImageDrawable(AppCompatResources.getDrawable(binding.avatar.context, DR.drawable.avatar_default))
            binding.avatarBadge.hide()
            binding.displayName.text = "?"
            binding.username.text = "?"
            binding.accountPronouns.hide()
            return
        }

        loadAvatar(
            glide,
            owner.account.avatar,
            binding.avatar,
            avatarRadius,
            viewModel.uiOptions.value.animateAvatars,
        )
        binding.avatarBadge.visible(owner.account.bot && viewModel.uiOptions.value.showBotOverlay)
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

        binding.collectionRemoveSelf.visible(collectionViewData.isMember)
    }

    private fun bindCollection(collection: ICollection) {
        supportActionBar?.title = collection.name

        // TODO: Make description clickable (links, mentions, hashtags)
        binding.collectionDescription.text = collection.description

        with(binding.collectionDiscoverable) {
            if (collection.discoverable) {
                text = "Discoverable"
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_public, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            } else {
                text = "Private"
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_lock, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            }
        }

        with(binding.collectionSensitive) {
            if (collection.sensitive) {
                text = "Sensitive"
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_visibility_off, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            } else {
                text = "Not sensitive"
                val icon = makeIcon(context, GoogleMaterial.Icon.gmd_visibility, textSize.toInt())
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            }
        }
    }
}
