/* Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.smartspace

import android.app.smartspace.SmartspaceAction
import android.app.smartspace.SmartspaceTarget
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SmartspaceCardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = LauncherPrefs.get(context)
    private var oldMusic = true
    private var oldOther = true

    @Before
    fun enableMusic() {
        oldMusic = prefs.get(LauncherPrefs.SMARTSPACE_MUSIC)
        oldOther = prefs.get(LauncherPrefs.SMARTSPACE_OTHER)
        prefs.put(LauncherPrefs.SMARTSPACE_MUSIC, true)
        prefs.put(LauncherPrefs.SMARTSPACE_OTHER, true)
    }

    @After
    fun restoreMusic() {
        prefs.put(LauncherPrefs.SMARTSPACE_MUSIC, oldMusic)
        prefs.put(LauncherPrefs.SMARTSPACE_OTHER, oldOther)
    }

    private fun target(
        feature: Int = SmartspaceTarget.FEATURE_MEDIA,
        user: UserHandle = Process.myUserHandle(),
    ) = SmartspaceTarget.Builder("provider_card", ComponentName(context, javaClass), user)
        .setFeatureType(feature)
        .setHeaderAction(SmartspaceAction.Builder("play", "Song").build())

    @Test
    fun disabledEventDoesNotProduceACard() {
        prefs.put(LauncherPrefs.SMARTSPACE_MUSIC, false)
        assertNull(SmartspaceCard.fromTarget(context, target().build()))
    }

    @Test
    fun anotherUsersCardDoesNotAppear() {
        val otherUser = UserHandle.of(Process.myUserHandle().identifier + 1)
        assertNull(SmartspaceCard.fromTarget(context, target(user = otherUser).build()))
    }

    @Test
    fun providerDateDoesNotDuplicateTheHomeDate() {
        assertNull(SmartspaceCard.fromTarget(context,
            target(feature = SmartspaceTarget.FEATURE_STEP_DATE).build()))
    }

    @Test
    fun expiredAndFutureCardsDoNotAppear() {
        val now = System.currentTimeMillis()
        assertNull(SmartspaceCard.fromTarget(context,
            target().setCreationTimeMillis(1).setExpiryTimeMillis(now - 1000).build()))
        assertNull(SmartspaceCard.fromTarget(context,
            target().setCreationTimeMillis(now + 60_000).build()))
    }

    @Test
    fun remoteOnlyProviderCardRetainsItsContent() {
        val remote = RemoteViews(context.packageName, R.layout.smartspace_view)
        val value = SmartspaceTarget.Builder("remote", ComponentName(context, javaClass),
            Process.myUserHandle()).setFeatureType(SmartspaceTarget.FEATURE_MEDIA)
            .setRemoteViews(remote).build()
        val card = SmartspaceCard.fromTarget(context, value)
        assertNotNull(card)
        assertNotNull(card!!.remoteViews)
        assertEquals(context.getString(R.string.smartspace_music), card.title.toString())
    }
}
