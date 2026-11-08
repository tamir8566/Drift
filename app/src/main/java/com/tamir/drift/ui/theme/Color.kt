package com.tamir.drift.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette — exactly the PRD design table. Nothing outside it may be used in the UI. */
object DriftColors {
    val Background = Color(0xFF050B08)
    val Text = Color(0xFFEEF3EC)
    val TextSecondary = Color(0xFF8FA89B)
    val TextDim = Color(0xFF6F857A)
    val Lime = Color(0xFFC5F36F)
    val Teal = Color(0xFF2EE6C5)
    val Orange = Color(0xFFFFB26B)

    /** Glass card: white 3% fill, 1dp white 7% border. */
    val GlassFill = Color.White.copy(alpha = 0.03f)
    val GlassBorder = Color.White.copy(alpha = 0.07f)

    /** Budget line track (white 12%), category bar track (white 10%), row divider (white 7%). */
    val LineTrack = Color.White.copy(alpha = 0.12f)
    val BarTrack = Color.White.copy(alpha = 0.10f)
    val Divider = Color.White.copy(alpha = 0.07f)
    val StatDivider = Color.White.copy(alpha = 0.10f)

    /** Plus button border: lime 45%. */
    val PlusBorder = Lime.copy(alpha = 0.45f)

    /** Background glows. */
    val GlowLime = Lime.copy(alpha = 0.14f)
    val GlowTeal = Teal.copy(alpha = 0.08f)
}
