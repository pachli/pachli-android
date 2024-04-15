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
package app.pachli.fragment

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.R
import app.pachli.core.accounts.AccountManager
import app.pachli.core.activity.AccountSelectionListener
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.activity.PostLookupFallbackBehavior
import app.pachli.core.activity.openLink
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.designsystem.R as DR
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ReportActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.ViewMediaActivityIntent
import app.pachli.core.network.ServerOperation.ORG_JOINMASTODON_STATUSES_TRANSLATE
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Status
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.interfaces.StatusActionListener
import app.pachli.network.ServerRepository
import app.pachli.usecase.TimelineCases
import app.pachli.view.showMuteAccountDialog
import app.pachli.viewdata.IStatusViewData
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.snackbar.Snackbar
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class SFragment<T : IStatusViewData> : Fragment(), StatusActionListener<T> {
    protected abstract fun removeItem(viewData: T)

    private lateinit var bottomSheetActivity: BottomSheetActivity

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var timelineCases: TimelineCases

    @Inject
    lateinit var serverRepository: ServerRepository

    private var serverCanTranslate = false

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        requireActivity().overridePendingTransition(DR.anim.slide_from_right, DR.anim.slide_to_left)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bottomSheetActivity = if (context is BottomSheetActivity) {
            context
        } else {
            throw IllegalStateException("Fragment must be attached to a BottomSheetActivity!")
        }
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                serverRepository.flow.collect { result ->
                    result.onSuccess {
                        serverCanTranslate = it?.can(
                            operation = ORG_JOINMASTODON_STATUSES_TRANSLATE,
                            constraint = ">=1.0".toConstraint(),
                        ) ?: false
                    }
                    result.onFailure {
                        val msg = getString(
                            R.string.server_repository_error,
                            accountManager.activeAccount!!.domain,
                            it.msg(requireContext()),
                        )
                        Timber.e(msg)
                        try {
                            Snackbar.make(requireView(), msg, Snackbar.LENGTH_INDEFINITE)
                                .setAction(app.pachli.core.ui.R.string.action_retry) { serverRepository.retry() }
                                .show()
                        } catch (e: IllegalArgumentException) {
                            // On rare occasions this code is running before the fragment's
                            // view is connected to the parent. This causes Snackbar.make()
                            // to crash.  See https://issuetracker.google.com/issues/228215869.
                            // For now, swallow the exception.
                        }
                        serverCanTranslate = false
                    }
                }
            }
        }
    }

    protected fun openReblog(status: Status?) {
        if (status == null) return
        bottomSheetActivity.viewAccount(status.account.id)
    }

    protected fun viewThread(statusId: String?, statusUrl: String?) {
        bottomSheetActivity.viewThread(statusId!!, statusUrl)
    }

    protected fun viewAccount(accountId: String?) {
        bottomSheetActivity.viewAccount(accountId!!)
    }

    override fun onViewUrl(url: String) {
        bottomSheetActivity.viewUrl(url, PostLookupFallbackBehavior.OPEN_IN_BROWSER)
    }

    protected fun reply(status: Status) {
        val actionableStatus = status.actionableStatus
        val account = actionableStatus.account
        var loggedInUsername: String? = null
        val activeAccount = accountManager.activeAccount
        if (activeAccount != null) {
            loggedInUsername = activeAccount.username
        }
        val mentionedUsernames = LinkedHashSet(
            listOf(account.username) + actionableStatus.mentions.map { it.username },
        ).apply { remove(loggedInUsername) }

        val composeOptions = ComposeOptions(
            inReplyToId = status.actionableId,
            replyVisibility = actionableStatus.visibility,
            contentWarning = actionableStatus.spoilerText,
            mentionedUsernames = mentionedUsernames,
            replyingStatusAuthor = account.localUsername,
            replyingStatusContent = actionableStatus.content.parseAsMastodonHtml().toString(),
            language = actionableStatus.language,
            kind = ComposeOptions.ComposeKind.NEW,
        )

        val intent = ComposeActivityIntent(requireContext(), composeOptions)
        requireActivity().startActivity(intent)
    }

    /**
     * Handles the user clicking the "..." (more) button typically at the bottom-right of
     * the status.
     */
    protected fun more(view: View, viewData: T) {
        val status = viewData.status
        val actionableId = viewData.actionableId
        val accountId = viewData.actionable.account.id
        val accountUsername = viewData.actionable.account.username
        val statusUrl = viewData.actionable.url
        var loggedInAccountId: String? = null
        val activeAccount = accountManager.activeAccount
        if (activeAccount != null) {
            loggedInAccountId = activeAccount.accountId
        }
        val popup = PopupMenu(requireContext(), view)
        // Give a different menu depending on whether this is the user's own toot or not.
        val statusIsByCurrentUser = loggedInAccountId != null && loggedInAccountId == accountId
        if (statusIsByCurrentUser) {
            popup.inflate(R.menu.status_more_for_user)
            val menu = popup.menu
            when (status.visibility) {
                Status.Visibility.PUBLIC, Status.Visibility.UNLISTED -> {
                    menu.add(0, R.id.pin, 1, getString(if (status.isPinned()) R.string.unpin_action else R.string.pin_action))
                }
                Status.Visibility.PRIVATE -> {
                    val reblogged = status.reblog?.reblogged ?: status.reblogged
                    menu.findItem(R.id.status_reblog_private).isVisible = !reblogged
                    menu.findItem(R.id.status_unreblog_private).isVisible = reblogged
                }
                else -> {}
            }
        } else {
            popup.inflate(R.menu.status_more)
            popup.menu.findItem(R.id.status_download_media).isVisible = status.attachments.isNotEmpty()
            if (serverCanTranslate && canTranslate() && status.visibility != Status.Visibility.PRIVATE && status.visibility != Status.Visibility.DIRECT) {
                popup.menu.findItem(R.id.status_translate).isVisible = viewData.translationState == TranslationState.SHOW_ORIGINAL
                popup.menu.findItem(R.id.status_translate_undo).isVisible = viewData.translationState == TranslationState.SHOW_TRANSLATION
            } else {
                popup.menu.findItem(R.id.status_translate).isVisible = false
                popup.menu.findItem(R.id.status_translate_undo).isVisible = false
            }
        }
        val menu = popup.menu
        val openAsItem = menu.findItem(R.id.status_open_as)
        val openAsText = (activity as BaseActivity?)?.openAsText
        if (openAsText == null) {
            openAsItem.isVisible = false
        } else {
            openAsItem.title = openAsText
        }
        val muteConversationItem = menu.findItem(R.id.status_mute_conversation)
        val mutable = statusIsByCurrentUser || accountIsInMentions(activeAccount, status.mentions)
        muteConversationItem.isVisible = mutable
        if (mutable) {
            muteConversationItem.setTitle(
                if (status.muted != true) {
                    R.string.action_mute_conversation
                } else {
                    R.string.action_unmute_conversation
                },
            )
        }
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.post_share_content -> {
                    val statusToShare = status.reblog ?: status
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${statusToShare.account.username} - ${statusToShare.content.parseAsMastodonHtml()}",
                        )
                        putExtra(Intent.EXTRA_SUBJECT, statusUrl)
                    }
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            resources.getText(R.string.send_post_content_to),
                        ),
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.post_share_link -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, statusUrl)
                        type = "text/plain"
                    }
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            resources.getText(R.string.send_post_link_to),
                        ),
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.status_copy_link -> {
                    (requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).apply {
                        setPrimaryClip(ClipData.newPlainText(null, statusUrl))
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.status_open_as -> {
                    showOpenAsDialog(statusUrl, item.title)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_download_media -> {
                    requestDownloadAllMedia(status)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_mute -> {
                    onMute(accountId, accountUsername)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_block -> {
                    onBlock(accountId, accountUsername)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_report -> {
                    openReportPage(accountId, accountUsername, actionableId)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_unreblog_private -> {
                    onReblog(viewData, false)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_reblog_private -> {
                    onReblog(viewData, true)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_delete -> {
                    showConfirmDeleteDialog(viewData)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_delete_and_redraft -> {
                    showConfirmEditDialog(viewData)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_edit -> {
                    editStatus(actionableId, status)
                    return@setOnMenuItemClickListener true
                }
                R.id.pin -> {
                    lifecycleScope.launch {
                        timelineCases.pin(status.id, !status.isPinned()).onFailure { e: Throwable ->
                            val message = e.message
                                ?: getString(if (status.isPinned()) R.string.failed_to_unpin else R.string.failed_to_pin)
                            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.status_mute_conversation -> {
                    lifecycleScope.launch {
                        timelineCases.muteConversation(status.id, status.muted != true)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.status_translate -> {
                    onTranslate(viewData)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_translate_undo -> {
                    onTranslateUndo(viewData)
                    return@setOnMenuItemClickListener true
                }
            }
            false
        }
        popup.show()
    }

    /**
     * True if this class can translate statuses (assuming the server can). Superclasses should
     * override this if they support translating a status, and also override [onTranslate]
     * and [onTranslateUndo].
     */
    open fun canTranslate() = false

    open fun onTranslate(statusViewData: T) {}

    open fun onTranslateUndo(statusViewData: T) {}

    private fun onMute(accountId: String, accountUsername: String) {
        showMuteAccountDialog(this.requireActivity(), accountUsername) { notifications: Boolean?, duration: Int? ->
            lifecycleScope.launch {
                timelineCases.mute(accountId, notifications == true, duration)
            }
        }
    }

    private fun onBlock(accountId: String, accountUsername: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.dialog_block_warning, accountUsername))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    timelineCases.block(accountId)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    protected fun viewMedia(urlIndex: Int, attachments: List<AttachmentViewData>, view: View?) {
        val (attachment) = attachments[urlIndex]
        when (attachment.type) {
            Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                val intent = ViewMediaActivityIntent(requireContext(), attachments, urlIndex)
                if (view != null) {
                    val url = attachment.url
                    view.transitionName = url
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        view,
                        url,
                    )
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }
            Attachment.Type.UNKNOWN -> {
                requireContext().openLink(attachment.url)
            }
        }
    }

    protected fun viewTag(tag: String) {
        startActivity(TimelineActivityIntent.hashtag(requireContext(), tag))
    }

    private fun openReportPage(accountId: String, accountUsername: String, statusId: String) {
        startActivity(ReportActivityIntent(requireContext(), accountId, accountUsername, statusId))
    }

    private fun showConfirmDeleteDialog(viewData: T) {
        AlertDialog.Builder(requireActivity())
            .setMessage(R.string.dialog_delete_post_warning)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    val result = timelineCases.delete(viewData.status.id).exceptionOrNull()
                    if (result != null) {
                        Timber.w(result, "error deleting status")
                        Toast.makeText(context, app.pachli.core.ui.R.string.error_generic, Toast.LENGTH_SHORT).show()
                    }
                    // XXX: Removes the item even if there was an error. This is probably not
                    // correct (see similar code in showConfirmEditDialog() which only
                    // removes the item if the timelineCases.delete() call succeeded.
                    //
                    // Either way, this logic should be in the view model.
                    removeItem(viewData)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showConfirmEditDialog(statusViewData: T) {
        if (activity == null) {
            return
        }
        AlertDialog.Builder(requireActivity())
            .setMessage(R.string.dialog_redraft_post_warning)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    timelineCases.delete(statusViewData.status.id).fold(
                        { deletedStatus ->
                            removeItem(statusViewData)
                            val sourceStatus = if (deletedStatus.isEmpty()) {
                                statusViewData.status.toDeletedStatus()
                            } else {
                                deletedStatus
                            }
                            val composeOptions = ComposeOptions(
                                content = sourceStatus.text,
                                inReplyToId = sourceStatus.inReplyToId,
                                visibility = sourceStatus.visibility,
                                contentWarning = sourceStatus.spoilerText,
                                mediaAttachments = sourceStatus.attachments,
                                sensitive = sourceStatus.sensitive,
                                modifiedInitialState = true,
                                language = sourceStatus.language,
                                poll = sourceStatus.poll?.toNewPoll(sourceStatus.createdAt),
                                kind = ComposeOptions.ComposeKind.NEW,
                            )
                            startActivity(ComposeActivityIntent(requireContext(), composeOptions))
                        },
                        { error: Throwable? ->
                            Timber.w(error, "error deleting status")
                            Toast.makeText(context, app.pachli.core.ui.R.string.error_generic, Toast.LENGTH_SHORT)
                                .show()
                        },
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editStatus(id: String, status: Status) {
        lifecycleScope.launch {
            mastodonApi.statusSource(id).fold(
                { source ->
                    val composeOptions = ComposeOptions(
                        content = source.text,
                        inReplyToId = status.inReplyToId,
                        visibility = status.visibility,
                        contentWarning = source.spoilerText,
                        mediaAttachments = status.attachments,
                        sensitive = status.sensitive,
                        language = status.language,
                        statusId = source.id,
                        poll = status.poll?.toNewPoll(status.createdAt),
                        kind = ComposeOptions.ComposeKind.EDIT_POSTED,
                    )
                    startActivity(ComposeActivityIntent(requireContext(), composeOptions))
                },
                {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.error_status_source_load),
                        Snackbar.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    private fun showOpenAsDialog(statusUrl: String?, dialogTitle: CharSequence?) {
        if (statusUrl == null) {
            return
        }

        (activity as BaseActivity).apply {
            showAccountChooserDialog(
                dialogTitle,
                false,
                object : AccountSelectionListener {
                    override fun onAccountSelected(account: AccountEntity) {
                        openAsAccount(statusUrl, account)
                    }
                },
            )
        }
    }

    private fun downloadAllMedia(status: Status) {
        Toast.makeText(context, R.string.downloading_media, Toast.LENGTH_SHORT).show()
        val downloadManager = requireActivity().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        for ((_, url) in status.attachments) {
            val uri = Uri.parse(url)
            downloadManager.enqueue(
                DownloadManager.Request(uri).apply {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uri.lastPathSegment)
                },
            )
        }
    }

    private fun requestDownloadAllMedia(status: Status) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            (activity as BaseActivity).requestPermissions(permissions) { _, grantResults ->
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    downloadAllMedia(status)
                } else {
                    Toast.makeText(context, R.string.error_media_download_permission, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            downloadAllMedia(status)
        }
    }

    companion object {
        private fun accountIsInMentions(account: AccountEntity?, mentions: List<Status.Mention>): Boolean {
            return mentions.any { mention ->
                account?.username == mention.username && account.domain == Uri.parse(mention.url)?.host
            }
        }
    }
}
