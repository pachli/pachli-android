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
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.pachli.R
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.OpenUrlUseCase
import app.pachli.core.activity.ViewUrlActivity
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusItemViewData
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.OfflineFirstStatusRepository
import app.pachli.core.data.repository.ServerRepository
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.TranslationState
import app.pachli.core.domain.DownloadUrlUseCase
import app.pachli.core.model.Attachment
import app.pachli.core.model.IStatus
import app.pachli.core.model.Status
import app.pachli.core.model.asQuotePolicy
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.ComposeActivityIntent
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions
import app.pachli.core.navigation.ComposeActivityIntent.ComposeOptions.ReferencingStatus
import app.pachli.core.navigation.ReportActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.ViewMediaActivityIntent
import app.pachli.core.navigation.ViewThreadActivityIntent
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.ClipboardUseCase
import app.pachli.core.ui.StatusActionListener
import app.pachli.translation.TranslationService
import app.pachli.usecase.TimelineCases
import app.pachli.view.showMuteAccountDialog
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class SFragment<T : IStatusViewData> : Fragment(), StatusActionListener {
    protected abstract fun removeItem(viewData: IStatusViewData)

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var statusRepository: OfflineFirstStatusRepository

    @Inject
    lateinit var timelineCases: TimelineCases

    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var downloadUrlUseCase: DownloadUrlUseCase

    @Inject
    lateinit var clipboard: ClipboardUseCase

    @Inject
    lateinit var translationService: TranslationService

    @Inject
    lateinit var openUrl: OpenUrlUseCase

    protected abstract val pachliAccountId: Long

    @Deprecated("Use startActivityWithTransition or startActivityWithDefaultTransition")
    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context as? ViewUrlActivity) ?: throw IllegalStateException("Fragment must be attached to a BottomSheetActivity")
    }

    protected fun openReblog(status: IStatus) {
        val intent = AccountActivityIntent(requireActivity(), pachliAccountId, status.account.id)
        startActivityWithTransition(intent, TransitionKind.SLIDE_FROM_END)
    }

    protected fun viewThread(statusId: String, statusUrl: String?) {
        val intent = ViewThreadActivityIntent(requireActivity(), pachliAccountId, statusId, statusUrl)
        startActivityWithTransition(intent, TransitionKind.SLIDE_FROM_END)
    }

    protected fun viewAccount(accountId: String) {
        val intent = AccountActivityIntent(requireActivity(), pachliAccountId, accountId)
        startActivityWithTransition(intent, TransitionKind.SLIDE_FROM_END)
    }

    override fun onViewUrl(url: String) {
        (requireActivity() as? ViewUrlActivity)?.viewUrl(pachliAccountId, url)
    }

    protected fun reply(pachliAccountId: Long, status: Status) {
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
            referencingStatus = ReferencingStatus.ReplyingTo.from(status.actionableStatus),
            replyVisibility = actionableStatus.visibility,
            contentWarning = actionableStatus.spoilerText,
            mentionedUsernames = mentionedUsernames,
            language = actionableStatus.language,
            kind = ComposeOptions.ComposeKind.NEW,
        )

        val intent = ComposeActivityIntent(requireContext(), pachliAccountId, composeOptions)
        startActivityWithTransition(intent, TransitionKind.SLIDE_FROM_END)
    }

    /**
     * Launches ComposeActivity to quote [status].
     */
    protected fun quote(pachliAccountId: Long, status: Status) {
        val actionableStatus = status.actionableStatus

        val composeOptions = ComposeOptions(
            referencingStatus = ReferencingStatus.Quoting.from(actionableStatus),
            contentWarning = actionableStatus.spoilerText,
            language = actionableStatus.language,
            kind = ComposeOptions.ComposeKind.NEW,
        )

        val intent = ComposeActivityIntent(requireContext(), pachliAccountId, composeOptions)
        startActivityWithTransition(intent, TransitionKind.SLIDE_FROM_END)
    }

    /**
     * Handles the user clicking the "..." (more) button typically at the bottom-right of
     * the status.
     */
    override fun onMore(view: View, viewData: IStatusViewData) {
        val status = viewData.status
        val accountId = viewData.actionable.account.id
        var loggedInAccountId: String? = null
        val activeAccount = accountManager.activeAccount
        if (activeAccount != null) {
            loggedInAccountId = activeAccount.accountId
        }
        val popup = PopupMenu(requireContext(), view)
        // Give a different menu depending on whether this is the user's own status or not.
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
            if (translationService.canTranslate(viewData)) {
                popup.menu.findItem(R.id.status_translate).apply {
                    setTitle(translationService.labelResource)
                    isVisible = viewData.translationState == TranslationState.SHOW_ORIGINAL
                }
                popup.menu.findItem(R.id.status_translate_undo).isVisible = viewData.translationState == TranslationState.SHOW_TRANSLATION
            } else {
                popup.menu.findItem(R.id.status_translate).isVisible = false
                popup.menu.findItem(R.id.status_translate_undo).isVisible = false
            }
        }

        // Quoted posts that are solely attachments are difficult to click to view
        // the quoted post (clicks open the attachment or the profile). Provide a
        // menu option as an escape hatch.
        if (viewData !is StatusItemViewData || viewData.quotedViewData == null) {
            popup.menu.findItem(R.id.status_open_quoted_post)?.apply {
                isVisible = false
            }
        }

        val menu = popup.menu

        // "Open as..." appears if:
        // - If it's not a DIRECT message.
        // - It is a DIRECT message, and any of our other accounts are also mentioned.
        val openAsItem = menu.findItem(R.id.status_open_as)
        if (status.visibility != Status.Visibility.DIRECT || accountManager.accountsOrderedByActive.drop(1).any { accountIsInMentions(it, status.mentions) }) {
            val openAsText = (activity as BaseActivity?)?.openAsText
            if (openAsText == null) {
                openAsItem.isVisible = false
            } else {
                openAsItem.title = openAsText
            }
        } else {
            openAsItem.isVisible = false
        }

        menu.findItem(R.id.status_mute_conversation)?.isVisible = status.muted == false && (statusIsByCurrentUser || accountIsInMentions(activeAccount, status.mentions))
        menu.findItem(R.id.status_unmute_conversation)?.isVisible = status.muted == true && (statusIsByCurrentUser || accountIsInMentions(activeAccount, status.mentions))

        onPrepareMoreMenu(menu, viewData)

        popup.setOnMenuItemClickListener { item: MenuItem -> onMoreMenuItemClick(item, viewData) }
        popup.show()
    }

    /**
     * Called after the "..." (more) menu has been prepared but before it is
     * displayed.
     *
     * Subclasses may override this and change the visibility of items in
     * [menu], add items, or remove them.
     *
     * Subclasses that override this and add items should also override
     * [onMoreMenuItemClick] to handle clicks on the new items.
     *
     * @param menu Populated menu to modify.
     * @param viewData IStatusViewData for the current item.
     */
    protected open fun onPrepareMoreMenu(menu: Menu, viewData: IStatusViewData) = Unit

    /**
     * Handler for clicks on the "..." (more) menu. Subclasses that overode
     * [onPrepareMoreMenu] should also override this to handle the menu items they
     * added / made visible.
     *
     * After handling their items subclasses should call this implementation.
     *
     * @param item [MenuItem] that was clicked on.
     * @param viewData IStatusViewData for the current item.
     * @return True if the click was acted on, false otherwise.
     */
    @CallSuper
    protected open fun onMoreMenuItemClick(item: MenuItem, viewData: IStatusViewData): Boolean {
        val status = viewData.status
        val actionableId = viewData.actionableId
        val accountId = viewData.actionable.account.id
        val accountUsername = viewData.actionable.account.username
        val statusUrl = viewData.actionable.url

        return when (item.itemId) {
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
                startActivityWithDefaultTransition(
                    Intent.createChooser(
                        sendIntent,
                        resources.getText(R.string.send_post_content_to),
                    ),
                )
                true
            }

            R.id.post_share_link -> {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, statusUrl)
                    type = "text/plain"
                }
                startActivityWithDefaultTransition(
                    Intent.createChooser(
                        sendIntent,
                        resources.getText(R.string.send_post_link_to),
                    ),
                )
                true
            }

            R.id.status_copy_link -> {
                statusUrl?.let { clipboard.copyTextTo(it) }
                true
            }

            R.id.status_open_as -> {
                showOpenAsDialog(statusUrl, item.title)
                true
            }

            R.id.status_download_media -> {
                requestDownloadAllMedia(status)
                true
            }

            R.id.status_mute -> {
                onMute(accountId, accountUsername)
                true
            }

            R.id.status_block -> {
                onBlock(accountId, accountUsername)
                true
            }

            R.id.status_report -> {
                openReportPage(accountId, accountUsername, actionableId)
                true
            }

            R.id.status_unreblog_private -> {
                onReblog(viewData, false)
                true
            }

            R.id.status_reblog_private -> {
                onReblog(viewData, true)
                true
            }

            R.id.status_delete -> {
                showConfirmDeleteDialog(viewData)
                true
            }

            R.id.status_delete_and_redraft -> {
                showConfirmEditDialog(viewData)
                true
            }

            R.id.status_edit -> {
                editStatus(actionableId, status)
                true
            }

            R.id.pin -> {
                lifecycleScope.launch {
                    statusRepository.pin(pachliAccountId, status.actionableId, !status.isPinned()).onFailure { e ->
                        val message = e.fmt(requireContext())
                        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
                    }
                }
                true
            }

            R.id.status_mute_conversation -> {
                lifecycleScope.launch {
                    timelineCases.muteConversation(pachliAccountId, status.actionableId, true)
                }
                true
            }

            R.id.status_unmute_conversation -> {
                lifecycleScope.launch {
                    timelineCases.muteConversation(pachliAccountId, status.actionableId, false)
                }
                true
            }

            R.id.status_translate -> {
                onTranslate(viewData)
                true
            }

            R.id.status_translate_undo -> {
                onTranslateUndo(viewData)
                true
            }

            R.id.status_open_quoted_post -> {
                (viewData as? StatusItemViewData)?.let {
                    it.quotedViewData?.let {
                        viewThread(it.actionableId, it.actionable.url)
                    }
                }
                true
            }

            else -> false
        }
    }

    private fun onMute(accountId: String, accountUsername: String) {
        showMuteAccountDialog(this.requireActivity(), accountUsername) { notifications: Boolean?, duration: Int? ->
            lifecycleScope.launch {
                timelineCases.muteAccount(pachliAccountId, accountId, notifications == true, duration)
            }
        }
    }

    private fun onBlock(accountId: String, accountUsername: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.dialog_block_warning, accountUsername))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    timelineCases.blockAccount(pachliAccountId, accountId)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * @param owningUsername The username that "owns" this media. If this is media from a
     * status then this is the username that posted the status. If this is media from an
     * account (e.g., the account's avatar or header image) then this is the username of
     * that account.
     */
    protected fun viewMedia(owningUsername: String, urlIndex: Int, attachments: List<AttachmentViewData>, view: View?) {
        val attachment = attachments[urlIndex].attachment
        when (attachment.type) {
            Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                if (view == null) {
                    val intent = ViewMediaActivityIntent(requireContext(), pachliAccountId, owningUsername, attachments, urlIndex)
                    startActivityWithDefaultTransition(intent)
                    return
                }

                val (intent, options) = ViewMediaActivityIntent.withSharedElementTransition(
                    requireActivity(),
                    pachliAccountId,
                    owningUsername,
                    attachments,
                    urlIndex,
                    view,
                )

                startActivityWithDefaultTransition(intent, options)
            }

            Attachment.Type.UNKNOWN -> openUrl(attachment.url)
        }
    }

    protected fun viewTag(tag: String) {
        startActivityWithTransition(
            TimelineActivityIntent.hashtag(requireContext(), pachliAccountId, tag),
            TransitionKind.SLIDE_FROM_END,
        )
    }

    private fun openReportPage(accountId: String, accountUsername: String, statusId: String) {
        startActivityWithDefaultTransition(ReportActivityIntent(requireContext(), pachliAccountId, accountId, accountUsername, statusId))
    }

    private fun showConfirmDeleteDialog(viewData: IStatusViewData) {
        AlertDialog.Builder(requireActivity())
            .setMessage(R.string.dialog_delete_post_warning)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    timelineCases.delete(viewData.status.actionableId).onFailure {
                        Timber.w("error deleting status: %s", it)
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

    private fun showConfirmEditDialog(statusViewData: IStatusViewData) {
        activity ?: return
        AlertDialog.Builder(requireActivity())
            .setMessage(R.string.dialog_redraft_post_warning)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    timelineCases.delete(statusViewData.status.actionableId).onSuccess {
                        val sourceStatus = it.body.asModel()
                        val composeOptions = ComposeOptions(
                            content = sourceStatus.text,
                            referencingStatus = statusViewData.status.inReplyToId?.let {
                                ReferencingStatus.ReplyId(it)
                            } ?: statusViewData.status.quote?.let {
                                ReferencingStatus.QuoteId(it.statusId)
                            },
                            visibility = sourceStatus.visibility,
                            contentWarning = sourceStatus.spoilerText,
                            mediaAttachments = sourceStatus.attachments,
                            sensitive = sourceStatus.sensitive,
                            modifiedInitialState = true,
                            language = sourceStatus.language,
                            poll = sourceStatus.poll?.toNewPoll(sourceStatus.createdAt),
                            kind = ComposeOptions.ComposeKind.NEW,
                            quotePolicy = statusViewData.status.quoteApproval.asQuotePolicy(),
                        )
                        startActivityWithTransition(
                            ComposeActivityIntent(requireContext(), pachliAccountId, composeOptions),
                            TransitionKind.SLIDE_FROM_END,
                        )
                    }
                        .onFailure {
                            Timber.w("error deleting status: %s", it)
                            Toast.makeText(context, app.pachli.core.ui.R.string.error_generic, Toast.LENGTH_SHORT)
                                .show()
                        }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editStatus(id: String, status: Status) {
        lifecycleScope.launch {
            mastodonApi.statusSource(id).onSuccess {
                val source = it.body
                val composeOptions = ComposeOptions(
                    content = source.text,
                    referencingStatus = status.inReplyToId?.let {
                        ReferencingStatus.ReplyId(it)
                    } ?: status.quote?.let {
                        ReferencingStatus.QuoteId(it.statusId)
                    },
                    visibility = status.visibility,
                    contentWarning = source.spoilerText,
                    mediaAttachments = status.attachments,
                    sensitive = status.sensitive,
                    language = status.language,
                    statusId = source.id,
                    poll = status.poll?.toNewPoll(status.createdAt),
                    kind = ComposeOptions.ComposeKind.EDIT_POSTED,
                    quotePolicy = status.quoteApproval.asQuotePolicy(),
                )
                startActivityWithTransition(
                    ComposeActivityIntent(requireContext(), pachliAccountId, composeOptions),
                    TransitionKind.SLIDE_FROM_END,
                )
            }
                .onFailure {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.error_status_source_load),
                        Snackbar.LENGTH_SHORT,
                    ).show()
                }
        }
    }

    private fun showOpenAsDialog(statusUrl: String?, dialogTitle: CharSequence?) {
        if (statusUrl == null) {
            return
        }

        (activity as BaseActivity).apply {
            viewLifecycleOwner.lifecycleScope.launch {
                chooseAccount(dialogTitle, false)?.let { account ->
                    openAsAccount(statusUrl, account)
                }
            }
        }
    }

    private fun downloadAllMedia(status: Status) {
        Toast.makeText(context, R.string.downloading_media, Toast.LENGTH_SHORT).show()

        status.attachments.forEach {
            lifecycleScope.launch {
                downloadUrlUseCase(
                    it.url,
                    accountManager.activeAccount!!.fullName,
                    status.actionableStatus.account.username,
                )
            }
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

    override fun onViewMedia(pachliAccountId: Long, username: String, url: String) {
        startActivityWithTransition(
            ViewMediaActivityIntent(requireContext(), pachliAccountId, username, url),
            TransitionKind.SLIDE_FROM_END,
        )
    }

    /**
     * Shows a dialog to confirm the user wants to detatch the quote. If
     * confirmed, detaches the quote.
     *
     * On error shows a snackbar with the message.
     *
     * @param actionableQuoteId Actionable ID of the quoted status to remove.
     * @param actionableStatusId Actionable ID of the status quoting the post.
     */
    override fun onDetachQuote(actionableQuoteId: String, actionableStatusId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.detach_quote_dialog_title))
            .setMessage(getString(R.string.detach_quote_dialog_message))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    timelineCases.detachQuote(
                        pachliAccountId,
                        actionableQuoteId,
                        actionableStatusId,
                    ).onFailure { e ->
                        val message = getString(R.string.detach_quote_error_fmt, e.fmt(requireContext()))
                        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private fun accountIsInMentions(account: AccountEntity?, mentions: List<Status.Mention>): Boolean {
            return mentions.any { mention ->
                account?.username == mention.username && account.domain == mention.url.toUri().host
            }
        }
    }
}
