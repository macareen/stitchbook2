package com.macareen.stitchbook2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StitchbookNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun topLevelNavigation_startsAtHome_andDoesNotDuplicateCurrentDestination() {
        val home = composeTestRule.activity.getString(R.string.destination_home)
        val projects = composeTestRule.activity.getString(R.string.destination_projects)

        composeTestRule.navigationItem(home).assertIsSelected()
        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.home_hero_title)
            )
            .assertIsDisplayed()

        composeTestRule.navigationItem(projects).performClick().assertIsSelected()
        composeTestRule.navigationItem(projects).performClick().assertIsSelected()

        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeTestRule.navigationItem(home).assertIsSelected()
    }

    @Test
    fun topLevelNavigation_reachesEveryDestination() {
        val destinations = listOf(
            R.string.destination_library to R.string.library_header_title,
            R.string.destination_stash to R.string.stash_header_title,
            R.string.destination_settings to R.string.settings_header_title
        )

        val projects = composeTestRule.activity.getString(R.string.destination_projects)
        composeTestRule.navigationItem(projects).performClick().assertIsSelected()

        destinations.forEach { (titleResource, descriptionResource) ->
            val title = composeTestRule.activity.getString(titleResource)
            val description = composeTestRule.activity.getString(descriptionResource)

            composeTestRule.navigationItem(title).performClick().assertIsSelected()
            composeTestRule.onNodeWithText(description).assertIsDisplayed()
        }
    }
}

private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.navigationItem(
    label: String
) = onNode(hasText(label) and hasClickAction())
