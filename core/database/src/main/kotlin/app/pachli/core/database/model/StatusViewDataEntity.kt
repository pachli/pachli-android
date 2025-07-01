/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.database.model

import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * The local view data for a status.
 *
 * There is *no* foreignkey relationship between this and [StatusEntity], as the view
 * data is kept even if the status is deleted from the local cache (e.g., during a refresh
 * operation).
 */
@androidx.room.Entity(
    primaryKeys = ["serverId", "pachliAccountId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("pachliAccountId"),
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["pachliAccountId"])],
)
data class StatusViewDataEntity(
    val pachliAccountId: Long,
    val serverId: String,
    /** Corresponds to [app.pachli.viewdata.IStatusViewData.isExpanded] */
    val expanded: Boolean?,
    /** Corresponds to [app.pachli.viewdata.IStatusViewData.isShowingContent] */
    val contentShowing: Boolean?,
    /** Corresponds to [app.pachli.viewdata.IStatusViewData.isCollapsed] */
    val contentCollapsed: Boolean?,
    /** Show the translated version of the status (if it exists) */
    @ColumnInfo(defaultValue = "SHOW_ORIGINAL")
    val translationState: TranslationState,
)

enum class TranslationState {
    /** Show the original, untranslated status */
    SHOW_ORIGINAL,

    /** Show the original, untranslated status, but translation is happening */
    TRANSLATING,

    /** Show the translated status */
    SHOW_TRANSLATION,
}

/**
 * Partial class for setting [StatusViewDataEntity.expanded] to [expanded].
 *
 * @see [app.pachli.core.database.dao.StatusDao.setExpanded]
 * @see [androidx.room.Upsert.entity]
 */
data class StatusViewDataExpanded(
    val pachliAccountId: Long,
    val serverId: String,
    val expanded: Boolean,
)

/**
 * Partial class for setting [StatusViewDataEntity.contentShowing] to [contentShowing].
 *
 * @see [app.pachli.core.database.dao.StatusDao.setContentShowing]
 * @see [androidx.room.Upsert.entity]
 */
data class StatusViewDataContentShowing(
    val pachliAccountId: Long,
    val serverId: String,
    val contentShowing: Boolean,
)

/**
 * Partial class for setting [StatusViewDataEntity.contentCollapsed] to [contentCollapsed].
 *
 * @see [app.pachli.core.database.dao.StatusDao.setContentCollapsed]
 * @see [androidx.room.Upsert.entity]
 */
data class StatusViewDataContentCollapsed(
    val pachliAccountId: Long,
    val serverId: String,
    val contentCollapsed: Boolean,
)

/**
 * Partial class for setting [StatusViewDataEntity.translationState] to [translationState].
 *
 * @see [app.pachli.core.database.dao.StatusDao.setTranslationState]
 * @see [androidx.room.Upsert.entity]
 */
data class StatusViewDataTranslationState(
    val pachliAccountId: Long,
    val serverId: String,
    val translationState: TranslationState,
)
