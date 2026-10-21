package com.macareen.stitchbook2.feature.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.guide.GlossaryEntry
import com.macareen.stitchbook2.domain.guide.GuideReference

@Composable
internal fun GlossaryText(text: String, style: TextStyle, glossary: List<GlossaryEntry>) {
    var selected by remember { mutableStateOf<GlossaryEntry?>(null) }
    val color = MaterialTheme.colorScheme.primary
    val annotated = remember(text, glossary, color) {
        val pattern = glossary.takeIf { it.isNotEmpty() }?.joinToString("|", "(?i)(?<![\\p{L}\\d])(?:", ")(?:\\d+)?(?![\\p{L}\\d])") { it.term.split("(s)").joinToString("(?:s)?") { part -> Regex.escape(part) } }
        buildAnnotatedString {
            var end = 0
            pattern?.let(::Regex)?.findAll(text)?.forEach { match ->
                append(text.substring(end, match.range.first))
                val entry = glossary.sortedByDescending { it.term.length }.firstOrNull {
                    Regex(it.term.split("(s)").joinToString("(?:s)?") { part -> Regex.escape(part) } + "(?:\\d+)?", RegexOption.IGNORE_CASE).matches(match.value)
                }
                if (entry == null) append(match.value)
                else withLink(LinkAnnotation.Clickable(entry.term,
                    styles = TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { selected = entry })) { append(match.value) }
                end = match.range.last + 1
            }
            append(text.substring(end))
        }
    }
    Text(annotated, style = style)
    selected?.let { entry -> AlertDialog(onDismissRequest = { selected = null },
        title = { Text(entry.term) }, text = { Text(entry.definition, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.reference_close)) } }) }
}

@Composable
fun GuideReferenceDialog(notes: String?, onClose: () -> Unit) {
    val glossary = remember(notes) { GuideReference.glossary(notes) }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.reference_title)) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (notes.isNullOrBlank()) Text(stringResource(R.string.reference_empty))
            else {
                if (glossary.isNotEmpty()) {
                    Text(stringResource(R.string.reference_glossary), style = MaterialTheme.typography.titleMedium)
                    glossary.forEach { Text(it.term, style = MaterialTheme.typography.labelLarge); Text(it.definition) }
                    HorizontalDivider()
                }
                val remaining = if (glossary.isEmpty()) notes else notes.replace(Regex("(?is)(?:^|\\n)##\\s*(?:glossary|abbreviations?)\\s*\\n.*?(?=\\n##\\s|$)"), "").trim()
                if (remaining.isNotBlank()) {
                    Text(stringResource(R.string.reference_all), style = MaterialTheme.typography.titleMedium)
                    val sections = com.macareen.stitchbook2.domain.library.PatternSuggestions.sections(remaining)
                    if (sections.isEmpty()) Text(remaining)
                    else {
                        remaining.substringBefore("## ").trim().takeIf(String::isNotBlank)?.let { Text(it) }
                        sections.forEach { (heading, body) ->
                            Text(heading, style = MaterialTheme.typography.titleSmall)
                            Text(body)
                        }
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.reference_close)) } })
}
