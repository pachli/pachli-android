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

import androidx.annotation.StringRes
import app.pachli.core.common.PachliError
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.repository.Loadable
import app.pachli.core.model.Account
import app.pachli.core.model.ICollection
import app.pachli.core.preferences.LinksToUnderline
import app.pachli.core.preferences.PronounDisplay
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface ICollectionViewModel {
    sealed interface UiAction {
        /** Get the most recent version of [collectionId] from the server. */
        data class Refresh(
            val pachliAccountId: Long,
            val collectionId: String,
        ) : UiAction

        data class GetCollection(
            val pachliAccountId: Long,
            val collectionId: String,
        ) : UiAction
    }

    sealed interface NavigationAction : UiAction {
        data class ViewAccount(val accountId: String) : NavigationAction
        data class ViewHashtag(val hashtag: String) : NavigationAction
        data class ViewUrl(val url: String) : NavigationAction

        data class ConfirmCollectionRevoke(val action: AccountAction.Revoke) : NavigationAction

        data class ConfirmUnfollowAccount(val action: AccountAction.UnfollowAccount) : NavigationAction
    }

    /** Actions that operate on the collection. */
    sealed interface CollectionAction : UiAction {
        val pachliAccountId: Long
        val collectionId: String
    }

    /** Actions that operate on an account in the collection. */
    sealed interface AccountAction : UiAction {
        val pachliAccountId: Long
        val account: Account

        data class FollowAccount(
            override val pachliAccountId: Long,
            override val account: Account,
        ) : AccountAction

        data class UnfollowAccount(
            override val pachliAccountId: Long,
            override val account: Account,
        ) : AccountAction

        data class CancelFollowRequest(
            override val pachliAccountId: Long,
            override val account: Account,
        ) : AccountAction

        data class UnblockAccount(
            override val pachliAccountId: Long,
            override val account: Account,
        ) : AccountAction

        data class UnblockDomain(
            override val pachliAccountId: Long,
            override val account: Account,
        ) : AccountAction

        data class UnmuteAccount(
            override val pachliAccountId: Long,
            override val account: Account,
        ) : AccountAction

        /** Remove the user from the collection. */
        data class Revoke(
            override val pachliAccountId: Long,
            override val account: Account,
            val collection: ICollection,
        ) : AccountAction
    }

    sealed interface UiSuccess {
        val action: UiAction

        data class Revoke(override val action: AccountAction.Revoke) : UiSuccess

        data class FollowAccount(override val action: AccountAction.FollowAccount) : UiSuccess
        data class UnfollowAccount(override val action: AccountAction.UnfollowAccount) : UiSuccess

        companion object {
            fun from(action: AccountAction) = when (action) {
                is AccountAction.Revoke -> Revoke(action)
                is AccountAction.FollowAccount -> FollowAccount(action)
                is AccountAction.UnfollowAccount -> UnfollowAccount(action)
                is AccountAction.CancelFollowRequest -> TODO()
                is AccountAction.UnblockAccount -> TODO()
                is AccountAction.UnblockDomain -> TODO()
                is AccountAction.UnmuteAccount -> TODO()
            }
        }
    }

    /**
     * @property collection
     * @property owner [AccountViewData] for the owner of the collection. If null
     * the owner's account data could not be fetched.
     * @property accounts [AccountViewData] for each account in the collection.
     * @property isMember If non-null, the user is a member of this collection,
     * and [isMember] is their account. If null the user is not a member of
     * the collection.
     */
    data class CollectionViewData(
        val collection: ICollection,
        val owner: AccountViewData?,
        val accounts: List<AccountViewData>,
        val isMember: Account?,
    )

    sealed class UiError(
        @StringRes override val resourceId: Int,
        open val action: UiAction,
        override val cause: PachliError,
        override val formatArgs: Array<out String>? = null,
    ) : PachliError {
        @JvmInline
        value class GetCollection(private val error: PachliError) : PachliError by error

        data class FollowAccount(
            override val action: AccountAction.FollowAccount,
            override val cause: PachliError,
        ) : UiError(
            app.pachli.core.ui.R.string.ui_error_follow_account_fmt,
            action,
            cause,
            arrayOf(action.account.name),
        )

        data class UnfollowAccount(
            override val action: AccountAction.UnfollowAccount,
            override val cause: PachliError,
        ) : UiError(
            app.pachli.core.ui.R.string.ui_error_unfollow_account_fmt,
            action,
            cause,
            arrayOf(action.account.name),
        )

        data class Revoke(
            override val action: AccountAction.Revoke,
            override val cause: PachliError,
        ) : UiError(R.string.ui_error_collection_revoke_fmt, action, cause)

        companion object {
            fun make(error: PachliError, action: AccountAction) = when (action) {
                is AccountAction.Revoke -> Revoke(action, error)
                is AccountAction.FollowAccount -> FollowAccount(action, error)
                is AccountAction.UnfollowAccount -> UnfollowAccount(action, error)
                is AccountAction.CancelFollowRequest -> TODO()
                is AccountAction.UnblockAccount -> TODO()
                is AccountAction.UnblockDomain -> TODO()
                is AccountAction.UnmuteAccount -> TODO()
            }
        }
    }

    val accept: (UiAction) -> Unit

    val uiResult: Flow<Result<UiSuccess, UiError>>

    val operationCount: Flow<Int>

    val collectionViewData: StateFlow<Result<Loadable<CollectionViewData>, PachliError>>

    data class UiOptions(
        val animateEmojis: Boolean = false,
        val animateAvatars: Boolean = false,
        val showBotOverlay: Boolean = false,
        val showPronouns: Boolean = false,
        val linksToUnderline: Set<LinksToUnderline> = emptySet(),
        val renderMarkdown: Boolean = false,
    ) {
        companion object {
            fun from(statusDisplayOptions: StatusDisplayOptions) = UiOptions(
                animateEmojis = statusDisplayOptions.animateEmojis,
                animateAvatars = statusDisplayOptions.animateAvatars,
                showBotOverlay = statusDisplayOptions.showBotOverlay,
                showPronouns = statusDisplayOptions.pronounDisplay == PronounDisplay.EVERYWHERE,
                linksToUnderline = statusDisplayOptions.linksToUnderline,
                renderMarkdown = statusDisplayOptions.renderMarkdown,
            )
        }
    }

    val uiOptions: Flow<UiOptions>
}
