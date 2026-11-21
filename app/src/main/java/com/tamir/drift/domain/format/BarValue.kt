package com.tamir.drift.domain.format

import kotlin.math.roundToInt

/**
 * One value against its reference — the single source for a bar and for the percent printed
 * next to it, so the two can never disagree: the bar draws [fraction], the text shows [percent].
 *
 *  - above the reference: the bar is full ([isOver]) and the text keeps the real percent (130%);
 *  - below zero: the bar is empty ([isNegative]) and the text keeps the negative value;
 *  - no reference (≤ 0): any positive value fills the bar.
 */
data class BarValue(val value: Double, val reference: Double) {

    private val ratio: Double get() = when {
        value.isNaN() || reference.isNaN() -> 0.0
        reference > 0.0 -> value / reference
        value > 0.0 -> 1.0
        else -> 0.0
    }

    /** What the bar draws, 0..1. */
    val fraction: Float get() = ratio.coerceIn(0.0, 1.0).toFloat()

    /** What the text shows: the real, unclamped percent. */
    val percent: Int get() = (ratio * 100.0).roundToInt()

    val isOver: Boolean get() = if (reference > 0.0) value > reference else value > 0.0

    val isNegative: Boolean get() = value < 0.0

    companion object {
        fun of(value: Long, reference: Long): BarValue = BarValue(value.toDouble(), reference.toDouble())

        /** A plain 0..1 (or beyond) fraction, e.g. a share of the total. */
        fun ofFraction(fraction: Double): BarValue = BarValue(fraction, 1.0)

        val EMPTY = BarValue(0.0, 0.0)
    }
}
