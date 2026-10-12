package com.macareen.stitchbook2.feature.calculators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.CraftCalculators
import com.macareen.stitchbook2.domain.model.CraftCalculators.YarnUnit
import com.macareen.stitchbook2.domain.model.ShapingPlan
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing

/** Three small offline helpers: even shaping, yarn amounts, and gauge. Nothing is stored. */
@Composable
fun CalculatorsScreen(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StitchbookSpacing.large, vertical = StitchbookSpacing.medium)
    ) {
        Text(text = stringResource(R.string.calculators_title), style = MaterialTheme.typography.headlineLarge)
        ShapingCard()
        YarnCard()
        GaugeCard()
    }
}

@Composable
private fun CalculatorCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
            modifier = Modifier.padding(StitchbookSpacing.medium)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = 64.dp)
    )
}

private fun String.number(): Double? = replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
private fun String.count(): Int? = number()?.let(CraftCalculators::wholeCount)

@Composable
private fun Answer(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ShapingCard() {
    var current by rememberSaveable { mutableStateOf("") }
    var desired by rememberSaveable { mutableStateOf("") }
    var centered by rememberSaveable { mutableStateOf(false) }
    CalculatorCard(stringResource(R.string.calculators_shaping_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            NumberField(stringResource(R.string.calculators_shaping_current), current, { current = it }, Modifier.weight(1f))
            NumberField(stringResource(R.string.calculators_shaping_desired), desired, { desired = it }, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = centered, onCheckedChange = { centered = it })
            QuietText(text = stringResource(R.string.calculators_shaping_centered))
        }
        val plan = current.count()?.let { from ->
            desired.count()?.let { to -> CraftCalculators.distribute(from, to, centered) }
        }
        if (plan != null) ShapingAnswer(plan)
        else if (current.isNotBlank() && desired.isNotBlank()) QuietText(text = stringResource(R.string.calculators_shaping_impossible))
    }
}

@Composable
private fun ShapingAnswer(plan: ShapingPlan) {
    val shape = stringResource(if (plan.isIncrease) R.string.calculators_increase else R.string.calculators_decrease)
    Answer(stringResource(R.string.calculators_shaping_first, plan.before, shape))
    plan.runs.forEach { run -> Answer(stringResource(R.string.calculators_shaping_run, run.plain, shape, run.times)) }
    if (plan.after > 0) Answer(stringResource(R.string.calculators_shaping_after, plan.after))
    QuietText(text = stringResource(R.string.calculators_shaping_total, plan.count, shape))
}

@Composable
private fun UnitChips(selected: YarnUnit, onSelect: (YarnUnit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        YarnUnit.entries.forEach { unit ->
            FilterChip(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                label = {
                    Text(
                        stringResource(
                            when (unit) {
                                YarnUnit.METRES -> R.string.calculators_unit_metres
                                YarnUnit.YARDS -> R.string.calculators_unit_yards
                                YarnUnit.GRAMS -> R.string.calculators_unit_grams
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun YarnCard() {
    var skeins by rememberSaveable { mutableStateOf("") }
    var patternAmount by rememberSaveable { mutableStateOf("") }
    var patternUnit by rememberSaveable { mutableStateOf(YarnUnit.METRES) }
    var yourAmount by rememberSaveable { mutableStateOf("") }
    var yourUnit by rememberSaveable { mutableStateOf(YarnUnit.YARDS) }
    CalculatorCard(stringResource(R.string.calculators_yarn_title)) {
        QuietText(text = stringResource(R.string.calculators_yarn_pattern))
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            NumberField(stringResource(R.string.calculators_yarn_skeins), skeins, { skeins = it }, Modifier.weight(1f))
            NumberField(stringResource(R.string.calculators_yarn_each), patternAmount, { patternAmount = it }, Modifier.weight(1f))
        }
        UnitChips(patternUnit) { patternUnit = it }
        QuietText(text = stringResource(R.string.calculators_yarn_yours))
        NumberField(stringResource(R.string.calculators_yarn_your_each), yourAmount, { yourAmount = it }, Modifier.fillMaxWidth())
        UnitChips(yourUnit) { yourUnit = it }
        val patternCount = skeins.number()
        val perSkein = patternAmount.number()
        val yours = yourAmount.number()
        val total = if (patternCount != null && perSkein != null) CraftCalculators.yarnTotal(patternCount, perSkein) else null
        if (total != null) {
            val converted = CraftCalculators.convert(total, patternUnit, yourUnit)?.takeIf { it.isFinite() }
            val shownUnit = if (converted != null) yourUnit else patternUnit
            val unit = when (shownUnit) {
                YarnUnit.METRES -> "m"
                YarnUnit.YARDS -> "yd"
                YarnUnit.GRAMS -> "g"
            }
            Answer(stringResource(R.string.calculators_yarn_total, converted ?: total, unit))
            if (converted == null) QuietText(text = stringResource(R.string.calculators_yarn_grams_note))
            if (yours != null) {
                val needed = CraftCalculators.skeinsNeeded(requireNotNull(patternCount), requireNotNull(perSkein), patternUnit, yours, yourUnit)
                if (needed != null) Answer(stringResource(R.string.calculators_yarn_answer, needed))
            } else QuietText(text = stringResource(R.string.calculators_yarn_missing_yours))
        } else {
            val missing = listOfNotNull(
                if (patternCount == null) stringResource(R.string.calculators_yarn_skeins) else null,
                if (perSkein == null) stringResource(R.string.calculators_yarn_each) else null
            )
            QuietText(text = if (missing.isEmpty()) stringResource(R.string.calculators_values_too_large)
                else stringResource(R.string.calculators_missing, missing.joinToString(", ")))
        }
    }
}

@Composable
private fun GaugeCard() {
    var patternSts by rememberSaveable { mutableStateOf("") }
    var patternRows by rememberSaveable { mutableStateOf("") }
    var yourSts by rememberSaveable { mutableStateOf("") }
    var yourRows by rememberSaveable { mutableStateOf("") }
    var countSts by rememberSaveable { mutableStateOf("") }
    var countRows by rememberSaveable { mutableStateOf("") }
    CalculatorCard(stringResource(R.string.calculators_gauge_title)) {
        QuietText(text = stringResource(R.string.calculators_gauge_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            NumberField(stringResource(R.string.calculators_gauge_pattern_sts), patternSts, { patternSts = it }, Modifier.weight(1f))
            NumberField(stringResource(R.string.calculators_gauge_pattern_rows), patternRows, { patternRows = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            NumberField(stringResource(R.string.calculators_gauge_your_sts), yourSts, { yourSts = it }, Modifier.weight(1f))
            NumberField(stringResource(R.string.calculators_gauge_your_rows), yourRows, { yourRows = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            NumberField(stringResource(R.string.calculators_gauge_count_sts), countSts, { countSts = it }, Modifier.weight(1f))
            NumberField(stringResource(R.string.calculators_gauge_count_rows), countRows, { countRows = it }, Modifier.weight(1f))
        }
        GaugeAnswer(patternSts, yourSts, countSts, R.string.calculators_gauge_pattern_sts,
            R.string.calculators_gauge_your_sts, R.string.calculators_gauge_count_sts,
            R.string.calculators_gauge_sts_factor, R.string.calculators_gauge_sts_answer)
        GaugeAnswer(patternRows, yourRows, countRows, R.string.calculators_gauge_pattern_rows,
            R.string.calculators_gauge_your_rows, R.string.calculators_gauge_count_rows,
            R.string.calculators_gauge_rows_factor, R.string.calculators_gauge_rows_answer)
    }
}

@Composable
private fun GaugeAnswer(pattern: String, yours: String, count: String, patternLabel: Int, yourLabel: Int,
    countLabel: Int, factorText: Int, answerText: Int) {
    val patternGauge = pattern.number()
    val yourGauge = yours.number()
    val factor = if (patternGauge != null && yourGauge != null) CraftCalculators.gaugeFactor(patternGauge, yourGauge) else null
    if (factor == null) {
        val missing = listOfNotNull(
            if (patternGauge == null) stringResource(patternLabel) else null,
            if (yourGauge == null) stringResource(yourLabel) else null
        )
        QuietText(text = if (missing.isEmpty()) stringResource(R.string.calculators_values_too_large)
                else stringResource(R.string.calculators_missing, missing.joinToString(", ")))
    } else {
        Answer(stringResource(factorText, factor))
        val converted = count.count()?.let { CraftCalculators.adaptCount(it, requireNotNull(patternGauge), requireNotNull(yourGauge)) }
        if (converted != null) Answer(stringResource(answerText, converted))
        else QuietText(text = stringResource(R.string.calculators_missing_count, stringResource(countLabel)))
    }
}
