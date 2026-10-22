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
tasks.register("precommit") {
    group = "Verification"
    description = "Runs the precommit tests."
    dependsOn("clean")
    dependsOn(":app:assembleOrangeGoogleDebug")
    dependsOn(":app:updateLintBaselineBlueFdroidDebug")
    val perModuleDeps =
        listOf(
            "testOrangeGoogleDebugUnitTest",
            "pixel9api31OrangeGoogleDebugAndroidTest",
            "validateOrangeGoogleDebugScreenshotTest",
        )
    allprojects
        .flatMap { it.tasks }
        .filter { task -> perModuleDeps.any { task.name.equals(it, ignoreCase = true) } }
        .forEach {
            dependsOn(it.path)
        }
}
