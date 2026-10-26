package me.mudkip.moememos.data.coffer

/** A Coffer note's text fields — what one memo's markdown splits into. */
data class NoteParts(val title: String, val text: String, val tags: List<String>)

/**
 * Memo markdown ↔ a Coffer note's fields. A port of Coffer's web composer split
 * (`packages/web-ui/src/list/compose-split.ts`) plus its inverse: `#word` tags are cut into
 * the tags, the first non-empty line becomes the title (heading marks stripped), the rest is
 * the text. Code (fences, spans) and links keep their `#`s.
 */
object CofferCompose {
    // A tag as Coffer's grammar reads it: `#`, a letter, then letters, digits, `_`, `-`,
    // at the start or after whitespace, `(` or `[`.
    private val TAG = Regex("""(?<![^\s(\[])#(\p{L}[\p{L}\p{N}_-]*)""")

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

    fun split(md: String): NoteParts {
        val parts = segments(md)
        val tags = parts.filterNot { it.second }
            .flatMap { (s, _) -> TAG.findAll(s).map { it.groupValues[1] }.toList() }
            .distinct()
        // Only the tag and the gap around it go: a tag opening a line takes the space after it,
        // elsewhere the gap before it.
        val cuts = tags.map { name ->
            val tag = "#" + Regex.escape(name) + """(?![\p{L}\p{N}_-])"""
            Regex("""(?<![^\n])[ \t]*$tag[ \t]*|[ \t]*(?<![^\s(\[])$tag""")
        }
        val src = parts.joinToString("") { (s, code) ->
            if (code) s else cuts.fold(s) { t, re -> re.replace(t, "") }
        }
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

    fun join(p: NoteParts): String = listOfNotNull(
        p.title.takeIf { it.isNotBlank() }?.let { "# $it" },
        p.text.takeIf { it.isNotBlank() },
        p.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
    ).joinToString("\n\n")
}
