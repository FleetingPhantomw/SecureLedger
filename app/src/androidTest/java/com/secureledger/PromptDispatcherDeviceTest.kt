package com.secureledger

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secureledger.core.accessibility.TransactionCandidate
import com.secureledger.integration.accessibility.AppVisibility
import com.secureledger.integration.accessibility.PromptDelivery
import com.secureledger.integration.accessibility.PromptDispatcher
import com.secureledger.integration.accessibility.PromptMode
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromptDispatcherDeviceTest {
    @Test
    fun grantedOverlayPermissionCreatesTouchableReminder() {
        val context = ApplicationProvider.getApplicationContext<SecureLedgerApplication>()
        assumeTrue("Overlay permission not granted on this test device", Settings.canDrawOverlays(context))
        val previousVisibility = AppVisibility.isVisible
        AppVisibility.isVisible = false
        val dispatcher = PromptDispatcher(context)
        try {
            val candidate = TransactionCandidate("overlay_test", 1234, "模拟商户", "微信",
                System.currentTimeMillis(), "com.tencent.mm", "wechat-payment", 0.9f)
            var delivery: PromptDelivery? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                delivery = dispatcher.dispatch(candidate, PromptMode.OVERLAY)
            }
            assertEquals(PromptDelivery.OVERLAY, delivery)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { dispatcher.dismissOverlay() }
            AppVisibility.isVisible = previousVisibility
        }
    }
}
