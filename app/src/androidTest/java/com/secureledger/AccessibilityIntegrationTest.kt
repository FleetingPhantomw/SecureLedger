package com.secureledger

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureledger.integration.accessibility.AccessibilityPreferences
import com.secureledger.integration.accessibility.AccessibilityTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityIntegrationTest {
    @Test
    fun textExtractorSkipsPasswordInputs() {
        val password = AccessibilityNodeInfo.obtain().apply {
            text = "secret123"
            isPassword = true
        }
        val visible = AccessibilityNodeInfo.obtain().apply { text = "实付 ¥28.50" }
        assertTrue(AccessibilityTextExtractor.extract(password).isEmpty())
        assertEquals(listOf("实付 ¥28.50"), AccessibilityTextExtractor.extract(visible))
    }

    @Test
    fun recognitionIsDisabledUntilUserEnablesAndSelectsAnApp() {
        val context = ApplicationProvider.getApplicationContext<SecureLedgerApplication>()
        val preferences = AccessibilityPreferences(context)
        val original = preferences.state.value
        try {
            preferences.setEnabled(false)
            assertFalse(preferences.state.value.enabled)
            preferences.setPackageSelected("com.tencent.mm", true)
            assertTrue(AccessibilityPreferences(context).state.value.selectedPackages.contains("com.tencent.mm"))
        } finally {
            preferences.setEnabled(original.enabled)
            original.selectedPackages.forEach { preferences.setPackageSelected(it, true) }
            preferences.state.value.selectedPackages.filterNot(original.selectedPackages::contains)
                .forEach { preferences.setPackageSelected(it, false) }
        }
    }
}
