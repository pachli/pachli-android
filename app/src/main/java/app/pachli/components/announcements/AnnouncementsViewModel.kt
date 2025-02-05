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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.data.repository.InstanceInfoRepository
import app.pachli.core.network.model.Announcement
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Resource
import app.pachli.util.Success
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val instanceInfoRepo: InstanceInfoRepository,
    private val mastodonApi: MastodonApi,
) : ViewModel() {

    private val announcementsMutable = MutableLiveData<Resource<List<Announcement>>>()
    val announcements: LiveData<Resource<List<Announcement>>> = announcementsMutable

    private val emojisMutable = MutableLiveData<List<Emoji>>()
    val emojis: LiveData<List<Emoji>> = emojisMutable

    init {
        viewModelScope.launch {
            emojisMutable.postValue(instanceInfoRepo.emojis.value)
        }
    }

    fun load() {
        viewModelScope.launch {
            announcementsMutable.postValue(Loading())
            mastodonApi.listAnnouncements()
                .onSuccess {
                    announcementsMutable.postValue(Success(it.body))
                    it.body.filter { announcement -> !announcement.read }
                        .forEach { announcement ->
                            mastodonApi.dismissAnnouncement(announcement.id)
                                .onSuccess {
                                    accountManager.deleteAnnouncement(accountManager.activeAccount!!.id, announcement.id)
                                }
                                .onFailure { throwable ->
                                    Timber.d("Failed to mark announcement as read.", throwable)
                                }
                        }
                }
                .onFailure { announcementsMutable.postValue(Error(cause = it.throwable)) }
        }
    }

    fun addReaction(announcementId: String, name: String) {
        viewModelScope.launch {
            mastodonApi.addAnnouncementReaction(announcementId, name)
                .onSuccess {
                    announcementsMutable.postValue(
                        Success(
                            announcements.value?.data?.map { announcement ->
                                if (announcement.id == announcementId) {
                                    announcement.copy(
                                        reactions = if (announcement.reactions.find { reaction -> reaction.name == name } != null) {
                                            announcement.reactions.map { reaction ->
                                                if (reaction.name == name) {
                                                    reaction.copy(
                                                        count = reaction.count + 1,
                                                        me = true,
                                                    )
                                                } else {
                                                    reaction
                                                }
                                            }
                                        } else {
                                            listOf(
                                                *announcement.reactions.toTypedArray(),
                                                emojis.value!!.find { emoji -> emoji.shortcode == name }!!.run {
                                                    Announcement.Reaction(
                                                        name,
                                                        1,
                                                        true,
                                                        url,
                                                        staticUrl,
                                                    )
                                                },
                                            )
                                        },
                                    )
                                } else {
                                    announcement
                                }
                            },
                        ),
                    )
                }.onFailure {
                    Timber.w("Failed to add reaction to the announcement: %s", it)
                }
        }
    }

    fun removeReaction(announcementId: String, name: String) {
        viewModelScope.launch {
            mastodonApi.removeAnnouncementReaction(announcementId, name)
                .onSuccess {
                    announcementsMutable.postValue(
                        Success(
                            announcements.value!!.data!!.map { announcement ->
                                if (announcement.id == announcementId) {
                                    announcement.copy(
                                        reactions = announcement.reactions.mapNotNull { reaction ->
                                            if (reaction.name == name) {
                                                if (reaction.count > 1) {
                                                    reaction.copy(
                                                        count = reaction.count - 1,
                                                        me = false,
                                                    )
                                                } else {
                                                    null
                                                }
                                            } else {
                                                reaction
                                            }
                                        },
                                    )
                                } else {
                                    announcement
                                }
                            },
                        ),
                    )
                }.onFailure {
                    Timber.w("Failed to remove reaction from the announcement: %s", it)
                }
        }
    }
}
