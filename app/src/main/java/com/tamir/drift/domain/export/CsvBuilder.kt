package com.tamir.drift.domain.export

import com.tamir.drift.domain.model.TxnSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Builds a UTF-8 (with BOM, so Excel opens Hebrew correctly) CSV of transactions. */
object CsvBuilder {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    data class Row(
        val txn: TxnSummary,
        val categoryName: String,
        val note: String?,
    )

    /**
     * @param headers column titles in display language (passed in so the domain stays free of resources).
     */
    fun build(rows: List<Row>, headers: List<String>, zone: ZoneId): String {
        val sb = StringBuilder()
        sb.append('﻿')
        sb.appendLine(headers.joinToString(",") { escape(it) })
        for (row in rows.sortedBy { it.txn.timestamp }) {
            val t = row.txn
            val date = formatter.format(Instant.ofEpochMilli(t.timestamp).atZone(zone))
            sb.appendLine(
                listOf(
                    date,
                    t.merchant,
                    row.categoryName,
                    format(t.amountTotal),
                    format(t.myShare),
                    if (t.isShared) "1" else "0",
                    t.source.name,
                    row.note.orEmpty(),
                ).joinToString(",") { escape(it) },
            )
        }
        return sb.toString()
    }

    private fun format(d: Double) = String.format(java.util.Locale.US, "%.2f", d)

    private fun escape(s: String): String {
        val needsQuotes = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = s.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
