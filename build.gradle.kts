plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.quadrant) apply false
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
// limited to the "orangeGoogleDebug" flavour/variant. While problems
// might affect other combinations (and CI will check all of them), this
// combination passing gives high confidence for the amount of time it
// takes to run.
//
// - :app:lintOrangeGoogleDebug
// - *:testOrangeGoogleDebugUnitTest
// - :app:assembleOrangeDebug
// - *:pixel9api31orangegoogledebugAndroidTest
tasks.register("precommit") {
    group = "Verification"
    description = "Runs the precommit tests."
    dependsOn(":app:lintOrangeGoogleDebug")
    allprojects
        .flatMap { it.tasks }
        .filter { it.name.equals("testOrangeGoogleDebugUnitTest", ignoreCase = true) }
        .forEach {
            dependsOn(it.path)
        }
    dependsOn(":app:assembleOrangeGoogleDebug")
    allprojects
        .flatMap { it.tasks }
        .filter { it.name.equals("pixel9api31OrangeGoogleDebugAndroidTest", ignoreCase = true) }
        .forEach {
            println("dep: $it.name")
            dependsOn(it.path)
        }
}
