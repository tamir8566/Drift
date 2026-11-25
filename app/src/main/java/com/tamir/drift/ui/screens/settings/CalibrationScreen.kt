package com.tamir.drift.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.ParsePatternEntity
import com.tamir.drift.domain.money.Currencies
import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.UserPattern
import com.tamir.drift.domain.parser.AmountParser
import com.tamir.drift.domain.parser.MarkLocator
import com.tamir.drift.domain.parser.PatternBuilder
import com.tamir.drift.domain.parser.TextNormalizer
import com.tamir.drift.domain.parser.TransactionTextParser
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.announceTestCapture
import com.tamir.drift.ui.components.Chip
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.PrimaryButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SecondaryButton
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.deletePatternWithUndo
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch

/** What the capture processor puts between the texts of two sources merged into one expense. */
private const val MERGED_TEXT_SEPARATOR = "\n\n---\n\n"

/**
 * PRD "כיול": paste a real message, see how it parses, select the amount / merchant in the
 * text and save a pattern that the parser tries first from now on.
 * With [prefillTransactionId] ("למד תבנית מההודעה הזו" in an expense's details) the screen opens
 * with that expense's raw captured text and its source already filled in.
 */
@Composable
fun CalibrationScreen(onBack: () -> Unit, prefillTransactionId: Long? = null) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val patterns by graph.db.parsePatterns().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val example = stringResource(R.string.calib_example)

    var source by remember { mutableStateOf(TransactionSource.SMS) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var amountMark by remember { mutableStateOf<String?>(null) }
    var merchantMark by remember { mutableStateOf<String?>(null) }
    // Where each mark begins in the field's text (-1 = unknown): the same characters may appear more
    // than once ("12" in the date and as the amount), and the pattern must be built on the marked ones.
    var amountMarkStart by remember { mutableIntStateOf(-1) }
    var merchantMarkStart by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(prefillTransactionId) {
        val prefillId = prefillTransactionId ?: return@LaunchedEffect
        val tx = graph.transactions.get(prefillId) ?: return@LaunchedEffect
        val raw = tx.rawText
        if (!raw.isNullOrBlank() && field.text.isBlank()) {
            // An expense merged from two sources keeps both texts; the pattern is learned from the first.
            field = TextFieldValue(raw.substringBefore(MERGED_TEXT_SEPARATOR).trim())
            if (tx.source == TransactionSource.GPAY.name) {
                source = TransactionSource.GPAY
            } else if (tx.source == TransactionSource.SMS.name) {
                source = TransactionSource.SMS
            }
        }
    }

    val userPatterns = patterns.map { UserPattern(it.id, it.amountRegex, it.source?.let { s -> runCatching { TransactionSource.valueOf(s) }.getOrNull() }) }
    val parsed = remember(field.text, source, patterns) {
        if (field.text.isBlank()) null else TransactionTextParser(userPatterns).parse(field.text, source)
    }

    /** The marked text (trimmed) and where it begins in the field. */
    fun selectedText(): Pair<String, Int>? {
        val sel = field.selection
        if (sel.collapsed) return null
        val raw = field.text.substring(sel.min, sel.max)
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed to (sel.min + raw.indexOf(trimmed))
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(R.string.calib_title), onBack = onBack, backDescription = stringResource(R.string.nav_back))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.calib_source), style = DriftType.Label12, color = DriftColors.TextSecondary)
            Chip(stringResource(R.string.source_sms), source == TransactionSource.SMS, { source = TransactionSource.SMS })
            Chip(stringResource(R.string.source_gpay), source == TransactionSource.GPAY, { source = TransactionSource.GPAY })
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.calib_use_example), style = DriftType.Link12, color = DriftColors.Lime,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { field = TextFieldValue(example); amountMark = null; merchantMark = null }.padding(6.dp),
            )
        }

        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            placeholder = { Text(stringResource(R.string.calib_paste_hint), style = DriftType.Body, color = DriftColors.TextDim) },
            textStyle = DriftType.Body.copy(color = DriftColors.Text),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DriftColors.PlusBorder, unfocusedBorderColor = DriftColors.GlassBorder,
                cursorColor = DriftColors.Lime, focusedTextColor = DriftColors.Text, unfocusedTextColor = DriftColors.Text,
                focusedContainerColor = DriftColors.GlassFill, unfocusedContainerColor = DriftColors.GlassFill,
            ),
        )

        // Parse result
        GlassCard(Modifier.fillMaxWidth(), radius = 20.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.calib_parse_result), style = DriftType.Label12, color = DriftColors.TextSecondary)
                val none = stringResource(R.string.calib_parsed_none)
                Text(stringResource(R.string.calib_parsed_amount, parsed?.let { p -> p.amount?.let { a -> if (p.isForeign) Fmt.foreign(Currencies.toMinor(a), p.currency) else Fmt.money(a) } } ?: none), style = DriftType.Body, color = if (parsed?.amount != null) DriftColors.Lime else DriftColors.Orange)
                Text(stringResource(R.string.calib_parsed_merchant, parsed?.merchant ?: none), style = DriftType.Body, color = if (parsed?.merchant != null) DriftColors.Text else DriftColors.Orange, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (parsed != null) Text(stringResource(R.string.calib_parser, parserLabel(parsed.parserName)), style = DriftType.Label11, color = DriftColors.TextDim)
            }
        }

        // Marking
        Text(stringResource(R.string.calib_mark_hint), style = DriftType.Label11, color = DriftColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.calib_mark_amount), modifier = Modifier.weight(1f), onClick = { selectedText()?.let { amountMark = it.first; amountMarkStart = it.second } })
            SecondaryButton(stringResource(R.string.calib_mark_merchant), modifier = Modifier.weight(1f), onClick = { selectedText()?.let { merchantMark = it.first; merchantMarkStart = it.second } })
        }
        if (amountMark != null) Text(stringResource(R.string.calib_marked_amount, amountMark!!), style = DriftType.Label12, color = DriftColors.Lime)
        if (merchantMark != null) Text(stringResource(R.string.calib_marked_merchant, merchantMark!!), style = DriftType.Label12, color = DriftColors.Lime, maxLines = 1, overflow = TextOverflow.Ellipsis)

        PrimaryButton(stringResource(R.string.calib_save_pattern), enabled = amountMark != null && field.text.isNotBlank(), onClick = {
            val clean = TextNormalizer.normalize(field.text)
            val a = amountMark!!
            val amountDigits = Regex("""\d[\d.,]*""").find(a)?.value
            if (amountDigits == null || AmountParser.parseNumber(amountDigits) <= 0.0) {
                message = "amount"
            } else {
                // The digits may sit inside a longer mark ("216.79 שח"); the pattern is anchored on
                // the marked occurrence of them, not on the first place the same digits appear.
                val digitsAt = if (amountMarkStart >= 0) amountMarkStart + a.indexOf(amountDigits) else -1
                val aStart = MarkLocator.locate(field.text, clean, amountDigits, digitsAt)
                val aRange = aStart until aStart + amountDigits.length
                val mRange = merchantMark?.let { m -> val i = MarkLocator.locate(field.text, clean, m, merchantMarkStart); if (i >= 0) i until i + m.length else null }
                if (aStart < 0) {
                    message = "amount"
                } else {
                    val regex = runCatching { PatternBuilder.build(clean, aRange, mRange) }.getOrNull()
                    if (regex == null) message = "amount" else {
                        scope.launch {
                            graph.db.parsePatterns().insert(ParsePatternEntity(source = source.name, amountRegex = regex, sample = clean))
                            message = "saved"
                        }
                    }
                }
            }
        })
        when (message) {
            "saved" -> Text(stringResource(R.string.calib_saved), style = DriftType.Label12, color = DriftColors.Teal)
            "amount" -> Text(stringResource(R.string.calib_amount_not_number), style = DriftType.Label12, color = DriftColors.Orange)
        }

        if (parsed != null && parsed.status != ParseStatus.UNPARSED) {
            SecondaryButton(stringResource(R.string.calib_test_as_tx), color = DriftColors.TextSecondary, onClick = {
                scope.launch {
                    // The real capture path: what it adds is a real expense, so the message offers to take it back.
                    val outcome = graph.captureProcessor.process(source, "calibration", field.text, System.currentTimeMillis())
                    graph.announceTestCapture(outcome)
                    onBack()
                }
            })
        }

        // Saved patterns
        SectionHeader(stringResource(R.string.calib_patterns), modifier = Modifier.padding(top = 8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (patterns.isEmpty()) EmptyNote(stringResource(R.string.calib_no_patterns))
                patterns.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(p.sample.replace('\n', ' '), style = DriftType.Label12, color = DriftColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(p.source?.let { if (it == TransactionSource.GPAY.name) stringResource(R.string.source_gpay) else stringResource(R.string.source_sms) } ?: "", style = DriftType.Label11, color = DriftColors.TextDim)
                        }
                        Text(
                            stringResource(R.string.delete), style = DriftType.Link12, color = DriftColors.Orange,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { graph.deletePatternWithUndo(p) }.padding(4.dp),
                        )
                    }
                    if (i < patterns.lastIndex) HairlineDivider()
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The parser's internal name ("max-sms", "user-pattern#3", …) as a Hebrew label. */
@Composable
private fun parserLabel(name: String): String {
    val base = name.removeSuffix("(fallback)")
    return when {
        base == "max-sms" -> stringResource(R.string.calib_parser_sms)
        base == "google-pay" -> stringResource(R.string.calib_parser_gpay)
        base.startsWith("user-pattern") -> stringResource(R.string.calib_parser_user)
        base == "generic" -> stringResource(R.string.calib_parser_generic)
        else -> stringResource(R.string.calib_parsed_none)
    }
}
