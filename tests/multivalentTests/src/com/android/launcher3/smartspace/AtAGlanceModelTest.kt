/* Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.smartspace

import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.ModelDbController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AtAGlanceModelTest {
    private fun availableRow(vararg entries: Array<Any?>): Int {
        val cursor = MatrixCursor(arrayOf("cellY", "spanY", "appWidgetProvider"))
        entries.forEach(cursor::addRow)
        val db = mock<ModelDbController>()
        whenever(db.query(any(), any(), any(), isNull())).thenReturn(cursor)
        val row = AtAGlanceModel.findAvailableRow(db, 5)
        verify(db).query(any(), any(), any(), isNull())
        verifyNoMoreInteractions(db)
        assertTrue(cursor.isClosed)
        return row
    }

    @Test
    fun savedIconAndSpanningWidgetKeepTheirRows() {
        assertEquals(3, availableRow(arrayOf(0, 1, null), arrayOf(1, 2, "example/.Widget")))
    }

    @Test
    fun fullPageNeverEvictsAnItem() {
        assertEquals(AtAGlanceModel.NO_SPACE, availableRow(arrayOf(0, 5, "example/.Widget")))
    }

    @Test
    fun existingGoogleWidgetPreventsDuplicateAtAGlance() {
        assertEquals(AtAGlanceModel.EXISTING_WIDGET,
            availableRow(arrayOf(0, 1,
                "com.google.android.googlequicksearchbox/" +
                    "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider")))
    }

    @Test
    fun emptyPageUsesFirstRow() { assertEquals(0, availableRow()) }
}
