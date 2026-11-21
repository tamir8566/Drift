package com.tamir.drift.domain.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WhatsNewTest {

    private val notes = "# חדש\nגיבוי ושחזור\nאנימציות עדינות\n"
    private val print = WhatsNew.fingerprint(notes)

    @Test
    fun `same version shows nothing`() {
        assertEquals(WhatsNew.Decision.NONE, WhatsNew.decide(12, 12, print, "", freshInstall = false))
    }

    @Test
    fun `an update with new notes shows them`() {
        assertEquals(WhatsNew.Decision.SHOW, WhatsNew.decide(13, 12, print, WhatsNew.fingerprint("ישן"), freshInstall = false))
        // The first version that carries notes: nothing was recorded before.
        assertEquals(WhatsNew.Decision.SHOW, WhatsNew.decide(13, 0, print, "", freshInstall = false))
    }

    @Test
    fun `an update with the same notes only records the version`() {
        assertEquals(WhatsNew.Decision.RECORD, WhatsNew.decide(13, 12, print, print, freshInstall = false))
    }

    @Test
    fun `a first install records without showing`() {
        assertEquals(WhatsNew.Decision.RECORD, WhatsNew.decide(13, 0, print, "", freshInstall = true))
    }

    @Test
    fun `blank notes are never shown`() {
        assertEquals("", WhatsNew.fingerprint("  \n\r\n "))
        assertEquals(WhatsNew.Decision.RECORD, WhatsNew.decide(13, 12, "", print, freshInstall = false))
    }

    @Test
    fun `the fingerprint ignores line endings and outer blank space but not the text`() {
        assertEquals(print, WhatsNew.fingerprint("\r\n# חדש\r\nגיבוי ושחזור\r\nאנימציות עדינות\r\n\r\n"))
        assertNotEquals(print, WhatsNew.fingerprint(notes + "עוד שורה"))
    }

    @Test
    fun `lines are split into headings and items without blanks`() {
        assertEquals(
            listOf(
                WhatsNew.Line("חדש", isHeading = true),
                WhatsNew.Line("גיבוי ושחזור", isHeading = false),
                WhatsNew.Line("אנימציות עדינות", isHeading = false),
            ),
            WhatsNew.lines("# חדש\n\n  גיבוי ושחזור  \n#\nאנימציות עדינות"),
        )
    }
}
