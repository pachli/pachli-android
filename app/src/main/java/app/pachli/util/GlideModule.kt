package app.pachli.util

import android.content.Context
import app.pachli.core.preferences.SharedPreferencesRepository
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.InputStream
import okhttp3.OkHttpClient
import timber.log.Timber

@GlideModule
@Excludes(OkHttpLibraryGlideModule::class)
class GlideModule : AppGlideModule() {
    // Replace the stock Glide OkHttpClient with the client configured in
    // NetworkModule. This shares the cache, and ensures any customisations
    // like proxy and SSL certificates are used.
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val entryPoint = EntryPoints.get(context, GlideEntryPoint::class.java)
        val httpClient = entryPoint.provideOkHttpClient()

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(httpClient),
        )
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val entryPoint = EntryPoints.get(context, GlideEntryPoint::class.java)
        val sharedPreferencesRepository = entryPoint.provideSharedPreferencesRepository()

        if (sharedPreferencesRepository.glideUseImageDecoder) {
            Timber.d("Glide: using ImageDecoder")

            // Enable ImageDecoder for Bitmaps (Android Q+)
            builder.setImageDecoderEnabledForBitmaps(true)

            // Enable ImageDecoder for Uris (Android Q+)
            builder.setUriImageDecoderEnabled(true)

            // Enable optimized buffer pooling for ImageDecoder
            builder.setUseArrayPoolForImageDecoderByteBufferAllocation(true)

            // Use heap buffers for ImageDecoder with InputStreams to avoid native memory overhead
            builder.setUseHeapBufferForImageDecoderWithInputStream(true)
        } else {
            Timber.d("Glide: not using ImageDecoder")
        }
    }

    // Hilt can't inject into GlideModule, provide an entry point to inject into.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface GlideEntryPoint {
        fun provideOkHttpClient(): OkHttpClient
        fun provideSharedPreferencesRepository(): SharedPreferencesRepository
    }
}
