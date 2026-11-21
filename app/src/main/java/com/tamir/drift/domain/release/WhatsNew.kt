package com.tamir.drift.domain.release

/**
 * "מה חדש": the bundled release notes are shown once, in a small dialog, after the app starts on a
 * new version whose notes differ from the ones shown last. A first install shows nothing.
 */
object WhatsNew {

    enum class Decision {
        /** Show the dialog; record the version and the notes when it is closed. */
        SHOW,

        /** Nothing to show, but remember this version (and its notes) as seen. */
        RECORD,

        /** Same version as last time: nothing to do. */
        NONE,
    }

    /** One line of the notes file: a line that starts with "#" is a small heading. */
    data class Line(val text: String, val isHeading: Boolean)

    /** Identifies a notes text (ignoring line-ending and outer-whitespace differences); "" for blank notes. */
    fun fingerprint(notes: String): String {
        val normalized = normalize(notes)
        if (normalized.isEmpty()) return ""
        return "${normalized.length}-${normalized.hashCode()}"
    }

    /**
     * @param lastVersion the versionCode recorded at the previous start; 0 = none recorded.
     * @param lastFingerprint the [fingerprint] of the notes shown last; "" = none.
     * @param freshInstall the app was just installed (not updated).
     */
    fun decide(currentVersion: Int, lastVersion: Int, fingerprint: String, lastFingerprint: String, freshInstall: Boolean): Decision {
        if (currentVersion == lastVersion) return Decision.NONE
        if (fingerprint.isEmpty() || freshInstall || fingerprint == lastFingerprint) return Decision.RECORD
        return Decision.SHOW
    }

    /** The non-empty lines of the notes, in order. */
    fun lines(notes: String): List<Line> = normalize(notes).split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { raw ->
            if (raw.startsWith("#")) Line(raw.trimStart('#').trim(), isHeading = true) else Line(raw, isHeading = false)
        }
        .filter { it.text.isNotEmpty() }

    private fun normalize(notes: String): String = notes.replace("\r\n", "\n").replace('\r', '\n').trim()
}
