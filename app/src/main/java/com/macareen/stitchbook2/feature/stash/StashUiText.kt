package com.macareen.stitchbook2.feature.stash

import androidx.annotation.StringRes
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.StashCategory

@StringRes
fun StashCategory.labelResource(): Int = when (this) {
    StashCategory.YARN -> R.string.stash_category_yarn
    StashCategory.NEEDLES_HOOKS -> R.string.stash_category_needles_hooks
    StashCategory.NOTIONS -> R.string.stash_category_notions
    StashCategory.MATERIALS -> R.string.stash_category_materials
}
