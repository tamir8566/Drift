package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParsedTransaction

/** A parser for one capture source. Pure Kotlin, stateless. */
interface SourceParser {
    val name: String
    fun parse(text: String): ParsedTransaction
}
