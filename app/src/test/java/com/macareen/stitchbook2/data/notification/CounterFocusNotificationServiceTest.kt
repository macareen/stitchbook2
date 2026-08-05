package com.macareen.stitchbook2.data.notification

import com.macareen.stitchbook2.domain.model.Counter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CounterFocusNotificationServiceTest {

    @Test
    fun `no counters yields null`() {
        assertNull(counterSummaryText(emptyList()))
    }

    @Test
    fun `one counter without a goal shows just its value`() {
        val text = counterSummaryText(listOf(counter(name = "Row", currentValue = 12, goal = null)))
        assertEquals("Row: 12", text)
    }

    @Test
    fun `one counter with a goal shows value over goal`() {
        val text = counterSummaryText(listOf(counter(name = "Sleeve repeats", currentValue = 3, goal = 8)))
        assertEquals("Sleeve repeats: 3/8", text)
    }

    @Test
    fun `multiple counters are joined in order`() {
        val text = counterSummaryText(
            listOf(
                counter(name = "Row", currentValue = 12, goal = null),
                counter(name = "Sleeve repeats", currentValue = 3, goal = 8)
            )
        )
        assertEquals("Row: 12  ·  Sleeve repeats: 3/8", text)
    }

    private fun counter(name: String, currentValue: Int, goal: Int?) = Counter(
        id = name,
        projectId = "project-1",
        name = name,
        unitLabel = "rows",
        currentValue = currentValue,
        goal = goal,
        createdAt = 0,
        updatedAt = 0,
        linkedCounterId = null,
        linkIncrementInterval = null,
        linkIncrementAmount = null
    )
}
