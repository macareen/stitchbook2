package com.macareen.stitchbook2.feature.home

import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.theme.CozyPastels
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGreetingTest {

    @Test
    fun greetingFollowsTheTimeOfDay() {
        assertEquals(R.string.home_greeting_evening, greetingFor(4))
        assertEquals(R.string.home_greeting_morning, greetingFor(5))
        assertEquals(R.string.home_greeting_morning, greetingFor(11))
        assertEquals(R.string.home_greeting_afternoon, greetingFor(12))
        assertEquals(R.string.home_greeting_afternoon, greetingFor(17))
        assertEquals(R.string.home_greeting_evening, greetingFor(18))
    }

    @Test
    fun theSameItemAlwaysGetsTheSamePastel() {
        assertEquals(CozyPastels.forKey("project-a"), CozyPastels.forKey("project-a"))
    }
}
