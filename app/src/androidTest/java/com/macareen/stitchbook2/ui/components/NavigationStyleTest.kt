package com.macareen.stitchbook2.ui.components

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.preferences.BottomNavigationStyle
import com.macareen.stitchbook2.data.preferences.SharedPreferencesUserPreferencesRepository
import com.macareen.stitchbook2.navigation.TopLevelDestination
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationStyleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun wordsWorkInLightMode() = check(false, 1f, BottomNavigationStyle.WORDS)
    @Test fun settingsCogKeepsWordsOnOneRowAtPhoneScale() = check(false, 1.1f, BottomNavigationStyle.WORDS)
    @Test fun wordsWorkInDarkMode() = check(true, 1f, BottomNavigationStyle.WORDS)
    @Test fun wordsWrapAtLargeTextSize() = check(true, 1.8f, BottomNavigationStyle.WORDS)
    @Test fun iconsKeepAccessibleNames() = check(false, 1f, BottomNavigationStyle.ICONS)
    @Test fun preferenceSurvivesRepositoryRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SharedPreferencesUserPreferencesRepository(context)
        val old = repository.preferences.value.navigationStyle
        try {
            repository.setNavigationStyle(BottomNavigationStyle.ICONS)
            assertEquals(BottomNavigationStyle.ICONS, SharedPreferencesUserPreferencesRepository(context).preferences.value.navigationStyle)
            repository.setNavigationStyle(BottomNavigationStyle.WORDS)
            assertEquals(BottomNavigationStyle.WORDS, SharedPreferencesUserPreferencesRepository(context).preferences.value.navigationStyle)
        } finally { repository.setNavigationStyle(old) }
    }
    private fun check(dark: Boolean, scale: Float, style: BottomNavigationStyle) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val items = TopLevelDestination.entries.map { BottomBarItem(it.route, context.getString(it.title), it.icon, preferIcon = it == TopLevelDestination.Settings) }
        var selected: String? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                        Box { StitchbookBottomBar(items, "home", { selected = it.key }, Modifier.align(Alignment.BottomCenter), style) }
                    }
                }
            }
        }
        items.forEach {
            compose.onNodeWithContentDescription(it.label).assertIsDisplayed().assertWidthIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(it.key, selected) }
            if (style == BottomNavigationStyle.WORDS && !it.preferIcon) compose.onNodeWithText(it.label).assertIsDisplayed()
        }
        if (scale <= 1.1f) {
            val tops = items.map { compose.onNodeWithContentDescription(it.label).fetchSemanticsNode().boundsInRoot.top }
            compose.runOnIdle { assertEquals("Settings must not create a second navigation row", 1, tops.distinct().size) }
        }
        compose.onRoot().captureToImage()
        if (InstrumentationRegistry.getArguments().getString("captureNavigation") == "true") {
            val mode = if (style == BottomNavigationStyle.ICONS) "icons" else if (scale > 1.1f) "large" else if (dark) "dark" else if (scale > 1f) "phone" else "light"
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/stitchbook-navigation-$mode.png")).use { it.readBytes() }
        }
    }
}
