/*
 * Copyright 2023 Pachli Association
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.plugins.markdown2resource

import com.android.build.gradle.LibraryExtension
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import java.io.IOException
import javax.lang.model.element.Modifier
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.configurationcache.extensions.capitalized
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

interface Markdown2ResourcePluginExtension {
    /** List of files */
    val files: ListProperty<RegularFile>

    /** Class name for the generated resources. Default is "markdownR" */
    val resourceClassName: Property<String>

    /** Class name for the generated strings. Default is "html". */
    val stringClassName: Property<String>

    /**
     * Package name for the generated class. Default is the value of the android.namespace
     * property.
     */
    val packageName: Property<String>
}

abstract class Markdown2ResourceTask : DefaultTask() {
    @get:InputFiles
    abstract val files: ListProperty<RegularFile>

    @get:Input
    abstract val resourceClassName: Property<String>

    @get:Input
    abstract val stringClassName: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute() {
        logger.info("outputDir: ${outputDir.get()}")

        val resourceClass = createResourceClass(resourceClassName.get())!!
        val stringClass = createStringClass(stringClassName.get())!!
        val flavour = GFMFlavourDescriptor()

        files.get().forEach { markdownFile ->
            logger.info("Processing ${markdownFile.asFile.absoluteFile.name}")

            val f = markdownFile.asFile.readText()
            val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(f)
            val html = HtmlGenerator(f, parsedTree, flavour).generateHtml()

            val resourceName = markdownFile.asFile.absoluteFile.name.replace("""[./\\]""".toRegex(), "_")
            logger.info("  Resource name: ${resourceClassName.get()}.${stringClassName.get()}.$resourceName")

            stringClass.addField(
                FieldSpec.builder(String::class.java, resourceName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("\$S", html)
                    .build(),
            )

            resourceClass.addType(stringClass.build())
        }

        generateStringResourceFile(packageName.get(), resourceClass)
    }

    private fun createResourceClass(name: String): TypeSpec.Builder? {
        return TypeSpec.classBuilder(name).addModifiers(Modifier.PUBLIC, Modifier.FINAL)
    }

    private fun createStringClass(name: String): TypeSpec.Builder? {
        return TypeSpec.classBuilder(name).addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
    }

    private fun generateStringResourceFile(packageName: String, classBuilder: TypeSpec.Builder) {
        val javaFile = JavaFile.builder(packageName, classBuilder.build()).build()
        try {
            javaFile.writeTo(outputDir.get().asFile)
            logger.info(javaFile.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

class Markdown2ResourcePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create(
            "markdown2resource",
            Markdown2ResourcePluginExtension::class.java,
        )
        extension.resourceClassName.convention("markdownR")
        extension.stringClassName.convention("html")

        target.extensions.findByType(LibraryExtension::class.java)?.let { appExtension ->
            appExtension.libraryVariants.all { variant ->
                val outputDir =
                    target.layout.buildDirectory.dir("generated/source/${variant.name}")
                val taskName = "markdown2resource${variant.name.capitalized()}"

                extension.packageName.convention(variant.mergeResourcesProvider.get().namespace)

                val task =
                    target.tasks.register(taskName, Markdown2ResourceTask::class.java) { task ->
                        task.files.set(extension.files)
                        task.resourceClassName.set(extension.resourceClassName)
                        task.stringClassName.set(extension.stringClassName)
                        task.packageName.set(extension.packageName)
                        task.outputDir.set(outputDir)
                    }

                variant.registerJavaGeneratingTask(task, outputDir.get().asFile)
            }
        } ?: throw GradleException("'android' configuration block not found")
    }
}
