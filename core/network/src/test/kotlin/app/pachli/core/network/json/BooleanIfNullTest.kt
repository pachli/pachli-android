package app.pachli.core.network.json

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test

@OptIn(ExperimentalStdlibApi::class)
class BooleanIfNullTest {
    private val moshi = Moshi.Builder()
        .add(BooleanIfNull.Factory())
        .build()

    @JsonClass(generateAdapter = true)
    data class Data(@BooleanIfNull(false) val x: Boolean)

    @JsonClass(generateAdapter = true)
    data class Data2(@BooleanIfNull(true) val x: Boolean)

    @Test
    fun `true x is true`() {
        val jsonInput = """
            {
              "x": true
            }
        """.trimIndent()
        assertThat(moshi.adapter<Data>().fromJson(jsonInput)).isEqualTo(Data(x = true))
    }

    @Test
    fun `false x is false`() {
        val jsonInput = """
            {
              "x": false
            }
        """.trimIndent()
        assertThat(moshi.adapter<Data>().fromJson(jsonInput)).isEqualTo(Data(x = false))
    }

    @Test
    fun `null x is false`() {
        val jsonInput = """
            {
              "x": null
            }
        """.trimIndent()
        assertThat(moshi.adapter<Data>().fromJson(jsonInput)).isEqualTo(Data(x = false))
    }

    @Test
    fun `null x is true`() {
        val jsonInput = """
            {
              "x": null
            }
        """.trimIndent()
        assertThat(moshi.adapter<Data2>().fromJson(jsonInput)).isEqualTo(Data2(x = true))
    }
}
