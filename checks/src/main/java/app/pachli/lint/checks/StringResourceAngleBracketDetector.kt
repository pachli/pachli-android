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

package app.pachli.lint.checks

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.OtherFileScanner
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.uast.UCallExpression

/**
 * Looks for the use of angle brackets in string resources, e.g.,
 *
 * ```xml
 * <string name="foo">Here is some <b>bold</b> text</string>
 * ```
 *
 * If that string resource is being fetched using `getString()` and then sent through
 * HtmlCompat.fromHtml() then the `<b>...</b>` is wrong, and the angle brackets
 * should use entities.
 *
 * However, if it's being sent through `getText()` it's OK.
 *
 * So this detector looks for two things:
 *
 * 1. All string resources across all translations that contain angle brackets
 * somewhere in the text.
 * 2. All calls to `getString()`.
 *
 * These are then filtered to produce a list of string resources with angle brackets
 * that are referenced by `getString()`. These are then warned about.
 *
 * See [StringResourceEntityDetector] for the detector that does the opposite of this
 * one when resources are sent to `getText`.
 */
// This can't process the XML files using the XML parser because, when parsing XML, any
// any `&gt;` or `&lt;` entities in the source XML file are converted to `<` and `>` by
// the parser **before** they are passed to the detector, so it's impossible to tell
// how the text was represented in the source element.
//
// So the resource files are processed as text, with regexes to pull out the different
// resources.
class StringResourceAngleBracketDetector : Detector(), SourceCodeScanner, OtherFileScanner {
    @Serializable
    data class Message(
        val message: String,
        val fix: Fix,
    )

    @Serializable
    data class Fix(
        val oldText: String,
        val newText: String,
    )

    /**
     * All string resource names referenced by calls to `getString`. This is the name
     * of the resource as it appears in the XML file, so a call like
     *
     * ```
     * getString(R.string.post_edited, ...)
     * ```
     *
     * will record the resource name `post_edited`.
     */
    private val referencedStringResources = mutableSetOf<String>()

    /** Records each [Location] where a potential incident occurs. */
    private val potentialLocations = LintMap()

    /** Records each [Message] where a potential incident occurs. */
    private val potentialMessages = LintMap()

    /**
     * Regex to match `<string name="{resourceName}">{innerText}</string>` when
     * processing the XML files.
     */
    private val rxStringResource = Regex(
        """<string\s+name="(?<resourceName>[^"]+?)"[^>]*?>(?<innerText>.*?)</string>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * Regex to match any unexpected `<...>` in the text (or optional `</...>`).
     */
    private val rxUnexpectedTag = Regex(
        """(?<open><)(?<slash>/)?(?<tagName>[a-z]+?)(?<close>>)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
    )

    // Call `visitMethodCall` on calls to `getString`.
    override fun getApplicableMethodNames(): List<String> = listOf(SdkConstants.GET_STRING_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        // Look up any of these string formatting methods:
        // android.content.res.Resources#getString(@StringRes int resId, Object... formatArgs)
        // android.content.Context#getString(@StringRes int resId, Object... formatArgs)
        // android.app.Fragment#getString(@StringRes int resId, Object... formatArgs)
        // android.support.v4.app.Fragment#getString(@StringRes int resId, Object... formatArgs)

        // Many of these also define a plain getString method:
        // android.content.res.Resources#getString(@StringRes int resId)
        // However, while it's possible that these contain formatting strings, it's
        // also possible that they're looking up strings that are not intended to be used
        // for formatting so while we may want to warn about this it's not necessarily
        // an error.
        if (
            evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_RESOURCES, false) ||
            evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_CONTEXT, false) ||
            evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_FRAGMENT, false) ||
            evaluator.isMemberInSubClassOf(
                method,
                AndroidXConstants.CLASS_V4_FRAGMENT.oldName(),
                false,
            ) ||
            evaluator.isMemberInSubClassOf(
                method,
                AndroidXConstants.CLASS_V4_FRAGMENT.newName(),
                false,
            )
        ) {
            checkGetStringCall(context, node)
        }
    }

    /**
     * Check the given `getString` call, record the referenced string resource.
     *
     * @param context the context to report errors to.
     * @param call the AST node for the `getString` call.
     */
    private fun checkGetStringCall(context: JavaContext, call: UCallExpression) {
        val args = call.valueArguments
        val argument = args.first()
        val resource = ResourceEvaluator.getResource(context.evaluator, argument) ?: return

        if (resource.isFramework) return
        if (resource.type != ResourceType.STRING) return

        // Record the resource name being used. In `getString(R.string.post_edited, ...)`
        // the resource name is `post_edited`.
        referencedStringResources.add(resource.name)
    }

    override fun run(context: Context) {
        if (context.file.name != "strings.xml") return
        val text = context.getContents() ?: return

        /**
         * All the [MatchResult] for [rxStringResource]. Each result has a
         * `resourceName` and `innerText` group.
         */
        val stringResources = rxStringResource.findAll(text).toList()
        if (stringResources.isEmpty()) return

        stringResources.forEach { stringResource ->
            val resourceName = stringResource.groups["resourceName"]?.value ?: return
            val innerTextGroup = stringResource.groups["innerText"] ?: return
            val innerText = innerTextGroup.value

            // Find any unexpected tags with angle brackets in `innerText`.
            val unexpectedTags = rxUnexpectedTag.findAll(innerText)

            unexpectedTags.forEachIndexed { index, result ->
                val open = result.groups["open"]!!
                val slash = result.groups["slash"]?.value ?: ""
                val tagName = result.groups["tagName"]!!.value
                val close = result.groups["close"]!!

                val location = Location.create(
                    context.file,
                    text,
                    innerTextGroup.range.first + open.range.first,
                    innerTextGroup.range.first + close.range.last + 1,
                )

                // The incident has to be passed to `checkPartialResults`. You can't
                // pass an `Incident` in a `LintMap` (https://issuetracker.google.com/issues/456225718)
                // so the data necessary to construct the final `Incident` is
                // passed in two maps with identical keys.
                //
                // `potentialLocations` records the `Location` of this incident.
                // `potentialMessages` records the message and fix of this
                // incident.
                //
                // Build the values and populate those maps.
                //
                // $index is in the key so that multiple issues in the same string
                // resource have a unique key.
                val key = "$resourceName:${context.file.absoluteFile}:$index"
                val message = Message(
                    message = "Replace with &lt;$slash$tagName&gt;",
                    fix = Fix(
                        oldText = result.groups.first()!!.value,
                        newText = "&lt;$slash$tagName&gt;",
                    ),
                )

                // Locations can be stored directly in a LintMap.
                potentialLocations.put(key, location)

                // Messages can't be stored directly in a LintMap, and LintMap
                // doesn't allow you to store anything marked @Serializable,
                // so serialize to a JSON string.
                potentialMessages.put(key, Json.encodeToString(message))
            }
        }
    }

    /**
     * Save the values of [potentialLocations], [potentialMessages], and
     * [referencedStringResources] so they can be merged together in
     * [checkPartialResults].
     */
    override fun afterCheckEachProject(context: Context) {
        super.afterCheckEachProject(context)

        context.getPartialResults(ISSUE).map().apply {
            put("POTENTIAL_LOCATIONS", potentialLocations)
            put("POTENTIAL_MESSAGES", potentialMessages)
            put("REFERENCED_STRING_RESOURCES", referencedStringResources.joinToString(","))
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkPartialResults(context, context.getPartialResults(ISSUE))
        }
    }

    /**
     * Merge the results from all modules and generate the actual incidents.
     */
    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val locationsWithAngleBrackets = LintMap()
        val messagesWithAngleBrackets = LintMap()
        val referencedStringResources = mutableSetOf<String>()

        partialResults.forEach { partialResult ->
            val map = partialResult.value

            map.getMap("POTENTIAL_LOCATIONS")?.let { locationsWithAngleBrackets.putAll(it) }
            map.getMap("POTENTIAL_MESSAGES")?.let { messagesWithAngleBrackets.putAll(it) }
            map.getString("REFERENCED_STRING_RESOURCES")?.let {
                referencedStringResources.addAll(it.split(","))
            }
        }

        // Report all the appropriate incidents.
        locationsWithAngleBrackets.forEach { key ->
            val resourceName = key.split(":").first()

            // If this resource is referenced from a getString() call and the location
            // is somewhere angle brackets have been used then this is an incident.
            //
            // Extract the location and message for the incident, build it, and report
            // it.
            if (referencedStringResources.contains(resourceName)) {
                val location = locationsWithAngleBrackets.getLocation(key)!!
                val message = Json.decodeFromString<Message>(messagesWithAngleBrackets.getString(key)!!)

                val incident = Incident(
                    ISSUE,
                    location,
                    message = message.message,
                    fix = fix().replace().text(message.fix.oldText)
                        .with(message.fix.newText).build(),
                )

                context.report(incident)
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "StringResourceAngleBracketDetector",
            briefDescription = "`<` and `>` in string resources should use HTML entities",
            explanation = """
                String resources that contain HTML strings should use HTML entities
                (`&lt;`, `&gt;`) instead of angle brackets (`<` and `>`).

                **Unless** the string is being passed to `getText()`. Then using the
                angle brackets is correct.
            """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                StringResourceAngleBracketDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES, Scope.OTHER),
            ),
        )
    }
}
