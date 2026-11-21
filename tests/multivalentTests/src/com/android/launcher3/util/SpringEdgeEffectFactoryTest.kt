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
    fun rebound_leaves_the_next_inward_swipe_available_to_scroll() {
        val view = RecyclerView(context).apply { layout(0, 0, 1000, 1000) }
        val edge = SpringEdgeEffectFactory.createSpringEdgeEffect(
            view, RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM
        )
        try {
            edge.onPullDistance(0.04f, 0.5f)
            edge.onRelease()

            assertEquals(-40f, view.translationY, 0.001f)
            assertEquals(0f, edge.distance, 0f)
            assertEquals(0f, edge.onPullDistance(-0.01f, 0.5f), 0f)
            assertEquals(-40f, view.translationY, 0.001f)

            assertEquals(0.01f, edge.onPullDistance(0.01f, 0.5f), 0.00001f)
            assertEquals(-50f, view.translationY, 0.001f)
            assertEquals(-0.05f, edge.onPullDistance(-0.10f, 0.5f), 0.00001f)
            assertEquals(0f, edge.distance, 0f)
        } finally {
            edge.finish()
        }
    }

    @Test
    fun rebound_at_top_does_not_claim_the_next_drawer_dismissal() {
        val view = RecyclerView(context).apply { layout(0, 0, 1000, 1000) }
        val edge = SpringEdgeEffectFactory.createSpringEdgeEffect(
            view, RecyclerView.EdgeEffectFactory.DIRECTION_TOP
        )
        try {
            edge.onPullDistance(0.04f, 0.5f)
            edge.onRelease()

            assertEquals(40f, view.translationY, 0.001f)
            assertEquals(0f, edge.distance, 0f)
        } finally {
            edge.finish()
        }
    }

    @Test
    fun releasing_or_finishing_an_edge_does_not_move_the_opposite_edge() {
        val view = RecyclerView(context).apply { layout(0, 0, 1000, 1000) }
        val top = SpringEdgeEffectFactory.createSpringEdgeEffect(
            view, RecyclerView.EdgeEffectFactory.DIRECTION_TOP
        )
        val bottom = SpringEdgeEffectFactory.createSpringEdgeEffect(
            view, RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM
        )
        try {
            bottom.onPullDistance(0.04f, 0.5f)
            top.onRelease()
            assertTrue(top.isFinished)

            top.onPullDistance(0.02f, 0.5f)
            assertEquals(-20f, view.translationY, 0.001f)
            top.finish()
            assertEquals(-40f, view.translationY, 0.001f)
            assertEquals(0.04f, bottom.distance, 0.00001f)
        } finally {
            top.finish()
            bottom.finish()
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
