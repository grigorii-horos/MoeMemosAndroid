package me.mudkip.moememos.data.coffer

/** A Coffer note's text fields — what one memo's markdown splits into. */
data class NoteParts(val title: String, val text: String, val tags: List<String>)

/**
 * Memo markdown ↔ a Coffer note's fields: the first non-empty line is the title (heading marks
 * stripped), the rest is the text, and the note's labels are the LAST paragraph when it holds
 * nothing but `#label` words — the line [join] writes so the app can list and show them. Coffer's
 * markdown has no #tag syntax (2026-10-27): any other `#word` is text and stays where it is.
 */
object CofferCompose {
    /** One word of the labels line: `#`, then the label (its spaces written as no-break spaces). */
    private val LABEL = Regex("""#([^\s#]+)""")
    private const val NBSP = '\u00A0'

    // Fenced blocks, inline code spans and `[label](dest)` links — never cut, never tags.
    private val CODE = Regex(
        """^ {0,3}(`{3,}|~{3,})[^\n]*\n[\s\S]*?(?:\n {0,3}\1[`~]*[ \t]*(?=\n|$)|$)|(`+)(?!`)[\s\S]*?(?<!`)\2(?!`)|!?\[[^\]\n]*]\([^)\n]*\)""",
        RegexOption.MULTILINE,
    )

    private val HEADING = Regex("""^\s*#+\s*""")

    /** Alternating plain/protected runs of `md`, plain first. */
    internal fun segments(md: String): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        var at = 0
        for (m in CODE.findAll(md)) {
            out += md.substring(at, m.range.first) to false
            out += m.value to true
            at = m.range.last + 1
        }
        out += md.substring(at) to false
        return out
    }

    /** The labels line, if the last paragraph is one, and what precedes it. */
    private fun takeLabels(md: String): Pair<String, List<String>> {
        val trimmed = md.trimEnd()
        val start = trimmed.lastIndexOf("\n\n").let { if (it < 0) 0 else it + 2 }
        val words = trimmed.substring(start).trim().split(Regex("[ \t]+"))
        if (words.isEmpty() || words.any { !LABEL.matches(it) }) return md to emptyList()
        val labels = words.map { LABEL.matchEntire(it)!!.groupValues[1].replace(NBSP, ' ') }.distinct()
        return trimmed.substring(0, start) to labels
    }

    fun split(md: String): NoteParts {
        val (src, tags) = takeLabels(md)
        val lines = src.split('\n')
        val at = lines.indexOfFirst { it.isNotBlank() }
        if (at == -1) return NoteParts("", "", tags)
        return NoteParts(
            title = lines[at].replace(HEADING, "").trim(),
            text = lines.drop(at + 1).joinToString("\n").trim(),
            tags = tags,
        )
    }

    private val LINK = Regex("""^(!?\[[^\]\n]*]\()([^)\s]*)(.*)$""", RegexOption.DOT_MATCHES_ALL)

    /** Rewrites every link/image destination outside code; [dest] returns the new one or null. */
    private fun mapLinks(md: String, dest: (String) -> String?): String =
        segments(md).joinToString("") { (s, protected) ->
            val m = if (protected) LINK.matchEntire(s) else null
            val to = m?.let { dest(it.groupValues[2]) }
            if (m == null || to == null) s else m.groupValues[1] + to + m.groupValues[3]
        }

    /**
     * Coffer links a file of the note by its bare name (`![](foto.jpg)`); MoeMemos needs a URL.
     * [files] are the note's stored file paths (`notes/notes/8/foto.jpg`).
     */
    fun bareToUrl(md: String, files: List<String>, uploadsBase: String): String {
        val byName = files.associateBy { it.substringAfterLast('/') }
        return mapLinks(md) { d -> byName[d]?.let { uploadsBase + it } }
    }

    /** The inverse: a URL into this note's own directory ([recordBase]) becomes the bare name. */
    fun urlToBare(md: String, recordBase: String): String = mapLinks(md) { d ->
        d.removePrefix(recordBase).takeIf { d.startsWith(recordBase) && it.isNotEmpty() && '/' !in it }
    }

    /**
     * An empty title is written as a bare `#` line — an empty heading, invisible when rendered —
     * so [split] reads it back as empty instead of promoting the text's first line.
     */
    fun join(p: NoteParts): String {
        val body = listOfNotNull(
            p.text.takeIf { it.isNotBlank() },
            p.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#" + it.replace(' ', NBSP) },
        )
        if (p.title.isBlank() && body.isEmpty()) return ""
        return (listOf(if (p.title.isBlank()) "#" else "# ${p.title}") + body).joinToString("\n\n")
    }
}
