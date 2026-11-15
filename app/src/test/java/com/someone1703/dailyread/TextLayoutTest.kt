package com.someone1703.dailyread

import com.someone1703.dailyread.data.Reading
import com.someone1703.dailyread.text.BlockKind
import com.someone1703.dailyread.text.PageLayout
import com.someone1703.dailyread.text.PageText
import com.someone1703.dailyread.text.PendingPages
import com.someone1703.dailyread.text.RawLine
import com.someone1703.dailyread.text.RawPage
import com.someone1703.dailyread.text.ReadBlock
import com.someone1703.dailyread.text.ReadingText
import com.someone1703.dailyread.text.TextBlock
import com.someone1703.dailyread.text.TextClean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutTest {

    private fun body(text: String, y: Float, x: Float = 50f) = RawLine(text, x, 400f, y, 10f)

    private fun page(n: Int, vararg lines: RawLine) = RawPage(n, lines.toList(), 450f, 650f, hasImages = false)

    @Test
    fun joinsBrokenWordsButKeepsRealHyphens() {
        assertEquals("framework", TextClean.joinParagraphs("frame-", "work"))
        assertEquals("Euro-Asian", TextClean.joinParagraphs("Euro-", "Asian"))
        assertEquals("one two", TextClean.joinParagraphs("one", "two"))
    }

    @Test
    fun findsHeadingsParagraphsAndDropsRunningHeaders() {
        val pages = (0 until 4).map { n ->
            page(
                n,
                RawLine("My Book · Part One", 50f, 200f, 30f, 8f),
                RawLine("Chapter ${n + 1}", 50f, 200f, 90f, 20f),
                RawLine("A Title Here", 50f, 200f, 120f, 16f),
                body("    The first line of a para-", 100f + 60f, x = 64f),
                body("graph goes on here and ends.", 172f),
                body("A new paragraph starts indented", 196f, x = 64f),
                body("and keeps going a while longer", 208f),
                RawLine("${n + 10}", 220f, 230f, 630f, 8f),
            )
        }
        val layout = PageLayout(pages)
        val blocks = layout.blocks(pages[0])
        assertEquals(BlockKind.HEADING, blocks[0].kind)
        assertEquals("Chapter 1: A Title Here", blocks[0].text)
        assertEquals(1, blocks[0].level)
        assertEquals("The first line of a paragraph goes on here and ends.", blocks[1].text)
        assertEquals("A new paragraph starts indented and keeps going a while longer", blocks[2].text)
        assertEquals(3, blocks.size)  // header and page number gone
    }

    @Test
    fun cutsSharedPagesAtTheNextHeadingAndJoinsAcrossPages() {
        val pages = mapOf(
            0 to PageText(0, listOf(TextBlock(BlockKind.HEADING, "Topic 1. Start", 2), TextBlock(BlockKind.PARAGRAPH, "It runs on"))),
            1 to PageText(1, listOf(
                TextBlock(BlockKind.PARAGRAPH, "to the next page."),
                TextBlock(BlockKind.HEADING, "Topic 2. Next", 2),
                TextBlock(BlockKind.PARAGRAPH, "Second topic."),
            )),
        )
        val first = Reading("Topic 1. Start", startPage = 0, endPage = 0)
        val second = Reading("Topic 2. Next", startPage = 1, endPage = 1)
        val a = ReadingText.assemble(first, second, { pages[it] }, { false }).map { (it as ReadBlock).text }
        assertEquals(listOf("Topic 1. Start", "It runs on to the next page."), a)
        val b = ReadingText.assemble(second, null, { pages[it] }, { false }).map { (it as ReadBlock).text }
        assertEquals(listOf("Topic 2. Next", "Second topic."), b)
    }

    @Test
    fun renamedReadingStillCutsAtItsOriginalHeading() {
        val pages = mapOf(0 to PageText(0, listOf(
            TextBlock(BlockKind.PARAGRAPH, "End of the last one."),
            TextBlock(BlockKind.HEADING, "Topic 2. Next", 2),
            TextBlock(BlockKind.PARAGRAPH, "Body."),
        )))
        val renamed = Reading("My own name", startPage = 0, endPage = 0, anchor = "Topic 2. Next")
        val items = ReadingText.assemble(renamed, null, { pages[it] }, { false })
        assertEquals("Topic 2. Next", (items.first() as ReadBlock).text)
    }

    @Test
    fun scannedPagesShowAsPending() {
        val r = Reading("Day 1", startPage = 0, endPage = 2)
        val items = ReadingText.assemble(r, null, { if (it == 0) PageText(0, listOf(TextBlock(BlockKind.PARAGRAPH, "Hi."))) else null }, { it > 0 })
        assertTrue(items[1] is PendingPages)
        assertEquals(listOf(1, 2), (items[1] as PendingPages).pages)
    }
}
