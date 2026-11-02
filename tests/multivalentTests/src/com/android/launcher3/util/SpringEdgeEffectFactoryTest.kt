/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.util

import androidx.recyclerview.widget.RecyclerView
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@UiThreadTest
@RunWith(LauncherMultivalentJUnit::class)
class SpringEdgeEffectFactoryTest {
    @get:Rule val context = SandboxApplication()

    @Test
    fun interrupted_return_consumes_only_the_new_finger_movement() {
        val view = RecyclerView(context).apply { layout(0, 0, 1000, 1000) }
        val edge = SpringEdgeEffectFactory.createSpringEdgeEffect(
            view, RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM
        )
        try {
            edge.onPullDistance(0.04f, 0.5f)
            edge.onRelease()
            // Simulate the spring moving part-way back before the next touch event.
            view.translationY = -30f
            assertEquals(0.03f, edge.distance, 0.00001f)
            assertEquals(-0.01f, edge.onPullDistance(-0.01f, 0.5f), 0.00001f)
            assertEquals(-20f, view.translationY, 0.001f)
            // Excess reverse motion is returned to RecyclerView to scroll the list.
            assertEquals(-0.02f, edge.onPullDistance(-0.10f, 0.5f), 0.00001f)
            assertEquals(0f, edge.distance, 0f)
        } finally {
            edge.finish()
        }
    }

    @Test
    fun finish_cancels_the_spring_and_resets_visible_and_reported_stretch() {
        val view = RecyclerView(context).apply { layout(0, 0, 1000, 1000) }
        val edge = SpringEdgeEffectFactory.createSpringEdgeEffect(
            view, RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM
        )
        edge.onPullDistance(0.04f, 0.5f)
        edge.onRelease()
        edge.finish()

        assertEquals(0f, view.translationY, 0f)
        assertEquals(0f, edge.distance, 0f)
        assertTrue(edge.isFinished)
        assertEquals(0f, edge.onPullDistance(-0.1f, 0.5f), 0f)
        assertEquals(0.02f, edge.onPullDistance(0.02f, 0.5f), 0.00001f)
        edge.finish()
    }
}
