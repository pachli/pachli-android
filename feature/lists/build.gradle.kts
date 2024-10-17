plugins {
    alias(libs.plugins.pachli.android.library)
    alias(libs.plugins.pachli.android.hilt)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "app.pachli.feature.lists"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.core.activity)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
        ?.because("AccountsInListFragment uses network.model.TimelineAccount and ApiError")

    implementation(projects.core.ui)

    // TODO: These dependencies are required by BottomSheetActivity,
    // make this part of the projects.core.activity API?
    implementation(projects.core.preferences)
    implementation(libs.bundles.androidx)

    implementation(libs.material.typeface)
    implementation(libs.material.iconics)
}
