package com.tamir.drift.ui.components

import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.feedback.DriftSound
import com.tamir.drift.feedback.SoundPlayer
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The feedback package: motion, haptics and sound, each behind its own switch in
 * settings → "משוב". Everything here is quiet on purpose (PRD "כללי העיצוב": calm, no strong glow).
 *
 * [animations] is already combined with the system's animator duration scale: with the switch off
 * or the system scale at zero it is false, and every helper below snaps instead of moving.
 */
data class Feedback(val animations: Boolean = true, val haptics: Boolean = true, val sound: Boolean = true)

val LocalFeedback = compositionLocalOf { Feedback() }

/** Durations and sizes of the app's motion, in one place. */
object Motion {
    /** A hero number rolls from its old value to the new one. */
    const val HERO_ROLL_MS = 600

    /** The six month columns start one after the other. */
    const val COLUMN_STAGGER_MS = 40

    /** A new row's tint fades out. */
    const val LANDING_MS = 1200
    const val LANDING_ALPHA = 0.08f

    /** The plus menu and the sheets: fade and a short rise. */
    const val OVERLAY_MS = 120
    val OVERLAY_RISE = 8.dp

    /** The plus icon turns into a close mark while its menu is open. */
    const val PLUS_ROTATE_MS = 180
    const val PLUS_ROTATE_DEGREES = 45f

    /** A pushed screen slides in and fades; a tab only fades. */
    const val PUSH_MS = 220
    const val TAB_FADE_MS = 180

    /** The single ring of the "goal reached" / "week in surplus" moment. */
    const val RING_MS = 700
    const val RING_ALPHA = 0.6f
    val RING_FROM = 5.dp
    val RING_TO = 14.dp

    /** The status pill cross-fades when its text changes. */
    const val PILL_MS = 300
}

/**
 * Provides [LocalFeedback] from the three settings switches and the system animator scale (read
 * again whenever the app comes back to the front). [content] is composed once the settings are
 * loaded, so a user who switched animations off never sees a first frame of them.
 */
@Composable
fun ProvideFeedback(content: @Composable () -> Unit) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val settings by graph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val systemAnimations: Boolean = remember(refresh) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    // The settings arrive a moment after the first frame; the content waits for them (the window
    // background is already the app's), so whether things move never depends on which loaded first.
    val loaded = settings ?: return
    val animations = loaded.feedbackAnimations && systemAnimations
    val haptics = loaded.feedbackHaptics
    val sound = loaded.feedbackSound
    val feedback = remember(animations, haptics, sound) { Feedback(animations, haptics, sound) }
    CompositionLocalProvider(LocalFeedback provides feedback, content = content)
}

// ---------------------------------------------------------------------------------------- bars

/**
 * The fraction a bar draws right now. The first time the bar appears it grows from zero to
 * [target] ([BAR_ANIM_MS], after [delayMillis] — the stagger of a row of columns); later changes
 * move from the value it shows. "Appeared" is saved, so scrolling away and back, or returning to
 * the screen, does not grow the bar again. With animations off the bar simply shows [target].
 */
@Composable
fun animatedBarFraction(target: Float, delayMillis: Int = 0): Float {
    val animations = LocalFeedback.current.animations
    val safe = if (target.isNaN()) 0f else target.coerceIn(0f, 1f)
    var appeared by rememberSaveable { mutableStateOf(false) }
    val fraction = remember { Animatable(if (appeared || !animations) safe else 0f) }
    LaunchedEffect(safe, animations) {
        if (!animations) {
            fraction.snapTo(safe)
        } else {
            if (!appeared && delayMillis > 0) delay(delayMillis.toLong())
            fraction.animateTo(safe, tween(BAR_ANIM_MS, easing = FastOutSlowInEasing))
        }
        // Only a bar that has shown a real value has "appeared"; an empty one (still loading) may grow later.
        if (safe > 0f) appeared = true
    }
    return fraction.value
}

// ------------------------------------------------------------------------------------- numbers

/** Whole shekels the way a hero number prints them: the magnitude rounded, the sign kept. */
fun wholeShekels(shekels: Double): Long = if (shekels < 0) -abs(shekels).roundToLong() else shekels.roundToLong()

/**
 * The value a hero number shows right now. Null while [target] is null (still loading). The first
 * value is shown at once — no count-up from zero on a cold start; when the value later really
 * changes (an expense or a shift was added, also on another screen) it rolls from the old value
 * to the new one over [Motion.HERO_ROLL_MS]. The last shown value is saved with the screen, so
 * coming back from the form that changed it still rolls.
 */
@Composable
fun rolledValue(target: Long?): Long? {
    val animations = LocalFeedback.current.animations
    var last by rememberSaveable { mutableStateOf<Long?>(null) }
    val shown = remember { Animatable((last ?: target ?: 0L).toFloat()) }
    LaunchedEffect(target, animations) {
        val to: Long = target ?: return@LaunchedEffect
        val from: Long? = last
        if (from == null || from == to || !animations) {
            shown.snapTo(to.toFloat())
        } else {
            shown.animateTo(to.toFloat(), tween(Motion.HERO_ROLL_MS, easing = FastOutSlowInEasing))
        }
        last = to
    }
    if (target == null) return null
    val settled: Long? = last
    if (settled == null || (settled == target && !shown.isRunning)) return target
    return shown.value.roundToLong()
}

/**
 * A hero number (68sp): a dim "—" while [value] is null (the screen is still loading) instead of
 * a misleading 0, then the magnitude of [value] in whole shekels, rolling when it changes
 * ([rolledValue]). The sign is the caller's to express, with [color] and the label above.
 */
@Composable
fun HeroNumber(value: Long?, color: Color, modifier: Modifier = Modifier) {
    val rolled: Long? = rolledValue(value)
    Text(
        if (rolled == null) "—" else Fmt.money(abs(rolled).toDouble()),
        style = DriftType.Hero,
        color = if (rolled == null) DriftColors.TextDim else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// ------------------------------------------------------------------------------------ new rows

/**
 * When the list named [list] was looked at before this visit (null until read; 0 = never), marking
 * it as looked at now. Rows created after the returned time are new to the user ([isNewSince]).
 */
@Composable
fun rememberListSeenBefore(list: String): Long? {
    val graph = LocalAppGraph.current
    val seen by produceState<Long?>(initialValue = null, list) {
        value = graph.settings.swapListSeen(list, System.currentTimeMillis())
    }
    return seen
}

/** A row is new when it was created after the list was last looked at; on the very first look nothing is. */
fun isNewSince(createdAtMillis: Long, seenBeforeMillis: Long?): Boolean =
    seenBeforeMillis != null && seenBeforeMillis > 0L && createdAtMillis > seenBeforeMillis

/**
 * The landing of a new row: a lime [Motion.LANDING_ALPHA] tint behind it that fades out over
 * [Motion.LANDING_MS], once — "played" is saved with the row, so scrolling does not repeat it.
 * Returns an empty modifier for a row that is not new, or with animations off.
 */
@Composable
fun landingTint(isNew: Boolean): Modifier {
    val animations = LocalFeedback.current.animations
    var played by rememberSaveable { mutableStateOf(false) }
    if (!isNew || played || !animations) return Modifier
    val alpha = remember { Animatable(Motion.LANDING_ALPHA) }
    LaunchedEffect(Unit) {
        alpha.animateTo(0f, tween(Motion.LANDING_MS))
        played = true
    }
    return Modifier.drawBehind {
        drawRoundRect(DriftColors.Lime.copy(alpha = alpha.value), cornerRadius = CornerRadius(6.dp.toPx()))
    }
}

/**
 * The motion of one row of a lazy list of expenses or shifts: it moves, fades in and fades out
 * with the list when rows are added or removed (the list must give its rows keys), and a new row
 * lands with the tint ([landingTint]). With animations off the row just appears.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.rowMotion(isNew: Boolean): Modifier {
    val base: Modifier = if (LocalFeedback.current.animations) Modifier.animateItem() else Modifier
    return base.then(landingTint(isNew))
}

// ------------------------------------------------------------------------------------ overlays

/**
 * The scrim behind the plus menu and the quick-actions sheet, with [content] anchored to the
 * bottom. It appears with a [Motion.OVERLAY_MS] fade while the content rises
 * [Motion.OVERLAY_RISE], and fades out the same way. A tap on the scrim calls [onDismiss].
 */
@Composable
fun ScrimOverlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val animations = LocalFeedback.current.animations
    AnimatedVisibility(
        visible = visible,
        enter = if (animations) fadeIn(tween(Motion.OVERLAY_MS)) else EnterTransition.None,
        exit = if (animations) fadeOut(tween(Motion.OVERLAY_MS)) else ExitTransition.None,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 1 = a full rise below its place, 0 = in place.
            val rise = remember { Animatable(if (animations) 1f else 0f) }
            LaunchedEffect(Unit) { rise.animateTo(0f, tween(Motion.OVERLAY_MS)) }
            Box(Modifier.graphicsLayer { translationY = rise.value * Motion.OVERLAY_RISE.toPx() }) {
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- ring

/**
 * Progress 0..1 of the single ring of a "goal reached" / "week in surplus" moment; 1 = nothing to
 * draw. [pulseAtMillis] is the [SystemClock.uptimeMillis] at which the moment was triggered
 * (0 = never): the ring plays only for a fresh trigger, so a bar that scrolls back into view does
 * not play it again. With animations off there is no ring.
 */
@Composable
fun pulseProgress(pulseAtMillis: Long): Float {
    val animations = LocalFeedback.current.animations
    val progress = remember { Animatable(1f) }
    LaunchedEffect(pulseAtMillis) {
        if (animations && pulseAtMillis > 0L && SystemClock.uptimeMillis() - pulseAtMillis < PULSE_FRESH_MS) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(Motion.RING_MS, easing = LinearOutSlowInEasing))
        }
    }
    return progress.value
}

private const val PULSE_FRESH_MS = 1_000L

/** Draws the ring: a 1dp lime circle, radius 5→14dp, alpha 0.6→0. No blur, no fill. */
fun DrawScope.drawPulseRing(progress: Float, center: Offset) {
    if (progress >= 1f) return
    val radius = Motion.RING_FROM.toPx() + (Motion.RING_TO.toPx() - Motion.RING_FROM.toPx()) * progress
    drawCircle(
        color = DriftColors.Lime.copy(alpha = Motion.RING_ALPHA * (1f - progress)),
        radius = radius,
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )
}

// --------------------------------------------------------------------------- haptics and sound

/**
 * The app's moments of feedback. Haptics go through the view (no permission) and follow the
 * "רטט" switch; sounds go through the app's [SoundPlayer], which follows the "צלילים" switch and
 * the phone's ringer mode by itself.
 */
class FeedbackActions internal constructor(
    private val view: View,
    private val hapticsOn: Boolean,
    private val sounds: SoundPlayer,
) {
    /** A shift, an expense or a deposit was saved. */
    fun saved() {
        haptic(confirmConstant())
        sounds.play(DriftSound.SAVED)
    }

    /** Something was deleted. */
    fun deleted() {
        haptic(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }

    /** One step of a − / + stepper, or a calendar day toggled. */
    fun tick() {
        haptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** The monthly goal was reached (played once per month). */
    fun goalReached() {
        haptic(confirmConstant())
        sounds.play(DriftSound.GOAL)
    }

    private fun confirmConstant(): Int =
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY

    private fun haptic(constant: Int) {
        if (hapticsOn) view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberFeedbackActions(): FeedbackActions {
    val view = LocalView.current
    val hapticsOn = LocalFeedback.current.haptics
    val graph = LocalAppGraph.current
    return remember(view, hapticsOn, graph) { FeedbackActions(view, hapticsOn, graph.sounds) }
}
