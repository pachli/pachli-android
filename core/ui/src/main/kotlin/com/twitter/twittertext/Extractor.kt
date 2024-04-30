// Copyright 2018 Twitter, Inc.
// Licensed under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.twitter.twittertext

import java.net.IDN
import java.util.regex.Matcher

/**
 * A class to extract usernames, hashtags and URLs from Mastodon text.
 */
open class Extractor {
    data class Entity(
        var start: Int,
        var end: Int,
        val value: String,
        val type: Type,
    ) {
        enum class Type {
            URL,
            HASHTAG,
            MENTION,
        }

        @JvmOverloads
        constructor(matcher: Matcher, type: Type, groupNumber: Int, startOffset: Int = -1) : this(
            matcher.start(groupNumber) + startOffset,
            matcher.end(groupNumber),
            matcher.group(groupNumber)!!,
            type,
        )
    }

    var isExtractURLWithoutProtocol = true

    private fun removeOverlappingEntities(entities: MutableList<Entity>) {
        // sort by index
        entities.sortWith { e1, e2 -> e1.start - e2.start }

        // Remove overlapping entities.
        // Two entities overlap only when one is URL and the other is hashtag/mention
        // which is a part of the URL. When it happens, we choose URL over hashtag/mention
        // by selecting the one with smaller start index.
        if (entities.isNotEmpty()) {
            val it = entities.iterator()
            var prev = it.next()
            while (it.hasNext()) {
                val cur = it.next()
                if (prev.end > cur.start) {
                    it.remove()
                } else {
                    prev = cur
                }
            }
        }
    }

    /**
     * Extract URLs, @mentions, lists and #hashtag from a given text/tweet.
     *
     * @param text text of tweet
     * @return list of extracted entities
     */
    fun extractEntitiesWithIndices(text: String): List<Entity> = buildList {
        addAll(extractURLsWithIndices(text))
        addAll(extractHashtagsWithIndices(text, false))
        addAll(extractMentionsOrListsWithIndices(text))
        removeOverlappingEntities(this)
    }

    /**
     * Extract @username and an optional list reference from Tweet text. A mention is an occurrence
     * of @username anywhere in a Tweet. A mention with a list is a @username/list.
     *
     * @param text of the tweet from which to extract usernames
     * @return List of usernames (without the leading @ sign) and an optional lists referenced
     */
    private fun extractMentionsOrListsWithIndices(text: String): List<Entity> {
        if (text.isEmpty()) return emptyList()

        // Performance optimization.
        // If text doesn't contain @/＠ at all, the text doesn't
        // contain @mention. So we can simply return an empty list.
        var found = false
        for (c in text.toCharArray()) {
            if (c == '@' || c == '＠') {
                found = true
                break
            }
        }
        if (!found) {
            return emptyList()
        }
        val extracted: MutableList<Entity> = ArrayList()
        val matcher: Matcher = Regex.VALID_MENTION_OR_LIST.matcher(text)
        while (matcher.find()) {
            val after = text.substring(matcher.end())
            if (!Regex.INVALID_MENTION_MATCH_END.matcher(after).find()) {
                if (matcher.group(Regex.VALID_MENTION_OR_LIST_GROUP_LIST) == null) {
                    extracted.add(
                        Entity(
                            matcher,
                            Entity.Type.MENTION,
                            Regex.VALID_MENTION_OR_LIST_GROUP_USERNAME,
                        ),
                    )
                } else {
                    extracted.add(
                        Entity(
                            matcher.start(Regex.VALID_MENTION_OR_LIST_GROUP_USERNAME) - 1,
                            matcher.end(Regex.VALID_MENTION_OR_LIST_GROUP_LIST),
                            matcher.group(Regex.VALID_MENTION_OR_LIST_GROUP_USERNAME),
                            Entity.Type.MENTION,
                        ),
                    )
                }
            }
        }
        return extracted
    }

    /**
     * Extract URL references from Tweet text.
     *
     * @param text of the tweet from which to extract URLs
     * @return List of URLs referenced.
     */
    private fun extractURLsWithIndices(text: String?): List<Entity> {
        if (text.isNullOrEmpty() ||
            (if (isExtractURLWithoutProtocol) text.indexOf('.') else text.indexOf(':')) == -1
        ) {
            // Performance optimization.
            // If text doesn't contain '.' or ':' at all, text doesn't contain URL,
            // so we can simply return an empty list.
            return emptyList()
        }
        val urls: MutableList<Entity> = ArrayList()
        val matcher: Matcher = Regex.VALID_URL.matcher(text)
        while (matcher.find()) {
            val protocol = matcher.group(Regex.VALID_URL_GROUP_PROTOCOL)
            if (protocol.isNullOrEmpty()) {
                // skip if protocol is not present and 'extractURLWithoutProtocol' is false
                // or URL is preceded by invalid character.
                if (!isExtractURLWithoutProtocol ||
                    Regex.INVALID_URL_WITHOUT_PROTOCOL_MATCH_BEGIN
                        .matcher(matcher.group(Regex.VALID_URL_GROUP_BEFORE))
                        .matches()
                ) {
                    continue
                }
            }
            val url = matcher.group(Regex.VALID_URL_GROUP_URL)
            val start = matcher.start(Regex.VALID_URL_GROUP_URL)
            val end = matcher.end(Regex.VALID_URL_GROUP_URL)
            val host = matcher.group(Regex.VALID_URL_GROUP_DOMAIN)
            if (isValidHostAndLength(url.length, protocol, host)) {
                urls.add(Entity(start, end, url, Entity.Type.URL))
            }
        }
        return urls
    }

    /**
     * Extract #hashtag references from Tweet text.
     *
     * @param text of the tweet from which to extract hashtags
     * @param checkUrlOverlap if true, check if extracted hashtags overlap URLs and
     * remove overlapping ones
     * @return List of hashtags referenced (without the leading # sign)
     */
    private fun extractHashtagsWithIndices(text: String, checkUrlOverlap: Boolean): List<Entity> {
        if (text.isEmpty()) return emptyList()

        // Performance optimization.
        // If text doesn't contain #/＃ at all, text doesn't contain
        // hashtag, so we can simply return an empty list.
        var found = false
        for (c in text.toCharArray()) {
            if (c == '#' || c == '＃') {
                found = true
                break
            }
        }
        if (!found) {
            return emptyList()
        }
        val extracted: MutableList<Entity> = ArrayList()
        val matcher: Matcher = Regex.VALID_HASHTAG.matcher(text)
        while (matcher.find()) {
            val after = text.substring(matcher.end())
            if (!Regex.INVALID_HASHTAG_MATCH_END.matcher(after).find()) {
                extracted.add(
                    Entity(
                        matcher,
                        Entity.Type.HASHTAG,
                        Regex.VALID_HASHTAG_GROUP_TAG,
                    ),
                )
            }
        }
        if (checkUrlOverlap) {
            // extract URLs
            val urls = extractURLsWithIndices(text)
            if (urls.isNotEmpty()) {
                extracted.addAll(urls)
                // remove overlap
                removeOverlappingEntities(extracted)
                // remove URL entities
                val it = extracted.iterator()
                while (it.hasNext()) {
                    val entity = it.next()
                    if (entity.type != Entity.Type.HASHTAG) {
                        it.remove()
                    }
                }
            }
        }
        return extracted
    }

    /**
     * An efficient converter of indices between code points and code units.
     */
    private class IndexConverter(val text: String) {
        // Keep track of a single corresponding pair of code unit and code point
        // offsets so that we can re-use counting work if the next requested
        // entity is near the most recent entity.
        private var codePointIndex = 0
        private var charIndex = 0

        /**
         * Converts code units to code points
         *
         * @param charIndex Index into the string measured in code units.
         * @return The code point index that corresponds to the specified character index.
         */
        fun codeUnitsToCodePoints(charIndex: Int): Int {
            if (charIndex < this.charIndex) {
                codePointIndex -= text.codePointCount(charIndex, this.charIndex)
            } else {
                codePointIndex += text.codePointCount(this.charIndex, charIndex)
            }
            this.charIndex = charIndex

            // Make sure that charIndex never points to the second code unit of a
            // surrogate pair.
            if (charIndex > 0 && Character.isSupplementaryCodePoint(text.codePointAt(charIndex - 1))) {
                this.charIndex -= 1
            }
            return codePointIndex
        }

        /**
         * Converts code points to code units
         *
         * @param codePointIndex Index into the string measured in code points.
         * @return the code unit index that corresponds to the specified code point index.
         */
        fun codePointsToCodeUnits(codePointIndex: Int): Int {
            // Note that offsetByCodePoints accepts negative indices.
            charIndex = text.offsetByCodePoints(charIndex, codePointIndex - this.codePointIndex)
            this.codePointIndex = codePointIndex
            return charIndex
        }
    }

    companion object {
        /**
         * The maximum url length that the Twitter backend supports.
         */
        private const val MAX_URL_LENGTH = 4096

        /**
         * The backend adds http:// for normal links and https to *.twitter.com URLs
         * (it also rewrites http to https for URLs matching *.twitter.com).
         * We're better off adding https:// all the time. By making the assumption that
         * URL_GROUP_PROTOCOL_LENGTH is https, the trade off is we'll disallow a http URL
         * that is 4096 characters.
         */
        private const val URL_GROUP_PROTOCOL_LENGTH = "https://".length

        /**
         * Verifies that the host name adheres to RFC 3490 and 1035
         * Also, verifies that the entire url (including protocol) doesn't exceed MAX_URL_LENGTH
         *
         * @param originalUrlLength The length of the entire URL, including protocol if any
         * @param protocol The protocol used
         * @param originalHost The hostname to check validity of
         * @return true if the host is valid
         */
        fun isValidHostAndLength(
            originalUrlLength: Int,
            protocol: String?,
            originalHost: String?,
        ): Boolean {
            if (originalHost.isNullOrEmpty()) {
                return false
            }
            val originalHostLength = originalHost.length
            val host: String = try {
                // Use IDN for all host names, if the host is all ASCII, it returns unchanged.
                // It comes with an added benefit of checking host length to be between 1 and 63 characters.
                IDN.toASCII(originalHost, IDN.ALLOW_UNASSIGNED)
                // toASCII can throw IndexOutOfBoundsException when the domain name is longer than
                // 256 characters, instead of the documented IllegalArgumentException.
            } catch (e: IllegalArgumentException) {
                return false
            } catch (e: IndexOutOfBoundsException) {
                return false
            }
            val punycodeEncodedHostLength = host.length
            if (punycodeEncodedHostLength == 0) {
                return false
            }
            // The punycodeEncoded host length might be different now, offset that length from the URL.
            val urlLength = originalUrlLength + punycodeEncodedHostLength - originalHostLength
            // Add the protocol to our length check, if there isn't one,
            // to ensure it doesn't go over the limit.
            val urlLengthWithProtocol =
                urlLength + if (protocol == null) URL_GROUP_PROTOCOL_LENGTH else 0
            return urlLengthWithProtocol <= MAX_URL_LENGTH
        }
    }
}
