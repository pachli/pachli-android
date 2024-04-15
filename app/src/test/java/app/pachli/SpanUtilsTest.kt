package app.pachli

import app.pachli.core.testing.fakes.FakeSpannable
import app.pachli.util.highlightSpans
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Enclosed::class)
class SpanUtilsTest {
    @Test
    fun matchesMixedSpans() {
        val input = "one #one two: @two three : https://thr.ee/meh?foo=bar&wat=@at#hmm four #four five @five ろく#six"
        val inputSpannable = FakeSpannable(input)
        highlightSpans(inputSpannable, 0xffffff)
        val spans = inputSpannable.spans
        Assert.assertEquals(6, spans.size)
    }

    @Test
    fun doesntMergeAdjacentURLs() {
        val firstURL = "http://first.bar"
        val secondURL = "https://second.bar"
        val inputSpannable = FakeSpannable("$firstURL $secondURL")
        highlightSpans(inputSpannable, 0xffffff)
        val spans = inputSpannable.spans
        Assert.assertEquals(2, spans.size)
        Assert.assertEquals(firstURL.length, spans[0].end - spans[0].start)
        Assert.assertEquals(secondURL.length, spans[1].end - spans[1].start)
    }

    @RunWith(Parameterized::class)
    class MatchingTests(private val thingToHighlight: String) {
        companion object {
            @Parameterized.Parameters(name = "{0}")
            @JvmStatic
            fun data(): Iterable<Any> {
                return listOf(
                    "@mention",
                    "#tag",
                    "#tåg",
                    "https://thr.ee/meh?foo=bar&wat=@at#hmm",
                    "http://thr.ee/meh?foo=bar&wat=@at#hmm",
                )
            }
        }

        @Test
        fun matchesSpanAtStart() {
            val inputSpannable = FakeSpannable(thingToHighlight)
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertEquals(1, spans.size)
            Assert.assertEquals(thingToHighlight.length, spans[0].end - spans[0].start)
        }

        @Test
        fun matchesSpanNotAtStart() {
            val inputSpannable = FakeSpannable(" $thingToHighlight")
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertEquals(1, spans.size)
            Assert.assertEquals(thingToHighlight.length, spans[0].end - spans[0].start)
        }

        @Test
        fun doesNotMatchSpanEmbeddedInText() {
            val inputSpannable = FakeSpannable("aa${thingToHighlight}aa")
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertTrue(spans.isEmpty())
        }

        @Test
        fun spansDoNotOverlap() {
            val begin = "@begin"
            val end = "#end"
            val inputSpannable = FakeSpannable("$begin $thingToHighlight $end")
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertEquals(3, spans.size)

            val middleSpan = spans.single { span -> span.start > 0 && span.end < inputSpannable.lastIndex }
            Assert.assertEquals(begin.length + 1, middleSpan.start)
            Assert.assertEquals(inputSpannable.length - end.length - 1, middleSpan.end)
        }
    }

    @RunWith(Parameterized::class)
    class HighlightingTestsForTag(
        private val text: String,
        private val expectedStartIndex: Int,
        private val expectedEndIndex: Int,
    ) {
        companion object {
            @Parameterized.Parameters(name = "{0}")
            @JvmStatic
            fun data(): Iterable<Any> {
                return listOf(
                    arrayOf("#test", 0, 5),
                    arrayOf(" #AfterSpace", 1, 12),
                    arrayOf("#BeforeSpace ", 0, 12),
                    arrayOf("@#after_at", 1, 10),
                    arrayOf("あいうえお#after_hiragana", 5, 20),
                    arrayOf("##DoubleHash", 1, 12),
                    arrayOf("###TripleHash", 2, 13),
                )
            }
        }

        @Test
        fun matchExpectations() {
            val inputSpannable = FakeSpannable(text)
            highlightSpans(inputSpannable, 0xffffff)
            val spans = inputSpannable.spans
            Assert.assertEquals(1, spans.size)
            val span = spans.first()
            Assert.assertEquals(expectedStartIndex, span.start)
            Assert.assertEquals(expectedEndIndex, span.end)
        }
    }
}
