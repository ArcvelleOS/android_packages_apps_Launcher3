/* Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.deviceprofile

import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.DeviceProfile
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.widget.util.WidgetSizes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WorkspaceWidgetPaddingTest {
    @Test
    fun widgetHostAndReportedSizeAgreeAcrossGridShapes() {
        for (grid in listOf(Point(4, 4), Point(5, 5), Point(4, 5), Point(5, 7), Point(6, 5))) {
            for (density in listOf(1f, 1.5f, 2.75f, 3.5f)) {
                for (border in listOf(Point(), Point(8, 0), Point(24, 24))) {
                    val padding = WorkspaceProfile.calculateWidgetPadding(
                        border, DisplayMetrics().apply { this.density = density },
                    )
                    val cellSize = Point(96, 128)
                    val workspace = mock<WorkspaceProfile>()
                    whenever(workspace.cellSize).thenReturn(cellSize)
                    whenever(workspace.cellLayoutBorderSpacePx).thenReturn(border)
                    whenever(workspace.widgetPadding).thenReturn(padding)
                    val profile = mock<DeviceProfile>()
                    whenever(profile.workspaceProfile).thenReturn(workspace)

                    for (span in listOf(Point(1, 1), Point(grid.x, 1), grid)) {
                        val params = CellLayoutLayoutParams(0, 0, span.x, span.y)
                        params.setup(cellSize.x, cellSize.y, false, grid.x, grid.y,
                            1f, 1f, border, padding)
                        val reported = WidgetSizes.getWidgetSizePx(profile, span.x, span.y)
                        assertEquals(reported.width, params.width)
                        assertEquals(reported.height, params.height)
                        assertEquals(padding.left, params.x)
                        assertEquals(padding.top, params.y)
                    }
                }
            }
        }
    }

    @Test
    fun existingGuttersReduceOnlyTheirOwnAxisPadding() {
        val metrics = DisplayMetrics().apply { density = 2f }
        assertEquals(Rect(12, 12, 12, 12),
            WorkspaceProfile.calculateWidgetPadding(Point(), metrics))
        assertEquals(Rect(0, 8, 0, 8),
            WorkspaceProfile.calculateWidgetPadding(Point(16, 4), metrics))
        assertEquals(Rect(), WorkspaceProfile.calculateWidgetPadding(Point(16, 16), metrics))
    }
}
