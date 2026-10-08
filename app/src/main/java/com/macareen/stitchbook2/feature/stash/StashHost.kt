package com.macareen.stitchbook2.feature.stash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing

enum class StashTab { YARN, TOOLS }

/**
 * One Stash place for everything you own: yarn and materials, or tools.
 * Keeping tools here leaves the bottom bar at five calm destinations.
 */
@Composable
fun StashHost(
    yarn: @Composable () -> Unit,
    tools: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(StashTab.YARN) }
    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StitchbookSpacing.large, vertical = StitchbookSpacing.small)
        ) {
            StashTab.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, StashTab.entries.size)
                ) {
                    Text(
                        text = stringResource(
                            if (entry == StashTab.YARN) R.string.stash_tab_yarn else R.string.stash_tab_tools
                        )
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                StashTab.YARN -> yarn()
                StashTab.TOOLS -> tools()
            }
        }
    }
}
