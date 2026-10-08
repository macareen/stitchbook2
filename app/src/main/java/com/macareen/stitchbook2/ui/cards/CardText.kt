package com.macareen.stitchbook2.ui.cards

import android.content.res.Resources
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.cards.CardContent
import com.macareen.stitchbook2.domain.cards.CardFact
import com.macareen.stitchbook2.domain.cards.CardFactKind
import com.macareen.stitchbook2.domain.cards.CardTemplate
import com.macareen.stitchbook2.domain.cards.CardValue
import com.macareen.stitchbook2.domain.model.parseLocalDateOrNull
import com.macareen.stitchbook2.feature.projects.labelResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Localizes card content; shared by the PNG renderer and the text description so they never disagree. */
class CardText(private val resources: Resources) {

    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun templateLabel(template: CardTemplate): String = resources.getString(
        when (template) {
            CardTemplate.PROGRESS -> R.string.card_template_progress
            CardTemplate.COMPLETED -> R.string.card_template_completed
            CardTemplate.MILESTONE -> R.string.card_template_milestone
            CardTemplate.BEFORE_AFTER -> R.string.card_template_before_after
            CardTemplate.YARN -> R.string.card_template_yarn
            CardTemplate.WEEKLY -> R.string.card_template_weekly
            CardTemplate.ANNUAL -> R.string.card_template_annual
        }
    )

    fun title(content: CardContent): String = content.title ?: when (content.template) {
        CardTemplate.ANNUAL -> resources.getString(R.string.card_title_annual, (content.periodEnd ?: LocalDate.now()).year)
        else -> resources.getString(R.string.card_title_weekly)
    }

    fun subtitle(content: CardContent): String? {
        val start = content.periodStart ?: return null
        val end = content.periodEnd ?: return null
        return resources.getString(R.string.statistics_window, dateFormat.format(start), dateFormat.format(end))
    }

    fun label(fact: CardFact): String {
        (fact.value as? CardValue.AmountValue)?.label?.let { return it }
        return resources.getString(
            when (fact.kind) {
                CardFactKind.CRAFT -> R.string.card_fact_craft
                CardFactKind.TYPE -> R.string.card_fact_type
                CardFactKind.STATUS -> R.string.card_fact_status
                CardFactKind.STARTED -> R.string.project_start_date_label
                CardFactKind.FINISHED -> R.string.project_completed_date_label
                CardFactKind.DURATION_DAYS -> R.string.card_fact_duration
                CardFactKind.TIME_SPENT -> R.string.card_fact_time
                CardFactKind.SESSIONS -> R.string.statistics_sessions
                CardFactKind.ROWS -> R.string.statistics_rows
                CardFactKind.MILESTONE -> R.string.journal_milestone_title
                CardFactKind.MILESTONE_DATE -> R.string.journal_milestone_date
                CardFactKind.YARN_USED -> R.string.statistics_yarn_section
                CardFactKind.YARN_ESTIMATED_YARDS -> R.string.card_fact_estimated_length
                CardFactKind.STREAK -> R.string.card_fact_streak
                CardFactKind.PROJECTS_COMPLETED -> R.string.statistics_projects_completed
                CardFactKind.DESCRIPTION -> R.string.project_description_label
                CardFactKind.NOTES -> R.string.project_notes_label
            }
        )
    }

    fun value(fact: CardFact): String = when (val value = fact.value) {
        is CardValue.Text -> value.text
        is CardValue.CraftValue -> resources.getString(value.craft.labelResource())
        is CardValue.TypeValue -> value.customLabel ?: resources.getString(value.type.labelResource())
        is CardValue.StatusValue -> resources.getString(value.status.labelResource())
        is CardValue.DateValue -> parseLocalDateOrNull(value.isoDate)?.let { dateFormat.format(it) } ?: value.isoDate
        is CardValue.DurationValue -> duration(value.millis)
        is CardValue.CountValue -> String.format(Locale.getDefault(), "%,d", value.count)
        is CardValue.DaysValue -> resources.getQuantityString(R.plurals.card_days, value.days.toInt(), value.days.toInt())
        is CardValue.AmountValue -> resources.getString(R.string.card_amount, trim(value.amount), value.unit)
    }

    /** Whether a fact is a long text block (rendered full-width) rather than a stat tile. */
    fun isLongText(fact: CardFact): Boolean =
        fact.kind == CardFactKind.DESCRIPTION || fact.kind == CardFactKind.NOTES

    /** The accessible text alternative offered next to an exported card. */
    fun description(content: CardContent): String = buildString {
        append(templateLabel(content.template)).append(": ").append(title(content)).append('.')
        subtitle(content)?.let { append(' ').append(it).append('.') }
        content.facts.forEach { append(' ').append(label(it)).append(": ").append(value(it)).append('.') }
        if (content.photoUris.isNotEmpty()) {
            append(' ').append(resources.getQuantityString(R.plurals.card_photo_count, content.photoUris.size, content.photoUris.size))
        }
    }

    private fun duration(millis: Long): String {
        val minutes = millis / 60_000L
        return if (minutes >= 60) {
            resources.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
        } else {
            resources.getString(R.string.duration_minutes, minutes)
        }
    }

    private fun trim(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString()
        else String.format(Locale.ROOT, "%.2f", amount).trimEnd('0').trimEnd('.')
}
