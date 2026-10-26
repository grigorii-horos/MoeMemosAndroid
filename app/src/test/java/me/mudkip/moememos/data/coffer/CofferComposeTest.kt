package me.mudkip.moememos.data.coffer

import org.junit.Assert.assertEquals
import org.junit.Test

class CofferComposeTest {
    private fun split(md: String) = CofferCompose.split(md)

    @Test
    fun firstLineBecomesTitle() =
        assertEquals(NoteParts("Buy filter", "Model as before", emptyList()), split("Buy filter\nModel as before"))

    @Test
    fun headingMarksStripped() = assertEquals(NoteParts("Plan", "body", emptyList()), split("## Plan\nbody"))

    @Test
    fun tagsCutAndCollectedInOrder() =
        assertEquals(NoteParts("T", "buy and", listOf("milk", "bread")), split("# T\nbuy #milk and #bread\n#milk"))

    @Test
    fun tagInCodeStaysText() {
        val md = "# T\n`#x` and\n```\n#y\n```"
        assertEquals(NoteParts("T", "`#x` and\n```\n#y\n```", emptyList()), split(md))
    }

    @Test
    fun tagInLinkStaysText() = assertEquals(NoteParts("T", "[#a](#b) c", emptyList()), split("# T\n[#a](#b) c"))

    @Test
    fun cyrillicTag() = assertEquals(listOf("молоко"), split("# T\nкупить #молоко").tags)

    @Test
    fun hashAfterLetterIsNotATag() = assertEquals(emptyList<String>(), split("# T\nC# and a#b").tags)

    @Test
    fun onlyTags() = assertEquals(NoteParts("", "", listOf("idea")), split("#idea"))

    @Test
    fun bareToUrlRewritesOnlyFilesOfTheNote() {
        val md = "![](foto.jpg) [m](manual.pdf) [s](example.com) `![](foto.jpg)`"
        val files = listOf("notes/notes/8/foto.jpg", "notes/notes/8/manual.pdf")
        assertEquals(
            "![](https://h/uploads/notes/notes/8/foto.jpg) [m](https://h/uploads/notes/notes/8/manual.pdf) [s](example.com) `![](foto.jpg)`",
            CofferCompose.bareToUrl(md, files, "https://h/uploads/"),
        )
    }

    @Test
    fun urlToBareIsTheInverseForTheOwnDirectory() {
        val files = listOf("notes/notes/8/foto.jpg")
        val md = "![x](foto.jpg \"t\") and [o](https://h/uploads/notes/notes/9/z.pdf)"
        val shown = CofferCompose.bareToUrl(md, files, "https://h/uploads/")
        assertEquals(md, CofferCompose.urlToBare(shown, "https://h/uploads/notes/notes/8/"))
    }

    @Test
    fun joinOmitsEmptyParts() {
        assertEquals("#\n\n#idea", CofferCompose.join(NoteParts("", "", listOf("idea"))))
        assertEquals("# T", CofferCompose.join(NoteParts("T", "", emptyList())))
        assertEquals("#\n\nonly", CofferCompose.join(NoteParts("", "only", emptyList())))
        assertEquals("", CofferCompose.join(NoteParts("", "", emptyList())))
    }

    @Test
    fun emptyTitleSurvivesTheEditorRoundTrip() {
        val parts = NoteParts("", "first line\nsecond", listOf("x"))
        assertEquals(parts, split(CofferCompose.join(parts)))
    }

    @Test
    fun memoTypedWithoutAHeadingStillPromotesItsFirstLine() =
        assertEquals(NoteParts("first", "rest", emptyList()), split("first\nrest"))

    @Test
    fun joinFull() =
        assertEquals("# Plan\n\nbody\n\n#a #b", CofferCompose.join(NoteParts("Plan", "body", listOf("a", "b"))))

    @Test
    fun roundTripIsStable() {
        for (md in listOf("## Plan\nbuy #a milk\n#b", "no heading\ntext", "#idea", "# T\n\n`#code` [x](#y) #t")) {
            val once = split(md)
            assertEquals(md, once, split(CofferCompose.join(once)))
        }
    }
}
