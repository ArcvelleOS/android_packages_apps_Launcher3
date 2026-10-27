/* Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.quickstep.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingAppLaunchTest {
    @Test
    fun latestTapWinsAndSupersededCallbacksAreReleased() {
        val queue = PendingAppLaunch()
        val events = mutableListOf<String>()
        queue.set({ events.add("old launch") }, { events.add("old cleanup") })
        queue.set({ events.add("new launch") }, { events.add("new cleanup") })
        assertEquals(listOf("old cleanup"), events)
        queue.execute()
        queue.execute()
        assertEquals(listOf("old cleanup", "new launch"), events)
        assertFalse(queue.hasPendingLaunch())
    }

    @Test
    fun reentrantLaunchDoesNotLoseTheNextRequest() {
        val queue = PendingAppLaunch()
        val events = mutableListOf<String>()
        queue.set({
            events.add("first")
            queue.set({ events.add("second") }, { events.add("unexpected cleanup") })
        }, {})
        queue.execute()
        assertTrue(queue.hasPendingLaunch())
        queue.execute()
        assertEquals(listOf("first", "second"), events)
        assertFalse(queue.hasPendingLaunch())
    }

    @Test
    fun motionCurvesHaveFiniteMonotonicEndpoints() {
        for (curve in listOf(CosAppAnimation.OPEN, CosAppAnimation.REVEAL)) {
            assertEquals(0f, curve.getInterpolation(0f), 0.00001f)
            assertEquals(1f, curve.getInterpolation(1f), 0.00001f)
            var previous = 0f
            for (step in 0..100) {
                val value = curve.getInterpolation(step / 100f)
                assertTrue(value.isFinite() && value >= previous && value <= 1f)
                previous = value
            }
        }
    }
}
