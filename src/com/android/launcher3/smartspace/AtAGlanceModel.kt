/* Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.smartspace

import android.view.View
import android.view.ViewGroup
import androidx.annotation.WorkerThread
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemViewProvider
import com.android.launcher3.views.ActivityContext

/** A model-only item: disabling At a Glance never changes the saved workspace. */
class AtAGlanceModel(columns: Int, row: Int) : ItemInfo(), ItemViewProvider {
    init {
        id = -R.id.launcher_smartspace
        itemType = Favorites.ITEM_TYPE_QSB
        container = Favorites.CONTAINER_DESKTOP
        screenId = FIRST_SCREEN_ID
        cellX = 0
        cellY = row
        spanX = columns
        spanY = 1
    }

    override fun getViewId() = R.id.launcher_smartspace

    override fun inflateView(ctx: ActivityContext, parent: ViewGroup): View {
        val view = (ctx as? Launcher)?.let(::AtAGlanceView) ?: View(ctx.asContext())
        view.tag = this
        return view
    }

    companion object {
        const val NO_SPACE = -1
        const val EXISTING_WIDGET = -2

        /** Consult saved occupancy before reserving a row; never evict saved items. */
        @JvmStatic
        @WorkerThread
        fun findAvailableRow(db: ModelDbController, rows: Int): Int {
            val occupied = BooleanArray(rows)
            db.query(
                arrayOf(Favorites.CELLY, Favorites.SPANY, Favorites.APPWIDGET_PROVIDER),
                "${Favorites.CONTAINER}=? AND ${Favorites.SCREEN}=?",
                arrayOf(Favorites.CONTAINER_DESKTOP.toString(), FIRST_SCREEN_ID.toString()),
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val provider = cursor.getString(2)
                    if (provider?.contains("SmartspaceWidgetProvider") == true) {
                        return EXISTING_WIDGET
                    }
                    val y = cursor.getInt(0)
                    val end = (y.toLong() + cursor.getInt(1).coerceAtLeast(1))
                        .coerceAtMost(rows.toLong()).toInt()
                    for (row in y.coerceAtLeast(0) until end) occupied[row] = true
                }
            }
            return occupied.indexOfFirst { !it }
        }
    }
}
