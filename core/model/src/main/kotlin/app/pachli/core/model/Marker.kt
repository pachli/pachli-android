package app.pachli.core.model

import java.util.Date

/**
 * API type for saving the scroll position of a timeline.
 */
data class Marker(
    val lastReadId: String,
    val version: Int,
    val updatedAt: Date,
)
