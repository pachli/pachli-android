package app.pachli.core.network.json

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Json
import org.junit.Test

class EnumConstantConverterFactoryTest {
    enum class Enum {
        @Json(name = "one")
        ONE,

        TWO,
    }

    private val converter = EnumConstantConverterFactory.EnumConstantConverter

    @Test
    fun `Annotated enum constant uses annotation`() {
        assertThat(converter.convert(Enum.ONE)).isEqualTo("one")
    }

    @Test
    fun `Unannotated enum constant uses constant name`() {
        assertThat(converter.convert(Enum.TWO)).isEqualTo("TWO")
    }
}
