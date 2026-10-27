plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.screenshot) apply false
}

allprojects {
    apply(
        plugin =
            rootProject.libs.plugins.ktlint
                .get()
                .pluginId,
    )
}

// GitHub action runners can timeout resulting in occasional test flakiness.
// Re-run tests on CI to work around this, while disallowing failing tests
// when developing locally.
subprojects {
    val isCiBuild = providers.environmentVariable("CI").isPresent

    if (isCiBuild) {
        tasks.withType<Test>().configureEach {
            develocity.testRetry {
                maxRetries = 4
                maxFailures = 5
                failOnPassedAfterRetry = false
            }
        }
    }

    // AGP 9 defaults to failing if a `src/test` directory exists with no tests.
    // Disable this. For details see:
    // - https://github.com/gradle/gradle/issues/33619
    // - https://github.com/react-native-device-info/react-native-device-info/issues/1743
    // - https://issuetracker.google.com/issues/411739086
    tasks.withType<Test> {
        failOnNoDiscoveredTests = false
    }

    // Work around compiler error:
    // AbstractMethodError: Receiver class FieldBundle$$serializer does not define or inherit an implementation of the resolved method KSerializer[]
    // See https://issuetracker.google.com/issues/447154195
    //
    // Triggered by update to androidx.room 2.8.x.
    configurations.all {
        resolutionStrategy {
            eachDependency {
                if (requested.group == "org.jetbrains.kotlinx" &&
                    requested.name.startsWith("kotlinx-serialization-")
                ) {
                    useVersion(
                        libs.versions.kotlin.serialization
                            .get(),
                    )
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

// Create a "precommit" lifecycle task that depends on other tasks that
// give reasonable confidence the change will pass CI. The tasks are
// (mostly) limited to the "orangeGoogleDebug" flavour/variant. While problems
// might affect other combinations (and CI will check all of them), this
// combination passing gives high confidence for the amount of time it
// takes to run.
//
// - clean
// - :app:lintOrangeGoogleDebug
// - :app:assembleOrangeDebug
// - *:pixel9api31orangegoogledebugAndroidTest
// - *:testOrangeGoogleDebugUnitTest
// - *:validateOrangeGoogleDebugScreenshotTest
// - app:updateLintBaselineBlueFdroidDebug
// tasks.register("precommit") {
//    group = "Verification"
//    description = "Runs the precommit tests."
//    dependsOn("clean")
//    dependsOn(":app:assembleOrangeGoogleDebug")
//    dependsOn(":app:updateLintBaselineBlueFdroidDebug")
//    val perModuleDeps =
//        listOf(
//            "testOrangeGoogleDebugUnitTest",
//            "pixel9api31OrangeGoogleDebugAndroidTest",
//            "validateOrangeGoogleDebugScreenshotTest",
//        )
//    allprojects
//        .flatMap { it.tasks }
//        .filter { task -> perModuleDeps.any { task.name.equals(it, ignoreCase = true) } }
//        .forEach {
//            dependsOn(it.path)
//        }
// }
