plugins {
    alias(libs.plugins.pachli.android.library)
    alias(libs.plugins.pachli.android.hilt)
    alias(libs.plugins.pachli.android.room)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "app.pachli.core.database"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.preferences)

    // Because of the use of @SerializedName in DraftEntity
    implementation(libs.gson)
}
