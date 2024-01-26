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

package app.pachli.mvstring

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.DelegatingKLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.createTempFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.useLines

private val log = KotlinLogging.logger {}

/**
 * Moves a string resource from one module to another.
 *
 * Run with `gradlew :tools:mvstring:run` or `runtools mvstring`.
 */
class App : CliktCommand(help = """Move string resources between modules""") {
    private val args by argument().multiple()

    private val verbose by option("-n", "--verbose", help = "show additional information").flag()

    /**
     * Returns the full path to a module's `.../src/main/res` directory, starting from the
     * given [start] directory, walking up the tree if it can't be found there.
     *
     * @return the path, or null if it's not a subtree of [start] or any of its parents.
     */
    private fun findResourcePath(start: Path, variant: String = "main"): Path? {
        val suffix = Path("src/$variant/res")

        var prefix = start
        var resourcePath: Path
        do {
            resourcePath = prefix / suffix
            if (resourcePath.exists()) return resourcePath
            prefix = prefix.parent
        } while (prefix != prefix.root)

        return null
    }

    override fun run() {
        System.setProperty("file.encoding", "UTF8")
        ((log as? DelegatingKLogger<*>)?.underlyingLogger as Logger).level = if (verbose) Level.INFO else Level.WARN

        if (args.size != 2 && args.size != 3) throw UsageError("incorrect arguments")

        val (src: String, dst: String, id: String) = if (args.size == 3) {
            Triple(args[0], args[1], args[2])
        } else {
            Triple("app", args[0], args[1])
        }

        val cwd = Paths.get("").toAbsolutePath()
        log.info { "working directory: $cwd" }

        val srcRes = findResourcePath(Path(src)) ?: throw UsageError("no resources in $src")
        val dstRes = findResourcePath(Path(dst)) ?: throw UsageError("no resources in $dst")

        // Enumerate all the values-* directories that contain a strings.xml file
        val resourceDirs =
            (
                srcRes.listDirectoryEntries("values") +
                    srcRes.listDirectoryEntries("values-*")
                )
                .filter { entry -> entry.isDirectory() }
                .filter { dir -> (dir / "strings.xml").isRegularFile() }

        if (resourceDirs.isEmpty()) throw UsageError("no strings.xml files found in $srcRes/values-*")

        resourceDirs.forEach { srcPath ->
            val dstPath = dstRes / srcPath.name // Construct the final values path
            moveResource(srcPath, dstPath, id)
        }
    }

    enum class State {
        Searching,
        Moving,
        Moved,
    }

    private fun moveResource(src: Path, dst: Path, id: String) {
        val tmpSrcFile = createTempFile().toFile()
        val tmpSrc = tmpSrcFile.printWriter()
        val tmpDstFile = createTempFile().toFile()
        val tmpDst = tmpDstFile.printWriter()

        dst.toFile().mkdirs()

        val srcFile = src / "strings.xml"
        val dstFile = dst / "strings.xml"

        val toCopy = mutableListOf<String>()
        var state = State.Searching

        srcFile.useLines { srcLines ->
            for (srcLine in srcLines) {
                // Moved the resource, copy the remaining lines
                if (state == State.Moved) {
                    tmpSrc.println(srcLine)
                    continue
                }

                // Not the resource we're looking for
                if (state == State.Searching && !srcLine.contains("""<string name="$id""")) {
                    tmpSrc.println(srcLine)
                    continue
                }

                // Matching resource. Append to dstFile
                state = State.Moving
                toCopy.add(srcLine)

                if (srcLine.contains("</string>")) {
                    if (!dstFile.exists()) createResourceFile(dstFile)
                    dstFile.useLines { dstLines ->
                        for (dstLine in dstLines) {
                            if (!dstLine.contains("</resources>")) {
                                tmpDst.println(dstLine)
                                continue
                            }

                            tmpDst.println(toCopy.joinToString("\n"))
                            toCopy.clear()
                            state = State.Moved
                            tmpDst.println(dstLine)
                        }
                    }
                }
            }
        }

        tmpSrc.close()
        tmpDst.close()

        if (state == State.Moved) {
            Files.copy(tmpSrcFile.toPath(), srcFile, StandardCopyOption.REPLACE_EXISTING)
            Files.copy(tmpDstFile.toPath(), dstFile, StandardCopyOption.REPLACE_EXISTING)
        }

        Files.delete(tmpSrcFile.toPath())
        Files.delete(tmpDstFile.toPath())
    }

    private fun createResourceFile(path: Path) {
        val w = path.createFile().toFile().printWriter()
        w.println(
            """
<?xml version="1.0" encoding="UTF-8"?>
<resources>
</resources>
            """.trimIndent(),
        )
        w.close()
    }
}

fun main(args: Array<String>) = App().main(args)
