package com.secureledger.app

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.secureledger.core.accessibility.TransactionCandidate
import com.secureledger.design.SecureLedgerTheme
import org.junit.Rule
import org.junit.Test

/** Production back guard, confirmation dialog, and candidate sheet on a test-only activity. */
class BackNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun editorBackPromptsAndDialogBackReturnsToEditor() {
        compose.setContent {
            var prompt by remember { mutableStateOf(false) }
            SecureLedgerTheme {
                Text("编辑中")
                DiscardChangesBackGuard(true, false, prompt) { prompt = true }
                if (prompt) DiscardChangesDialog(onDismiss = { prompt = false }, onDiscard = { prompt = false })
            }
        }
        compose.onNodeWithText("编辑中").assertIsDisplayed()
        pressSystemBack()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        pressSystemBack()
        compose.onNodeWithText("放弃未保存的修改？").assertDoesNotExist()
        compose.onNodeWithText("编辑中").assertIsDisplayed()
    }

    @Test
    fun systemBackDismissesCandidateSheet() {
        compose.setContent {
            var showSheet by remember { mutableStateOf(true) }
            SecureLedgerTheme {
                Text("首页")
                if (showSheet) CandidateReviewSheet(
                    candidate = TransactionCandidate(
                        id = "qa", amountCents = 2350, merchant = "测试商户", paymentChannel = "支付宝",
                        transactionTime = 0, sourcePackage = "com.example.test", ruleId = "qa", confidence = 0.9f
                    ),
                    onDismiss = { showSheet = false },
                    onReview = { showSheet = false }
                )
            }
        }
        compose.onNodeWithText("检测到一笔消费").assertIsDisplayed()
        pressSystemBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("检测到一笔消费").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    private fun pressSystemBack() {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        automation.injectInputEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true)
        automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true)
        compose.waitForIdle()
    }
}
