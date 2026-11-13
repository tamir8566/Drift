package com.tamir.drift.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tamir.drift.R

/** Heebo (OFL) in 300/400/500/700 — bundled under res/font. */
val Heebo = FontFamily(
    Font(R.font.heebo_300, FontWeight.Light),
    Font(R.font.heebo_400, FontWeight.Normal),
    Font(R.font.heebo_500, FontWeight.Medium),
    Font(R.font.heebo_700, FontWeight.Bold),
)

/** Text styles taken from the design mockup. */
object DriftType {
    /** 68sp, weight 300, letter-spacing −3, line-height 0.9. */
    val Hero = TextStyle(fontFamily = Heebo, fontSize = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp, lineHeight = 61.sp)
    val HeroCurrency = TextStyle(fontFamily = Heebo, fontSize = 24.sp, fontWeight = FontWeight.Light)
    /** Header date: 13sp, 500, letter-spacing 2. */
    val HeaderLabel = TextStyle(fontFamily = Heebo, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
    /** Section title: 13sp, 500, letter-spacing 1. */
    val SectionTitle = TextStyle(fontFamily = Heebo, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    val Label13 = TextStyle(fontFamily = Heebo, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val Label12 = TextStyle(fontFamily = Heebo, fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val Label11 = TextStyle(fontFamily = Heebo, fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val Tag10 = TextStyle(fontFamily = Heebo, fontSize = 10.sp, fontWeight = FontWeight.Normal)
    val Percent11 = TextStyle(fontFamily = Heebo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val Link12 = TextStyle(fontFamily = Heebo, fontSize = 12.sp, fontWeight = FontWeight.Normal)
    /** Stat / category value: 20sp, 700. */
    val Value20 = TextStyle(fontFamily = Heebo, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    /** Value of a three-column stat tile: 18sp, 700 — three values must fit side by side on a 390dp screen. */
    val Value18 = TextStyle(fontFamily = Heebo, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    /** Transaction row: name 14sp 500, amount 15sp 700. */
    val RowTitle = TextStyle(fontFamily = Heebo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val RowAmount = TextStyle(fontFamily = Heebo, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val Body = TextStyle(fontFamily = Heebo, fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val Title = TextStyle(fontFamily = Heebo, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    val Big = TextStyle(fontFamily = Heebo, fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
}

val DriftTypography = Typography(
    displayLarge = DriftType.Hero,
    headlineSmall = DriftType.Title,
    titleMedium = DriftType.RowTitle,
    bodyLarge = DriftType.Body,
    bodyMedium = DriftType.Label13,
    bodySmall = DriftType.Label12,
    labelLarge = DriftType.RowTitle,
    labelMedium = DriftType.Label12,
    labelSmall = DriftType.Label11,
)
