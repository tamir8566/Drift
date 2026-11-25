package com.tamir.drift.domain.parser

/**
 * Finds where a piece of text the user marked in the calibration screen sits inside the
 * normalised text the pattern is built from.
 *
 * The same characters can appear more than once ("12" in the date and "12" as the amount), so the
 * first match is not good enough: the mark is the n-th occurrence in the original text, and the
 * same n-th occurrence is taken in the normalised one (normalising only removes invisible marks
 * and collapses spaces — it never adds or removes an occurrence).
 */
object MarkLocator {

    /** How many times [piece] occurs in [text] before position [before] (occurrences do not overlap). */
    fun occurrencesBefore(text: String, piece: String, before: Int): Int {
        if (piece.isEmpty()) return 0
        var count = 0
        var from = 0
        while (true) {
            val i = text.indexOf(piece, from)
            if (i < 0 || i >= before) return count
            count++
            from = i + piece.length
        }
    }

    /** Start of occurrence number [n] (0 = the first) of [piece] in [text], or -1. */
    fun nthIndexOf(text: String, piece: String, n: Int): Int {
        if (piece.isEmpty() || n < 0) return -1
        var from = 0
        var seen = 0
        while (true) {
            val i = text.indexOf(piece, from)
            if (i < 0) return -1
            if (seen == n) return i
            seen++
            from = i + piece.length
        }
    }

    /**
     * Where [piece], marked at [markStart] in [original], begins in [normalized]: the same
     * occurrence of it. Falls back to the first occurrence when the position is unknown
     * ([markStart] < 0) or does not line up; -1 when [piece] is not there at all.
     */
    fun locate(original: String, normalized: String, piece: String, markStart: Int): Int {
        if (piece.isEmpty()) return -1
        val first = normalized.indexOf(piece)
        if (markStart < 0 || markStart > original.length) return first
        val inMark = original.indexOf(piece, markStart)
        if (inMark < 0) return first
        val nth = nthIndexOf(normalized, piece, occurrencesBefore(original, piece, inMark))
        return if (nth >= 0) nth else first
    }
}
