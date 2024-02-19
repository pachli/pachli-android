package app.pachli.core.network.json

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Test

@OptIn(ExperimentalStdlibApi::class)
class DefaultIfNullTest {

    private val moshi = Moshi.Builder()
        .add(DefaultIfNull.Factory())
        .build()

    @JsonClass(generateAdapter = true)
    data class Wrapper(
        @DefaultIfNull
        val data: Data,
    )

    @JsonClass(generateAdapter = true)
    data class Data(
        val x: Int = 1,
        val y: Float = 2f,
        val z: String = "hello, world",
        val nullable: String? = null,
    )

    @Test
    fun `null x defaults to 1`() {
        val jsonInput = """
            {
              "data": {
                "x": null,
                "y": 3,
                "z": "foo",
                "nullable": "bar"
              }
            }
        """.trimIndent()
        assertThat(moshi.adapter<Wrapper>().fromJson(jsonInput)).isEqualTo(
            Wrapper(
                data = Data(
                    x = 1,
                    y = 3f,
                    z = "foo",
                    nullable = "bar",
                ),
            ),
        )
    }

    @Test
    fun `null y defaults to 2f`() {
        val jsonInput = """
            {
              "data": {
                "x": 1,
                "y": null,
                "z": "foo",
                "nullable": "bar"
              }
            }
        """.trimIndent()
        assertThat(moshi.adapter<Wrapper>().fromJson(jsonInput)).isEqualTo(
            Wrapper(
                data = Data(
                    x = 1,
                    y = 2f,
                    z = "foo",
                    nullable = "bar",
                ),
            ),
        )
    }

    @Test
    fun `null z defaults to "hello, world"`() {
        val jsonInput = """
            {
              "data": {
                "x": 1,
                "y": 2,
                "z": null,
                "nullable": "bar"
              }
            }
        """.trimIndent()
        assertThat(moshi.adapter<Wrapper>().fromJson(jsonInput)).isEqualTo(
            Wrapper(
                data = Data(
                    x = 1,
                    y = 2f,
                    z = "hello, world",
                    nullable = "bar",
                ),
            ),
        )
    }

    @Test
    fun `nullable remains null`() {
        val jsonInput = """
            {
              "data": {
                "x": 1,
                "y": 2,
                "z": "foo",
                "nullable": null
              }
            }
        """.trimIndent()
        assertThat(moshi.adapter<Wrapper>().fromJson(jsonInput)).isEqualTo(
            Wrapper(
                data = Data(
                    x = 1,
                    y = 2f,
                    z = "foo",
                    nullable = null,
                ),
            ),
        )
    }

    @Test
    fun `null everything returns default`() {
        val jsonInput = """
            {
              "data": {
                "x": null,
                "y": null,
                "z": null,
                "nullable": null
              }
            }
        """.trimIndent()
        assertThat(moshi.adapter<Wrapper>().fromJson(jsonInput)).isEqualTo(
            Wrapper(
                data = Data(),
            ),
        )
    }
}
