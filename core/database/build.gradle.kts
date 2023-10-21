plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.hilt)
}

android {
    buildFeatures {
        buildConfig = true
    }

    namespace = "app.pachli.core.database"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//        consumerProguardFiles "consumer-rules.pro"
    }

//    buildTypes {
//        release {
//            minifyEnabled false
//            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
//        }
//    }
//    compileOptions {
//        sourceCompatibility JavaVersion.VERSION_17
//        targetCompatibility JavaVersion.VERSION_17
//    }
//    kotlinOptions {
//        jvmTarget = '17'
//    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.preferences)

    implementation(libs.bundles.androidx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Because of the use of @SerializedName in DraftEntity
    implementation(libs.gson)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.androidx.test.junit)

    androidTestImplementation(libs.androidx.test.junit)
}
