package com.tamir.drift.domain.export

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvBuilderTest {
    @Test
    fun `csv has bom, header and escaped fields`() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val t = TxnSummary(1, 180.0, 60.0, "וולט, פיצה", CategoryKeys.EATING_OUT, 1_762_600_000_000L, TransactionSource.GPAY, isShared = true)
        val csv = CsvBuilder.build(listOf(CsvBuilder.Row(t, "אוכל בחוץ", "עם \"חברים\"")), listOf("תאריך", "בית עסק", "קטגוריה", "סכום", "שלי", "משותף", "מקור", "הערה"), zone)
        assertTrue(csv.startsWith("﻿"))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"וולט, פיצה\""))
        assertTrue(lines[1].contains("180.00,60.00,1,GPAY"))
        assertTrue(lines[1].endsWith("\"עם \"\"חברים\"\"\""))
    }
}
