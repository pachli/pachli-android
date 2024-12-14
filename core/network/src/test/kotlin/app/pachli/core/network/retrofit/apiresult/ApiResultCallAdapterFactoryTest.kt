package app.pachli.core.network.retrofit.apiresult

import com.github.michaelbull.result.Result
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit

class ApiResultCallAdapterFactoryTest {
    private val retrofit = Retrofit.Builder().baseUrl("https://example.com").build()

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `should return an ApiResultCallAdapter`() {
        val callType = typeOf<Call<ApiResult<Site>>>()
        val adapter = ApiResultCallAdapterFactory().get(callType.javaType, arrayOf(), retrofit)
        assertThat(adapter).isInstanceOf(ApiResultCallAdapter::class.java)
        assertThat(adapter?.responseType()).isEqualTo(Site::class.java)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `should return a SyncApiResultCallAdapter`() {
        val responseType = typeOf<ApiResult<Site>>()
        val adapter = ApiResultCallAdapterFactory().get(responseType.javaType, arrayOf(), retrofit)

        assertThat(adapter).isInstanceOf(SyncApiResultCallAdapter::class.java)
        assertThat(adapter?.responseType()).isEqualTo(Site::class.java)
    }

    @Test
    fun `should throw error if the type is not parameterized`() {
        assertThrows(IllegalStateException::class.java) {
            ApiResultCallAdapterFactory().get(Result::class.java, arrayOf(), retrofit)
        }
    }

    @Test
    fun `should return null if the type is not supported`() {
        val adapter = ApiResultCallAdapterFactory().get(Site::class.java, arrayOf(), retrofit)

        assertThat(adapter).isNull()
    }
}
