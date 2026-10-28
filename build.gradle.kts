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
tasks.register("precommit") {
    group = "Verification"
    description = "Runs the precommit tests."
    dependsOn("clean")
    dependsOn(":app:assembleOrangeGoogleDebug")
    dependsOn(":app:updateLintBaselineBlueFdroidDebug")

    // Depend on all the "precommit" tasks in each project.
    subprojects.forEach { project ->
        // Gradle creates intermediate projects for directories that
        // exist for organisation only (e.g., "core" and "feature").
        // These projects don't have a build file, so don't have a
        // "precommit" task. Skip them.
        val projectDir = project.isolated.projectDirectory.asFile
        val hasBuildScript =
            projectDir.resolve("build.gradle.kts").exists() ||
                projectDir.resolve("build.gradle").exists()

        if (!hasBuildScript) {
            return@forEach
        }

        dependsOn("${project.isolated.path}:precommit")
    }
}
