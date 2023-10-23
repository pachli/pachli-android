plugins {
    alias(libs.plugins.pachli.android.library)
    alias(libs.plugins.pachli.android.hilt)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "app.pachli.core.accounts"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    // Depends on the okhttp3.Interceptor type
    // class app.pachli.core.network.retrofit.InstanceSwitchAuthInterceptor, unresolved supertypes: okhttp3.Interceptor
    compileOnly(libs.bundles.okhttp)

    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.preferences)
}
