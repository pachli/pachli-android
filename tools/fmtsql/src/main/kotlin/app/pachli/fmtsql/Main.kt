/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.fmtsql

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Lint result from sqlfluff for a single file.
 *
 * @param violations Individual lint violations.
 */
@JsonClass(generateAdapter = true)
data class SqlFluffLintResult(
    val violations: List<SqlFluffViolation>,
)

/**
 * Individual sqlfluff lint violation.
 *
 * @param code The violated rule.
 * @param description End-user description of the rule.
 * @param name Internal name of the rule.
 * @param fixes List of fixes that can be applied. Empty if no fixes are possible.
 */
@JsonClass(generateAdapter = true)
data class SqlFluffViolation(
    val code: String,
    val description: String,
    val name: String,
    val fixes: List<Any>?,
)

/** Formats SQL queries in `@Query(...)` annotations using `sqlfluff` */
class App : SuspendingCliktCommand() {
    override fun help(context: Context) = "Run sqlfluff on DAO SQL"

    /**
     * Regex to match SQL in `@Query` annotations.
     *
     * Matches `@Query("...")` and `@Query("""...""")` where the content inside
     * may span multiple lines.
     */
    private val rxQuery = """@Query\(\s*"{1,3}(.*?)"{1,3},?\s*\)""".toRegex(
        setOf(
            RegexOption.DOT_MATCHES_ALL,
            RegexOption.MULTILINE,
        ),
    )
    private val moshi: Moshi = Moshi.Builder().build()

    @OptIn(ExperimentalStdlibApi::class)
    val lintResultAdapter = moshi.adapter<Array<SqlFluffLintResult>>()

    private val daoDir by option("--dir", help = "Path to directory containing DAO files")
        .path(mustExist = true)
        .default(Path("core/database/src/main/kotlin/app/pachli/core/database/dao"))
    private val sqlFluff by option("--sqlfluff", help = "Path to sqlfluff executable")
        .file(mustExist = true)
        .default(File("""C:\Users\Nik\AppData\Local\Programs\Python\Python313\Scripts\sqlfluff.exe"""))

    override suspend fun run() = coroutineScope {
        System.setProperty("file.encoding", "UTF8")

        val jobs = daoDir.listDirectoryEntries("*.kt")
            .filter { it.isRegularFile() }
            .map { async { formatSql(it.toFile()) } }

        jobs.awaitAll()
        return@coroutineScope
    }

    /** Format `@Query` annotations in [file]. */
    private suspend fun formatSql(file: File) = withContext(Dispatchers.IO) {
        println(file.path)

        val content = file.readText()

        val newContent = rxQuery.replace(content) { match ->
            val unformattedSql = match.groupValues[1].trim()
            val lintResults = sqlfluffLint(unformattedSql)

            if (lintResults == null) {
                println("error: could not parse lint results for query")
                println("  sql: $unformattedSql")
                return@replace match.value
            }

            val violations = lintResults.first().violations

            // No violations? Nothing to format.
            if (violations.isEmpty()) return@replace match.value

            // If any violations have no fixes then treat as unfixable.
            violations.filter { it.fixes.isNullOrEmpty() }.takeIf { it.isNotEmpty() }?.let { unfixable ->
                println("error: $file: can't fix query, it has unfixable lint violations")
                println("  sql: $unformattedSql")
                unfixable.forEach { println("  $it") }
                return@replace match.value
            }

            // Format, and either return the formatted value (if OK) or the
            // original value (if an error occurred).
            return@replace when (val result = sqlfluffFix(unformattedSql)) {
                is Err -> {
                    println("error: $file: ${result.error}")
                    println("  sql: $unformattedSql")
                    match.value
                }
                is Ok -> result.value
            }
        }

        // No changes?
        if (newContent == content) return@withContext

        val tmpFile = createTempFile().toFile()
        val tmpW = tmpFile.printWriter()

        tmpW.print(newContent)
        tmpW.close()
        Files.copy(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Lint's [sql] with sqlfluff.
     *
     * @return Array of [SqlFluffLintResult], null if the SQL could not be
     * linted.
     */
    private fun sqlfluffLint(sql: String): Array<SqlFluffLintResult>? {
        val cmd = arrayOf(sqlFluff.path, "lint", "-f", "json", "-")

        val lint = ProcessBuilder(*cmd)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        lint.outputWriter().use { w ->
            w.append(sql)
            w.newLine()
            w.flush()
        }
        lint.waitFor(10, TimeUnit.SECONDS)
        return lintResultAdapter.fromJson(lint.inputStream.bufferedReader().readText())
    }

    /**
     * Formats [sql] with `sqlfluff fix`.
     *
     * @return The formatted string.
     */
    private fun sqlfluffFix(sql: String): Result<String, String> {
        val cmdFix = arrayOf(sqlFluff.path, "fix", "-")
        val proc = ProcessBuilder(*cmdFix)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        val w = proc.outputWriter()
        w.append(sql)
        w.newLine()
        w.flush()
        w.close()

        proc.waitFor(10, TimeUnit.SECONDS)
        val errors = proc.errorStream.bufferedReader().readText()
        if (errors.isNotEmpty()) {
            return Err(errors)
        }

        val formattedSql = proc.inputStream.bufferedReader().readText().trim()
        val tq = "\"\"\""
        return Ok(
            """@Query(
        $tq
$formattedSql
$tq,
    )""",
        )
    }
}

suspend fun main(args: Array<String>) = App().main(args)
