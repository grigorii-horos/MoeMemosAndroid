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
    fun joinOmitsEmptyParts() {
        assertEquals("#idea", CofferCompose.join(NoteParts("", "", listOf("idea"))))
        assertEquals("# T", CofferCompose.join(NoteParts("T", "", emptyList())))
        assertEquals("only", CofferCompose.join(NoteParts("", "only", emptyList())))
    }

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
