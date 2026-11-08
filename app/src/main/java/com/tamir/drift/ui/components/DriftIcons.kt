package com.tamir.drift.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons (stroke only, no fill) built from the SVG paths of the design mockup.
 * Tint is applied by [androidx.compose.material3.Icon], so the stroke color here is a placeholder.
 */
object DriftIcons {

    private fun line(name: String, strokeWidth: Float = 1.8f, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name, defaultWidth = 22.dp, defaultHeight = 22.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Search: ImageVector by lazy { line("search", 1.8f, "M11,11m-7,0a7,7 0,1 1,14 0a7,7 0,1 1,-14 0", "M20,20l-3.5,-3.5") }
    val Menu: ImageVector by lazy { line("menu", 1.8f, "M4,7h16", "M4,12h16", "M4,17h16") }
    val Home: ImageVector by lazy { line("home", 1.8f, "M3,11L12,3l9,8v10a1,1 0,0 1,-1 1h-5v-7h-6v7H4a1,1 0,0 1,-1 -1z") }
    val Bars: ImageVector by lazy { line("bars", 1.8f, "M4,20V10", "M10,20V4", "M16,20v-8", "M22,20H2") }
    val Plus: ImageVector by lazy { line("plus", 2f, "M12,5v14", "M5,12h14") }
    val Minus: ImageVector by lazy { line("minus", 2f, "M5,12h14") }
    val Budget: ImageVector by lazy { line("budget", 1.8f, "M12,12m-9,0a9,9 0,1 1,18 0a9,9 0,1 1,-18 0", "M12,7v5l3,2") }
    val Savings: ImageVector by lazy { line("savings", 1.8f, "M12,12m-9,0a9,9 0,1 1,18 0a9,9 0,1 1,-18 0", "M12,12m-4,0a4,4 0,1 1,8 0a4,4 0,1 1,-8 0", "M12,3v3", "M12,18v3") }
    /** Back arrow for RTL: points to the right (towards the previous screen). */
    val Back: ImageVector by lazy { line("back", 1.8f, "M5,12h14", "M13,6l6,6l-6,6") }
    val Trash: ImageVector by lazy { line("trash", 1.8f, "M4,7h16", "M10,11v6", "M14,11v6", "M6,7l1,13h10l1,-13", "M9,7V4h6v3") }
    val Check: ImageVector by lazy { line("check", 2f, "M5,13l4,4L19,7") }
    val Close: ImageVector by lazy { line("close", 1.8f, "M6,6l12,12", "M18,6L6,18") }
    val Share: ImageVector by lazy { line("share", 1.8f, "M12,3v12", "M8,7l4,-4l4,4", "M5,12v8h14v-8") }
    val ChevronDown: ImageVector by lazy { line("chevron-down", 1.8f, "M6,9l6,6l6,-6") }
    val Calendar: ImageVector by lazy { line("calendar", 1.8f, "M4,6h16v14H4z", "M4,10h16", "M8,3v4", "M16,3v4") }
    val Settings: ImageVector by lazy { line("settings", 1.8f, "M12,12m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0", "M12,2v3", "M12,19v3", "M2,12h3", "M19,12h3", "M4.9,4.9l2.1,2.1", "M17,17l2.1,2.1", "M4.9,19.1l2.1,-2.1", "M17,7l2.1,-2.1") }
    val List: ImageVector by lazy { line("list", 1.8f, "M8,6h13", "M8,12h13", "M8,18h13", "M3,6h1", "M3,12h1", "M3,18h1") }
    /** Briefcase (work tab). */
    val Work: ImageVector by lazy { line("work", 1.8f, "M3,8h18v12H3z", "M9,8V5h6v3", "M3,13h18") }
    /** Target / flag (goal tab). */
    val Goal: ImageVector by lazy { line("goal", 1.8f, "M12,12m-9,0a9,9 0,1 1,18 0a9,9 0,1 1,-18 0", "M12,12m-5,0a5,5 0,1 1,10 0a5,5 0,1 1,-10 0", "M12,12m-1.5,0a1.5,1.5 0,1 1,3 0a1.5,1.5 0,1 1,-3 0") }
    /** Chevrons for month paging (physical direction; not mirrored). */
    val ChevronLeft: ImageVector by lazy { line("chevron-left", 1.8f, "M15,6l-6,6l6,6") }
    val ChevronRight: ImageVector by lazy { line("chevron-right", 1.8f, "M9,6l6,6l-6,6") }
    val Refresh: ImageVector by lazy { line("refresh", 1.8f, "M21,12a9,9 0,1 1,-3 -6.7", "M21,3v6h-6") }
}
