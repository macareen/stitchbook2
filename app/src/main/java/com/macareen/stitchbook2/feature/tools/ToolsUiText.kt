package com.macareen.stitchbook2.feature.tools

import androidx.annotation.StringRes
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.ToolCategory

@StringRes
fun ToolCategory.labelResource(): Int = when (this) {
    ToolCategory.STRAIGHT_NEEDLES -> R.string.tools_category_straight_needles
    ToolCategory.CIRCULAR_NEEDLES -> R.string.tools_category_circular_needles
    ToolCategory.DPN_SET -> R.string.tools_category_dpn_set
    ToolCategory.INTERCHANGEABLE_TIP -> R.string.tools_category_interchangeable_tip
    ToolCategory.INTERCHANGEABLE_CABLE -> R.string.tools_category_interchangeable_cable
    ToolCategory.CROCHET_HOOK -> R.string.tools_category_crochet_hook
    ToolCategory.TUNISIAN_HOOK -> R.string.tools_category_tunisian_hook
    ToolCategory.LOOM -> R.string.tools_category_loom
    ToolCategory.CABLE_NEEDLE -> R.string.tools_category_cable_needle
    ToolCategory.STITCH_MARKER -> R.string.tools_category_stitch_marker
    ToolCategory.CONNECTOR -> R.string.tools_category_connector
    ToolCategory.END_STOPPER -> R.string.tools_category_end_stopper
    ToolCategory.TIGHTENING_KEY -> R.string.tools_category_tightening_key
    ToolCategory.OTHER_NOTION -> R.string.tools_category_other_notion
}

/** Needle/hook diameter is only meaningful for categories with a bore or gauge size. */
fun ToolCategory.usesSizeFields(): Boolean = this in setOf(
    ToolCategory.STRAIGHT_NEEDLES,
    ToolCategory.CIRCULAR_NEEDLES,
    ToolCategory.DPN_SET,
    ToolCategory.INTERCHANGEABLE_TIP,
    ToolCategory.CROCHET_HOOK,
    ToolCategory.TUNISIAN_HOOK
)

/** A fixed working length is only meaningful for these categories; interchangeable
 * cables track length through the dedicated cable fields instead. */
fun ToolCategory.usesLengthField(): Boolean = this in setOf(
    ToolCategory.STRAIGHT_NEEDLES,
    ToolCategory.CIRCULAR_NEEDLES,
    ToolCategory.DPN_SET,
    ToolCategory.INTERCHANGEABLE_TIP,
    ToolCategory.TUNISIAN_HOOK,
    ToolCategory.LOOM
)

/** Stated/definition/assembled cable length only apply to interchangeable cables. */
fun ToolCategory.usesCableFields(): Boolean = this == ToolCategory.INTERCHANGEABLE_CABLE

/** Connector family/compatibility notes only apply to interchangeable-system parts. */
fun ToolCategory.usesConnectorFields(): Boolean = this in setOf(
    ToolCategory.INTERCHANGEABLE_TIP,
    ToolCategory.INTERCHANGEABLE_CABLE,
    ToolCategory.CONNECTOR
)
