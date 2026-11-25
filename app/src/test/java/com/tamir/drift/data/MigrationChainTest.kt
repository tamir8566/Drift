package com.tamir.drift.data

import com.tamir.drift.data.db.DRIFT_DB_VERSION
import com.tamir.drift.data.db.DriftDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The database has no destructive fallback: a version step without a migration makes the app
 * refuse to open the user's data. This test fails the build before such a version can ship.
 */
class MigrationChainTest {

    @Test
    fun `every version step up to the current schema version has a migration`() {
        val steps = DriftDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.sortedBy { it.first }
        val expected = (1 until DRIFT_DB_VERSION).map { it to it + 1 }
        assertEquals(expected, steps)
    }
}
