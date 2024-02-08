package app.pachli.core.network.json

import app.pachli.core.network.json.GuardedAdapter.Companion.GuardedAdapterFactory
import app.pachli.core.network.model.Relationship
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test

@OptIn(ExperimentalStdlibApi::class)
class GuardedAdapterTest {

    private val moshi = Moshi.Builder()
        .add(GuardedAdapterFactory())
        .build()

    @Test
    fun `should deserialize Relationship when attribute 'subscribing' is a boolean`() {
        val jsonInput = """
            {
              "id": "1",
              "following": true,
              "showing_reblogs": true,
              "notifying": false,
              "followed_by": true,
              "blocking": false,
              "blocked_by": false,
              "muting": false,
              "muting_notifications": false,
              "requested": false,
              "domain_blocking": false,
              "endorsed": false,
              "note": "Hi",
              "subscribing": true
            }
        """.trimIndent()

        assertThat(moshi.adapter<Relationship>().fromJson(jsonInput)).isEqualTo(
            Relationship(
                id = "1",
                following = true,
                followedBy = true,
                blocking = false,
                muting = false,
                mutingNotifications = false,
                requested = false,
                showingReblogs = true,
                subscribing = true,
                blockingDomain = false,
                note = "Hi",
                notifying = false,
            ),
        )
    }

    @Test
    fun `should deserialize Relationship when attribute 'subscribing' is an object`() {
        val jsonInput = """
            {
              "id": "2",
              "following": true,
              "showing_reblogs": true,
              "notifying": false,
              "followed_by": true,
              "blocking": false,
              "blocked_by": false,
              "muting": false,
              "muting_notifications": false,
              "requested": false,
              "domain_blocking": false,
              "endorsed": false,
              "note": "Hi",
              "subscribing": { }
            }
        """.trimIndent()

        assertThat(moshi.adapter<Relationship>().fromJson(jsonInput)).isEqualTo(
            Relationship(
                id = "2",
                following = true,
                followedBy = true,
                blocking = false,
                muting = false,
                mutingNotifications = false,
                requested = false,
                showingReblogs = true,
                subscribing = null,
                blockingDomain = false,
                note = "Hi",
                notifying = false,
            ),
        )
    }

    @Test
    fun `should deserialize Relationship when attribute 'subscribing' does not exist`() {
        val jsonInput = """
            {
              "id": "3",
              "following": true,
              "showing_reblogs": true,
              "notifying": false,
              "followed_by": true,
              "blocking": false,
              "blocked_by": false,
              "muting": false,
              "muting_notifications": false,
              "requested": false,
              "domain_blocking": false,
              "endorsed": false,
              "note": "Hi"
            }
        """.trimIndent()

        assertThat(moshi.adapter<Relationship>().fromJson(jsonInput)).isEqualTo(
            Relationship(
                id = "3",
                following = true,
                followedBy = true,
                blocking = false,
                muting = false,
                mutingNotifications = false,
                requested = false,
                showingReblogs = true,
                subscribing = null,
                blockingDomain = false,
                note = "Hi",
                notifying = false,
            ),
        )
    }
}
