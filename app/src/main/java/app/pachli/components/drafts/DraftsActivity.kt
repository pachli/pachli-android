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

package app.pachli.components.drafts

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewGroupCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.R
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.database.model.DraftEntity
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.network.retrofit.apiresult.ClientError
import app.pachli.core.ui.BackgroundMessage
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.ActivityDraftsBinding
import app.pachli.db.DraftsAlert
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class DraftsActivity : BaseActivity(), DraftActionListener {

    @Inject
    lateinit var draftsAlert: DraftsAlert

    private val viewModel: DraftsViewModel by viewModels()

    private val binding by viewBinding(ActivityDraftsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.includedToolbar.appbar.applyDefaultWindowInsets()
        binding.includedToolbar.toolbar.addScrollEffect(FadeChildScrollEffect)
        binding.draftsRecyclerView.applyDefaultWindowInsets()

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_drafts)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.draftsErrorMessageView.setup(BackgroundMessage.Empty(R.string.no_drafts))

        val adapter = DraftsAdapter(glide, this)

        binding.draftsRecyclerView.adapter = adapter
        binding.draftsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.draftsRecyclerView.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )

        lifecycleScope.launch {
            viewModel.drafts.collectLatest { draftData ->
                adapter.submitData(draftData)
            }
        }

        adapter.addLoadStateListener {
            binding.draftsErrorMessageView.visible(adapter.itemCount == 0)
        }

        // If a failed post is saved to drafts while this activity is up, do nothing; the user is already in the drafts view.
        draftsAlert.observeInContext(this, false)
    }

    override fun onOpenDraft(draft: DraftEntity) {
        if (draft.inReplyToId == null) {
            openDraftWithoutReply(draft)
            return
        }

        val context = this as Context

        lifecycleScope.launch {
            viewModel.getStatus(draft.inReplyToId!!)
                .onSuccess {
                    val status = it.body.asModel()
                    val composeOptions = ComposeOptions(
                        draftId = draft.id,
                        content = draft.content,
                        contentWarning = draft.contentWarning,
                        inReplyTo = ComposeOptions.InReplyTo.Status.from(status),
                        draftAttachments = draft.attachments,
                        poll = draft.poll,
                        sensitive = draft.sensitive,
                        visibility = draft.visibility,
                        scheduledAt = draft.scheduledAt,
                        language = draft.language,
                        statusId = draft.statusId,
                        kind = ComposeOptions.ComposeKind.EDIT_DRAFT,
                    )

                    startActivity(ComposeActivityIntent(context, intent.pachliAccountId, composeOptions))
                }.onFailure { error ->
                    Timber.w("failed loading reply information: %s", error)

                    if (error is ClientError.NotFound) {
                        // the original status to which a reply was drafted has been deleted
                        // let's open the ComposeActivity without reply information
                        Toast.makeText(context, getString(R.string.drafts_post_reply_removed), Toast.LENGTH_LONG).show()
                        openDraftWithoutReply(draft)
                    } else {
                        Snackbar.make(binding.root, getString(R.string.drafts_failed_loading_reply), Snackbar.LENGTH_SHORT)
                            .show()
                    }
                }
        }
    }

    private fun openDraftWithoutReply(draft: DraftEntity) {
        val composeOptions = ComposeOptions(
            draftId = draft.id,
            content = draft.content,
            contentWarning = draft.contentWarning,
            draftAttachments = draft.attachments,
            poll = draft.poll,
            sensitive = draft.sensitive,
            visibility = draft.visibility,
            scheduledAt = draft.scheduledAt,
            language = draft.language,
            statusId = draft.statusId,
            kind = ComposeOptions.ComposeKind.EDIT_DRAFT,
        )

        startActivity(ComposeActivityIntent(this, intent.pachliAccountId, composeOptions))
    }

    override fun onDeleteDraft(draft: DraftEntity) {
        viewModel.deleteDraft(draft)
        Snackbar.make(binding.root, getString(R.string.draft_deleted), Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) {
                viewModel.restoreDraft(draft)
            }
            .show()
    }
}
