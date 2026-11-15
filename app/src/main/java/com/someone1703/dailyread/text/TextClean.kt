package com.someone1703.dailyread.text

/** Small text fixes shared by the PDF text pass and text recognition. */
object TextClean {

    private val LIGATURES = mapOf(
        'ﬀ' to "ff", 'ﬁ' to "fi", 'ﬂ' to "fl", 'ﬃ' to "ffi", 'ﬄ' to "ffl", 'ﬅ' to "st", 'ﬆ' to "st",
    )

    /** "Chapter 3", "CHAPTER THREE", "Part II", "Part One: ..." */
    val CHAPTER_LABEL = Regex(
        "^(chapter|part|book)\\s+([0-9]+|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|" +
            "eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty)\\b.*",
        RegexOption.IGNORE_CASE,
    )

    /** Just the label, with nothing after it: "Chapter 3", "PART II". */
    val CHAPTER_LABEL_ONLY = Regex(
        "^(chapter|part|book)\\s+([0-9]+|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|" +
            "eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty)[.:]?$",
        RegexOption.IGNORE_CASE,
    )

    private val PAGE_NUMBER = Regex("^(page\\s+)?([0-9]{1,4}|[ivxlcdm]{1,7})$", RegexOption.IGNORE_CASE)
    private val LIST_ITEM = Regex("^([•●◦▪■–\\-*]|[0-9]{1,2}[.)]|[a-z][.)])\\s+.*")
    private val TERMINAL = setOf('.', '!', '?', ':', '"', '”', '’', '\'', ')', ';')

    fun line(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when {
                c == '­' -> Unit  // soft hyphen: only a line-break hint
                c in LIGATURES -> sb.append(LIGATURES.getValue(c))
                c == ' ' || c == '\t' -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString().replace(Regex(" {2,}"), " ").trim()
    }

    fun isPageNumber(text: String) = PAGE_NUMBER.matches(text.trim())

    fun isListItem(text: String) = LIST_ITEM.matches(text)

    fun endsSentence(text: String) = text.trimEnd().lastOrNull()?.let { it in TERMINAL } == true

    /**
     * Appends [next] to [current] as the next line of the same paragraph, removing the hyphen of a
     * word broken across lines ("frame-" + "work" -> "framework") but keeping real hyphens before a
     * capital or digit ("Euro-" + "Asian").
     */
    fun appendLine(current: StringBuilder, next: String) {
        if (next.isEmpty()) return
        if (current.isEmpty()) {
            current.append(next)
            return
        }
        val last = current.last()
        val beforeLast = if (current.length >= 2) current[current.length - 2] else ' '
        when {
            last == '-' && beforeLast.isLetter() && next.first().isLowerCase() -> {
                current.setLength(current.length - 1)
                current.append(next)
            }
            // A real hyphen ("Euro-" + "Asian") or a dash/slash: no space.
            last == '-' && beforeLast.isLetter() -> current.append(next)
            last == '—' || last == '/' -> current.append(next)
            else -> current.append(' ').append(next)
        }
    }

    /** Joins two parts of a paragraph split across a page break. */
    fun joinParagraphs(first: String, second: String): String =
        StringBuilder(first).also { appendLine(it, second) }.toString()

    /** Whether [next] looks like the rest of a paragraph that [previous] left unfinished. */
    fun continues(previous: String, next: String): Boolean {
        if (previous.isEmpty() || next.isEmpty()) return false
        if (isListItem(next)) return false
        if (previous.endsWith("-")) return true
        if (!endsSentence(previous)) return true
        return next.first().isLowerCase()
    }

    /** Lowercase letters and digits only, for comparing a bookmark title with a heading. */
    fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    fun titlesMatch(title: String, heading: String): Boolean {
        val a = normalize(title)
        val b = normalize(heading)
        if (a.length < 3 || b.length < 3) return a == b && a.isNotEmpty()
        return a == b || a.contains(b) || b.contains(a)
    }
}
