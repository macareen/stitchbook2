package com.macareen.stitchbook2.feature.projects

import androidx.annotation.StringRes
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType

@StringRes
fun Craft.labelResource(): Int = when (this) {
    Craft.KNITTING -> R.string.craft_knitting
    Craft.CROCHET -> R.string.craft_crochet
    Craft.TUNISIAN_CROCHET -> R.string.craft_tunisian_crochet
    Craft.LOOM_KNITTING -> R.string.craft_loom_knitting
    Craft.OTHER -> R.string.value_other
}

@StringRes
fun ProjectStatus.labelResource(): Int = when (this) {
    ProjectStatus.PLANNED -> R.string.status_planned
    ProjectStatus.ACTIVE -> R.string.status_active
    ProjectStatus.PAUSED -> R.string.status_paused
    ProjectStatus.COMPLETED -> R.string.status_completed
    ProjectStatus.ABANDONED -> R.string.status_abandoned
}

@StringRes
fun ProjectType.labelResource(): Int = when (this) {
    ProjectType.SWEATER -> R.string.project_type_sweater
    ProjectType.CARDIGAN -> R.string.project_type_cardigan
    ProjectType.TOP -> R.string.project_type_top
    ProjectType.SOCKS -> R.string.project_type_socks
    ProjectType.HAT -> R.string.project_type_hat
    ProjectType.SCARF -> R.string.project_type_scarf
    ProjectType.SHAWL -> R.string.project_type_shawl
    ProjectType.BLANKET -> R.string.project_type_blanket
    ProjectType.BAG -> R.string.project_type_bag
    ProjectType.AMIGURUMI -> R.string.project_type_amigurumi
    ProjectType.HOMEWARE -> R.string.project_type_homeware
    ProjectType.ACCESSORY -> R.string.project_type_accessory
    ProjectType.OTHER -> R.string.value_other
}
